package com.lecture.enrollment.service;

import com.lecture.enrollment.dto.EnrollmentDto;
import com.lecture.enrollment.entity.Enrollment;
import com.lecture.enrollment.kafka.EnrollmentKafkaProducer;
import com.lecture.enrollment.repository.EnrollmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private CourseServiceClient courseServiceClient;
    @Mock
    private PaymentServiceClient paymentServiceClient;
    @Mock
    private EnrollmentKafkaProducer kafkaProducer;
    @Mock
    private EnrollmentWriteService enrollmentWriteService;

    @InjectMocks
    private EnrollmentService enrollmentService;

    @Test
    void enrollCalculatesAndReturnsPersistedOrderDetails() {
        EnrollmentDto.EnrollRequest request = EnrollmentDto.EnrollRequest.builder()
                .courseId(7L)
                .quantity(12L)
                .unit("개")
                .deliveryDate(LocalDate.now().plusDays(7))
                .deliveryPlace("서울 공장")
                .notes("파렛트 포장")
                .contactName("홍길동")
                .contactPhone("010-1234-5678")
                .build();

        BigDecimal expectedTotal = new BigDecimal("15006.00");
        Enrollment saved = Enrollment.builder()
                .userId(3L)
                .courseId(7L)
                .quantity(request.getQuantity())
                .unit(request.getUnit())
                .deliveryDate(request.getDeliveryDate())
                .deliveryPlace(request.getDeliveryPlace())
                .notes(request.getNotes())
                .contactName(request.getContactName())
                .contactPhone(request.getContactPhone())
                .estimatedTotal(expectedTotal)
                .build();

        when(courseServiceClient.existsCourse(7L)).thenReturn(true);
        when(enrollmentRepository.existsByUserIdAndCourseId(3L, 7L)).thenReturn(false);
        when(courseServiceClient.getCourse(7L)).thenReturn(Map.of("price", "1250.50"));
        when(enrollmentWriteService.createPendingEnrollment(3L, request, expectedTotal)).thenReturn(saved);

        EnrollmentDto.EnrollmentResponse response = enrollmentService.enroll(3L, request);

        assertThat(response.getOrderRequest().getQuantity()).isEqualTo(12L);
        assertThat(response.getOrderRequest().getDeliveryPlace()).isEqualTo("서울 공장");
        assertThat(response.getOrderRequest().getEstimatedTotal()).isEqualByComparingTo("15006.00");
        // 계산한 견적이 그대로 결제로 넘어가는지 고정한다.
        // 이 자리에 99,000원 상수가 박혀 있었다. 바로 위에서 estimatedTotal 을
        // 계산해 놓고 쓰지 않는 형태라 같은 실수가 다시 나기 쉽다.
        verify(paymentServiceClient).requestPayment(eq(3L), eq(7L), eq(expectedTotal));
        verify(enrollmentWriteService).createPendingEnrollment(3L, request, expectedTotal);
    }

    @Test
    void legacyEnrollmentWithoutOrderDetailsReturnsNullOrderRequest() {
        Enrollment legacy = Enrollment.builder()
                .userId(3L)
                .courseId(7L)
                .build();

        EnrollmentDto.EnrollmentResponse response = EnrollmentDto.EnrollmentResponse.from(legacy);

        assertThat(response.getOrderRequest()).isNull();
    }

    @Test
    void qualityRegistrationCalculatesDefectRateFromDeliveredQuantity() {
        Enrollment enrollment = Enrollment.builder()
                .userId(3L)
                .courseId(7L)
                .status(Enrollment.Status.ACTIVE)
                .build();
        EnrollmentDto.QualityRequest request = EnrollmentDto.QualityRequest.builder()
                .deliveredQuantity(200L)
                .defectQuantity(5L)
                .defectType("외관 불량")
                .build();
        when(enrollmentRepository.findById(11L)).thenReturn(Optional.of(enrollment));

        EnrollmentDto.QualityResponse response = enrollmentService.updateQuality(3L, 11L, request);

        assertThat(response.getDefectRate()).isEqualByComparingTo("2.50");
        assertThat(response.getDeliveredQuantity()).isEqualTo(200L);
        assertThat(response.getDefectQuantity()).isEqualTo(5L);
        verify(courseServiceClient).applyPerformance(7L, 200L, 5L, null, null, null);
    }

    @Test
    void qualityUpdateAppliesOnlyDifferenceToSupplierMetrics() {
        Enrollment enrollment = Enrollment.builder()
                .userId(3L)
                .courseId(7L)
                .status(Enrollment.Status.ACTIVE)
                .build();
        enrollment.updateQuality(200L, 5L, "외관 불량");

        EnrollmentDto.QualityRequest request = EnrollmentDto.QualityRequest.builder()
                .deliveredQuantity(220L)
                .defectQuantity(6L)
                .defectType("규격 불량")
                .build();
        when(enrollmentRepository.findById(11L)).thenReturn(Optional.of(enrollment));

        enrollmentService.updateQuality(3L, 11L, request);

        verify(courseServiceClient).applyPerformance(7L, 20L, 1L, null, null, null);
    }

    @Test
    void qualityRegistrationRejectsOtherUsersOrder() {
        Enrollment enrollment = Enrollment.builder()
                .userId(3L)
                .courseId(7L)
                .status(Enrollment.Status.ACTIVE)
                .build();
        EnrollmentDto.QualityRequest request = EnrollmentDto.QualityRequest.builder()
                .deliveredQuantity(100L)
                .defectQuantity(0L)
                .defectType("해당 없음")
                .build();
        when(enrollmentRepository.findById(11L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> enrollmentService.updateQuality(999L, 11L, request))
                .isInstanceOf(AccessDeniedException.class);
    }

    /**
     * Kafka 는 at-least-once 라 payment.completed 가 두 번 올 수 있다.
     * 두 번째부터는 건너뛰어야 거래건수가 부풀지 않는다.
     */
    @Test
    void activateIsIdempotentForDuplicateEvents() {
        Enrollment enrollment = Enrollment.builder().userId(3L).courseId(7L).build();
        when(enrollmentRepository.findByUserIdAndCourseId(3L, 7L)).thenReturn(Optional.of(enrollment));

        enrollmentService.activateEnrollment(3L, 7L);
        enrollmentService.activateEnrollment(3L, 7L);

        assertThat(enrollment.getStatus()).isEqualTo(Enrollment.Status.ACTIVE);
        verify(courseServiceClient, times(1)).increaseEnrollmentCount(7L);
    }
}
