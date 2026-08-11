package com.lecture.payment.service;

import com.lecture.payment.dto.PaymentDto;
import com.lecture.payment.entity.Payment;
import com.lecture.payment.kafka.PaymentKafkaProducer;
import com.lecture.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentKafkaProducer kafkaProducer;

    /**
     * 내부 결제 요청 (Enrollment Service → Payment Service REST 호출)
     * 실습 환경에서는 PG 연동 없이 항상 성공으로 처리
     *
     * 처리 흐름:
     * 1. Payment 생성 (PENDING)
     * 2. PG 결제 처리 (실습: UUID 트랜잭션 ID 발급으로 대체)
     * 3. Payment 상태 → COMPLETED
     * 4. payment.completed 이벤트 발행 → Kafka
     */
    @Transactional
    public PaymentDto.InternalPaymentResult processInternalPayment(
            PaymentDto.InternalPaymentRequest request) {

        log.info("[PaymentService] 결제 요청 - userId: {}, courseId: {}, amount: {}",
                request.getUserId(), request.getCourseId(), request.getAmount());

        Payment payment = paymentRepository.save(
                Payment.builder()
                        .userId(request.getUserId())
                        .courseId(request.getCourseId())
                        .amount(request.getAmount())
                        .build()
        );

        try {
            String transactionId = UUID.randomUUID().toString();

            payment.complete(transactionId);
            log.info("[PaymentService] 결제 완료 처리 - paymentId: {}, transactionId: {}",
                    payment.getId(), transactionId);

            kafkaProducer.publishPaymentCompleted(
                    PaymentKafkaProducer.PaymentCompletedEvent.builder()
                            .paymentId(payment.getId())
                            .userId(request.getUserId())
                            .courseId(request.getCourseId())
                            .status("COMPLETED")
                            .build()
            );

            log.info("[PaymentService] 결제 최종 성공 - paymentId: {}", payment.getId());

            return PaymentDto.InternalPaymentResult.builder()
                    .paymentId(payment.getId())
                    .status("COMPLETED")
                    .build();

        } catch (Exception e) {
            payment.fail();

            log.error("[PaymentService] 결제 실패 - paymentId: {}, userId: {}, courseId: {}, error: {}",
                    payment.getId(),
                    request.getUserId(),
                    request.getCourseId(),
                    e.getMessage(),
                    e);

            return PaymentDto.InternalPaymentResult.builder()
                    .paymentId(payment.getId())
                    .status("FAILED")
                    .build();
        }
    }

    /**
     * 내부 결제 취소 (Enrollment Service 호출)
     *
     * 발주가 취소되면 결제 내역도 취소로 표시한다. 그러지 않으면 취소한 주문의
     * 결제가 COMPLETED 로 남아 결제 내역이 사실과 달라진다.
     *
     * 결제 기록이 없어도 예외를 던지지 않는다. 결제가 실패했거나 아직 생기지
     * 않은 발주도 취소할 수 있어야 한다.
     */
    @Transactional
    public void cancelInternalPayment(PaymentDto.InternalCancelRequest request) {
        paymentRepository.findByUserIdAndCourseId(request.getUserId(), request.getCourseId())
                .ifPresentOrElse(
                        payment -> {
                            payment.cancel();
                            log.info("[PaymentService] 결제 취소 - paymentId: {}, userId: {}, courseId: {}",
                                    payment.getId(), request.getUserId(), request.getCourseId());
                        },
                        () -> log.info("[PaymentService] 취소할 결제 기록 없음 - userId: {}, courseId: {}",
                                request.getUserId(), request.getCourseId())
                );
    }

    /**
     * 결제 단건 조회
     */
    public PaymentDto.PaymentResponse getPayment(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다: " + id));
        return PaymentDto.PaymentResponse.from(payment);
    }

    /**
     * 사용자 결제 내역 조회
     */
    public List<PaymentDto.PaymentResponse> getPaymentsByUser(Long userId) {
        return paymentRepository.findByUserId(userId).stream()
                .map(PaymentDto.PaymentResponse::from)
                .collect(Collectors.toList());
    }
}