package com.lecture.enrollment.service;

import com.lecture.enrollment.dto.EnrollmentDto;
import com.lecture.enrollment.entity.Enrollment;
import com.lecture.enrollment.kafka.EnrollmentKafkaProducer;
import com.lecture.enrollment.kafka.KafkaEvent;
import com.lecture.enrollment.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseServiceClient courseServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final EnrollmentKafkaProducer kafkaProducer;
    private final EnrollmentWriteService enrollmentWriteService;

    /**
     * 수강신청 전체 흐름
     * 1. 강의 존재 확인
     * 2. 중복 수강 확인
     * 3. Enrollment 생성 및 즉시 커밋 (PENDING)
     * 4. 결제 요청
     */
    public EnrollmentDto.EnrollmentResponse enroll(Long userId, EnrollmentDto.EnrollRequest request) {
        Long courseId = request.getCourseId();

        if (!courseServiceClient.existsCourse(courseId)) {
            throw new IllegalArgumentException("존재하지 않는 강의입니다: " + courseId);
        }

        if (enrollmentRepository.existsByUserIdAndCourseId(userId, courseId)) {
            throw new IllegalArgumentException("이미 수강신청한 강의입니다");
        }

        Map<String, Object> courseInfo = courseServiceClient.getCourse(courseId);
        BigDecimal unitPrice = toBigDecimal(courseInfo.get("price"));
        BigDecimal estimatedTotal = unitPrice.multiply(BigDecimal.valueOf(request.getQuantity()));

        Enrollment enrollment = enrollmentWriteService.createPendingEnrollment(userId, request, estimatedTotal);

        // 50행에서 계산한 견적을 그대로 넘긴다.
        // 원본 템플릿이 수강료 99,000원 하나로 고정이라 상수가 박혀 있었고,
        // 그 탓에 화면의 견적과 결제 내역이 서로 다른 값을 말했다.
        paymentServiceClient.requestPayment(userId, courseId, estimatedTotal);

        log.info("[EnrollmentService] 수강신청 완료 (결제 대기) - enrollmentId: {}, 결제금액: {}",
                enrollment.getId(), estimatedTotal);
        return EnrollmentDto.EnrollmentResponse.from(enrollment);
    }

    /**
     * 결제 완료 이벤트를 받아 주문을 확정한다.
     *
     * **PENDING 인 것만 확정한다.** 두 가지를 막는다.
     *
     * 하나. 결제가 몇 초 만에 끝나므로 사용자가 발주 직후 취소를 누르면 이벤트
     * 처리보다 취소가 먼저 도착할 수 있다. 상태를 보지 않고 덮어쓰면 취소한 발주가
     * ACTIVE 로 되살아나고, 결제는 CANCELLED 인데 발주는 ACTIVE 인 상태가 남는다.
     * 거래건수도 취소했는데 올라간다.
     *
     * 둘. Kafka 는 at-least-once 라 payment.completed 가 두 번 올 수 있다.
     * 상태를 보지 않으면 거래건수가 두 번 올라간다.
     */
    @Transactional
    public void activateEnrollment(Long userId, Long courseId) {
        Enrollment enrollment = enrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "수강 정보를 찾을 수 없습니다 - userId: " + userId + ", courseId: " + courseId));

        if (enrollment.getStatus() != Enrollment.Status.PENDING) {
            log.warn("[EnrollmentService] 확정 대상이 아니라 건너뜀 - enrollmentId: {}, status: {}",
                    enrollment.getId(), enrollment.getStatus());
            return;
        }

        enrollment.activate();

        courseServiceClient.increaseEnrollmentCount(courseId);

        kafkaProducer.publishEnrollmentCompleted(
                KafkaEvent.EnrollmentCompletedEvent.builder()
                        .enrollmentId(enrollment.getId())
                        .userId(userId)
                        .courseId(courseId)
                        .build()
        );

        log.info("[EnrollmentService] 수강 활성화 완료 - enrollmentId: {}", enrollment.getId());
    }

    /**
     * 발주 취소
     *
     * 상태만 CANCELLED 로 바꾸고 행은 남긴다. 결제 내역과 대조할 근거가 사라지면 안 된다.
     *
     * 이미 결제가 끝난 발주도 취소할 수 있다. Kafka 로 결제가 몇 초 만에 완료되어
     * PENDING 은 사실상 스쳐 지나가므로, PENDING 만 허용하면 취소할 수 있는 발주가
     * 없는 것과 같다.
     *
     * 결제 취소와 거래건수 감소는 실패해도 취소 자체를 막지 않는다. 각 클라이언트가
     * 예외를 삼키고 로그만 남긴다. 그것 때문에 사용자가 취소를 못 하게 되는 편이 나쁘다.
     *
     * enrollments 에 UNIQUE (user_id, course_id) 가 있어 **취소한 품목을 다시 발주할
     * 수는 없다.** 행을 남기는 대가다. 재발주를 허용하려면 제약을 풀어야 하고 그것은
     * docs/constraints.md 의 5번 항목에 걸린다.
     */
    @Transactional
    public EnrollmentDto.EnrollmentResponse cancel(Long userId, Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("발주를 찾을 수 없습니다: " + enrollmentId));

        if (!enrollment.getUserId().equals(userId)) {
            throw new AccessDeniedException("자신의 발주만 취소할 수 있습니다");
        }

        if (enrollment.getStatus() == Enrollment.Status.CANCELLED) {
            throw new IllegalArgumentException("이미 취소된 발주입니다");
        }

        boolean wasActive = enrollment.getStatus() == Enrollment.Status.ACTIVE;
        enrollment.cancel();

        paymentServiceClient.cancelPayment(userId, enrollment.getCourseId());

        // 거래건수는 ACTIVE 로 전환될 때 올라간다. PENDING 에서 취소하면 내릴 것이 없다.
        if (wasActive) {
            courseServiceClient.decreaseEnrollmentCount(enrollment.getCourseId());
        }

        log.info("[EnrollmentService] 발주 취소 - enrollmentId: {}, userId: {}, 이전 상태: {}",
                enrollmentId, userId, wasActive ? "ACTIVE" : "PENDING");

        return EnrollmentDto.EnrollmentResponse.from(enrollment);
    }

    /**
     * 공급성과 평가 등록 (QCD)
     *
     * 납품이 끝난 뒤 구매기업이 발주 관리 화면의 지난 발주에 입력한다.
     * 등록하면 그 공급기업의 누적 지표가 갱신되고 다음 추천에 반영된다.
     * 기획안의 핵심 차별점인 피드백 구조가 여기서 닫힌다.
     *
     * ACTIVE 인 발주만 평가할 수 있다. 결제가 끝나지 않았거나 취소한 주문은
     * 납품 자체가 없으므로 평가할 대상이 아니다.
     *
     * 다시 평가하는 것은 막는다. 같은 발주를 두 번 등록하면 누적 지표에 두 번
     * 반영되어 공급기업 성과가 왜곡된다. 고쳐야 한다면 수정 API 를 따로 만들고
     * 이전 값을 빼는 처리를 함께 넣어야 한다.
     */
    @Transactional
    public EnrollmentDto.EnrollmentResponse evaluate(
            Long userId, Long enrollmentId, EnrollmentDto.PerformanceRequest request) {

        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("발주를 찾을 수 없습니다: " + enrollmentId));

        if (!enrollment.getUserId().equals(userId)) {
            throw new AccessDeniedException("자신의 발주만 평가할 수 있습니다");
        }

        if (enrollment.getStatus() != Enrollment.Status.ACTIVE) {
            throw new IllegalArgumentException(
                    "주문이 확정된 발주만 평가할 수 있습니다. 현재 상태: " + enrollment.getStatus());
        }

        if (enrollment.isEvaluated()) {
            throw new IllegalArgumentException("이미 평가한 발주입니다");
        }

        if (request.getDefectQty() > request.getDeliveredQty()) {
            throw new IllegalArgumentException("불량수량이 납품수량보다 많을 수 없습니다");
        }

        enrollment.evaluate(
                request.getDeliveredQty(),
                request.getDefectQty(),
                request.getDefectType(),
                request.getActualDeliveryDate(),
                request.getActualAmount());

        courseServiceClient.applyPerformance(
                enrollment.getCourseId(),
                enrollment.getDeliveredQty(),
                enrollment.getDefectQty(),
                enrollment.getOnTime(),
                enrollment.getEstimatedTotal(),
                enrollment.getActualAmount());

        log.info("[EnrollmentService] 공급성과 평가 등록 - enrollmentId: {}, 불량률: {}%, 납기준수: {}",
                enrollmentId, enrollment.getDefectRate(), enrollment.getOnTime());

        return EnrollmentDto.EnrollmentResponse.from(enrollment);
    }

    /**
     * 사용자 수강 목록 조회
     * - course-service에서 강의 상세 정보를 붙여서 반환
     */
    public List<EnrollmentDto.EnrollmentResponse> getEnrollmentsByUser(Long userId) {
        List<Enrollment> enrollments = enrollmentRepository.findByUserId(userId);

        return enrollments.stream()
                .map(enrollment -> {
                    Map<String, Object> courseInfo = courseServiceClient.getCourse(enrollment.getCourseId());

                    EnrollmentDto.CourseSummary courseSummary = EnrollmentDto.CourseSummary.builder()
                            .id(toLong(courseInfo.get("id")))
                            .title((String) courseInfo.get("title"))
                            .description((String) courseInfo.get("description"))
                            .category(normalizeCategory((String) courseInfo.get("category")))
                            .price(toInteger(courseInfo.get("price")))
                            .thumbnail((String) courseInfo.get("thumbnail"))
                            .instructorName(
                                    firstNonNull(
                                            (String) courseInfo.get("instructorName"),
                                            (String) courseInfo.get("teacherName"),
                                            (String) courseInfo.get("instructor_name")
                                    )
                            )
                            .enrollmentCount(toInteger(
                                    firstNonNullObject(
                                            courseInfo.get("enrollmentCount"),
                                            courseInfo.get("enrollment_count")
                                    )
                            ))
                            .defectRate(toNullableBigDecimal(courseInfo.get("defectRate")))
                            .onTimeRate(toNullableBigDecimal(courseInfo.get("onTimeRate")))
                            .costVarianceRate(toNullableBigDecimal(courseInfo.get("costVarianceRate")))
                            .evaluatedCount(toInteger(courseInfo.get("evaluatedCount")))
                            .build();

                    return EnrollmentDto.EnrollmentResponse.from(enrollment, courseSummary);
                })
                .collect(Collectors.toList());
    }

    /**
     * 수강 이력 조회 - 추천 서비스용
     */
    public EnrollmentDto.EnrollmentHistoryResponse getEnrollmentHistory(Long userId) {
        List<Long> activeCourseIds = enrollmentRepository
                .findByUserIdAndStatus(userId, Enrollment.Status.ACTIVE)
                .stream()
                .map(Enrollment::getCourseId)
                .collect(Collectors.toList());

        return EnrollmentDto.EnrollmentHistoryResponse.builder()
                .userId(userId)
                .activeCourseIds(activeCourseIds)
                .build();
    }

    private String normalizeCategory(String category) {
        if (category == null) return null;

        return switch (category) {
            case "BACKEND" -> "백엔드";
            case "FRONTEND" -> "프론트엔드";
            case "DEVOPS" -> "DevOps";
            case "DATA" -> "데이터";
            case "AI" -> "AI";
            default -> category;
        };
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(value.toString());
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(value.toString());
    }

    private BigDecimal toNullableBigDecimal(Object value) {
        if (value == null) return null;
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            throw new IllegalStateException("강의 가격 정보가 없습니다");
        }
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Object firstNonNullObject(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
