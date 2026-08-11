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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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
        verify(paymentServiceClient).requestPayment(eq(3L), eq(7L), eq(BigDecimal.valueOf(99000)));
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
}
