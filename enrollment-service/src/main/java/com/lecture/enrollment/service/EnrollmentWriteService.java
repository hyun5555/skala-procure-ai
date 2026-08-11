package com.lecture.enrollment.service;

import com.lecture.enrollment.dto.EnrollmentDto;
import com.lecture.enrollment.entity.Enrollment;
import com.lecture.enrollment.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentWriteService {

    private final EnrollmentRepository enrollmentRepository;

    /**
     * 반드시 독립 트랜잭션으로 실행
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Enrollment createPendingEnrollment(
            Long userId,
            EnrollmentDto.EnrollRequest request,
            BigDecimal estimatedTotal) {

        Enrollment enrollment = enrollmentRepository.save(
                Enrollment.builder()
                        .userId(userId)
                        .courseId(request.getCourseId())
                        .quantity(request.getQuantity())
                        .unit(request.getUnit().trim())
                        .deliveryDate(request.getDeliveryDate())
                        .deliveryPlace(request.getDeliveryPlace().trim())
                        .notes(normalizeOptionalText(request.getNotes()))
                        .contactName(request.getContactName().trim())
                        .contactPhone(request.getContactPhone().trim())
                        .estimatedTotal(estimatedTotal)
                        .build()
        );

        log.info("[EnrollmentWriteService] PENDING enrollment 생성 완료 - enrollmentId: {}, userId: {}, courseId: {}",
                enrollment.getId(), userId, request.getCourseId());

        return enrollment;
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
