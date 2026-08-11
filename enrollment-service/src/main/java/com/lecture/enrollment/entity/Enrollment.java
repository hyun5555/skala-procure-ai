package com.lecture.enrollment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "enrollments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "course_id"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    // 기존 발주 행과의 무중단 스키마 호환을 위해 컬럼 자체는 nullable로 둔다.
    // 신규 요청은 EnrollRequest validation에서 필수값을 강제한다.
    @Column
    private Long quantity;

    @Column(length = 20)
    private String unit;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    @Column(name = "delivery_place", length = 100)
    private String deliveryPlace;

    @Column(length = 300)
    private String notes;

    @Column(name = "contact_name", length = 30)
    private String contactName;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "estimated_total", precision = 19, scale = 2)
    private BigDecimal estimatedTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.PENDING;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING,   // 수강신청 완료, 결제 대기
        ACTIVE,    // 결제 완료, 수강 활성화
        CANCELLED  // 취소
    }

    public void activate() {
        this.status = Status.ACTIVE;
    }

    public void cancel() {
        this.status = Status.CANCELLED;
    }
}
