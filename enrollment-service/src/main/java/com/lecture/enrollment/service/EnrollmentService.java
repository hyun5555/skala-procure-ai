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
     * **PENDING 인 것만 확정한다.** Kafka 는 at-least-once 라
     * payment.completed 가 두 번 올 수 있다.
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
     * 공급성과 평가 등록 (QCD)
     *
     * 납품이 끝난 뒤 구매기업이 발주 관리 화면의 지난 발주에 입력한다.
     * 등록하면 그 공급기업의 누적 지표가 갱신되고 다음 추천에 반영된다.
     * 기획안의 핵심 차별점인 피드백 구조가 여기서 닫힌다.
     *
     * 결제가 끝난 발주만 평가할 수 있다(isPaid — SHIPPING · DELIVERED · ACTIVE).
     * 결제가 끝나지 않았거나 취소한 주문은 납품 자체가 없으므로 평가할 대상이 아니다.
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

        if (!enrollment.isPaid()) {
            throw new IllegalArgumentException(
                    "결제가 끝난 발주만 평가할 수 있습니다. 현재 상태: " + enrollment.getStatus());
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
        enrollment.markDelivered();

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

    @Transactional
    public EnrollmentDto.QualityResponse updateQuality(
            Long userId, Long enrollmentId, EnrollmentDto.QualityRequest request) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("발주 정보를 찾을 수 없습니다: " + enrollmentId));

        if (!enrollment.getUserId().equals(userId)) {
            throw new AccessDeniedException("본인의 발주에만 품질 정보를 등록할 수 있습니다");
        }
        if (!enrollment.isPaid()) {
            throw new IllegalArgumentException("결제가 끝난 발주에만 품질 정보를 등록할 수 있습니다");
        }
        if (request.getDefectQuantity() > request.getDeliveredQuantity()) {
            throw new IllegalArgumentException("불량 수량은 납품 수량보다 많을 수 없습니다");
        }
        if (request.getDefectQuantity() > 0 && "해당 없음".equals(request.getDefectType())) {
            throw new IllegalArgumentException("불량이 발생한 경우 불량 유형을 선택해야 합니다");
        }
        if (request.getDefectQuantity() == 0 && !"해당 없음".equals(request.getDefectType())) {
            throw new IllegalArgumentException("불량 수량이 0이면 불량 유형은 해당 없음이어야 합니다");
        }

        // 품질 정보는 수정도 허용하므로 기존 값과의 차이만 공급기업 누적 지표에 반영한다.
        // 매번 전체 수량을 더하면 '품질 수정'을 누를 때마다 불량률의 분모와 분자가
        // 중복 누적된다. 최초 등록이면 기존 값이 0이라 입력값 전체가 그대로 반영된다.
        long previousDeliveredQty = enrollment.getDeliveredQty() == null ? 0L : enrollment.getDeliveredQty();
        long previousDefectQty = enrollment.getDefectQty() == null ? 0L : enrollment.getDefectQty();

        enrollment.updateQuality(
                request.getDeliveredQuantity(), request.getDefectQuantity(), request.getDefectType());
        enrollment.markDelivered();

        // 뒤 세 인자(onTime · 견적 · 실제청구)를 null 로 보내 **Q 축만 갱신한다.**
        // 납기와 비용은 성과평가에서만 산출하는 값이라 품질 수정이 건드리면 안 된다.
        //
        // 그래서 성과평가를 먼저 한 뒤 품질을 고치면 불량률은 따라 움직이지만
        // 납기 준수율과 견적 대비 증감률은 첫 평가값에 머문다. 품질 정보는 명세가
        // 재저장을 허용하므로 막지 않고, 대신 이 제약을 여기 적어 둔다.
        courseServiceClient.applyPerformance(
                enrollment.getCourseId(),
                enrollment.getDeliveredQty() - previousDeliveredQty,
                enrollment.getDefectQty() - previousDefectQty,
                null,
                null,
                null);

        log.info("[EnrollmentService] 품질 정보 등록 - enrollmentId: {}, 불량률: {}%",
                enrollmentId, enrollment.getDefectRate());
        return EnrollmentDto.QualityResponse.from(enrollment);
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
                            // 카테고리는 course-service 가 준 enum 값을 그대로 넘긴다.
                            // 라벨은 vue-frontend 의 store/course.js categoryLabelMap 한 곳에서만
                            // 정한다. 여기서 한 번 더 바꾸면 그 표가 원본 enum 을 받지 못해
                            // 매핑에 실패하고 변환된 문자열이 그대로 화면에 노출된다.
                            .category((String) courseInfo.get("category"))
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
        // 결제가 끝난 발주는 배송 중이든 납품이 끝났든 모두 '거래한 이력'이다.
        // ACTIVE 만 보면 SHIPPING/DELIVERED 로 넘어간 발주가 추천에서 통째로 빠져
        // 이미 산 품목이 다시 추천되고 카테고리 판정도 어긋난다.
        List<Long> activeCourseIds = enrollmentRepository
                .findByUserIdAndStatusIn(userId, List.of(
                        Enrollment.Status.SHIPPING,
                        Enrollment.Status.DELIVERED,
                        Enrollment.Status.ACTIVE))
                .stream()
                .map(Enrollment::getCourseId)
                .collect(Collectors.toList());

        return EnrollmentDto.EnrollmentHistoryResponse.builder()
                .userId(userId)
                .activeCourseIds(activeCourseIds)
                .build();
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
