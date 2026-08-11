package com.lecture.enrollment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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

    // ── 공급성과 평가 (QCD) ───────────────────────────────────
    // 납품이 끝난 뒤 구매기업이 발주 관리 화면의 지난 발주에 입력한다.
    // 평가 전에는 전부 null 이다. 기획안의 핵심 차별점인
    // "거래 후 데이터를 다음 추천에 재활용" 의 출발점이다.

    // Quality — 납품수량 대비 불량수량
    @Column(name = "delivered_qty")
    private Long deliveredQty;

    @Column(name = "defect_qty")
    private Long defectQty;

    @Column(name = "defect_type", length = 100)
    private String defectType;

    // 불량수량 ÷ 납품수량 × 100. 소수 둘째 자리까지 둔다.
    @Column(name = "defect_rate", precision = 5, scale = 2)
    private BigDecimal defectRate;

    // Delivery — 실제 납품일. 발주 때 받은 deliveryDate(희망 납품일)와 비교한다
    @Column(name = "actual_delivery_date")
    private LocalDate actualDeliveryDate;

    @Column(name = "on_time")
    private Boolean onTime;

    // 음수면 앞당겨 납품한 것이다
    @Column(name = "delay_days")
    private Integer delayDays;

    // Cost — 실제 청구금액. 선택 입력이며 비우면 비용 축은 평가하지 않는다
    @Column(name = "actual_amount", precision = 19, scale = 2)
    private BigDecimal actualAmount;

    @Column(name = "evaluated_at")
    private LocalDateTime evaluatedAt;

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

    /**
     * 공급성과(QCD)를 등록하고 파생 값을 계산한다.
     *
     * 불량률과 지연일수는 화면에 그대로 노출되는 값이라 반올림 자리와 부호 규칙을
     * 여기서 못 박는다. 계산을 프론트엔드에 맡기면 화면마다 결과가 달라진다.
     *
     * 희망 납품일이 없는 옛 발주는 납기를 평가할 수 없다. 그 경우 onTime 을 null 로
     * 두고 품질만 남긴다. 임의로 준수했다고 치면 지표가 거짓이 된다.
     */
    public void evaluate(Long deliveredQty, Long defectQty, String defectType,
                         LocalDate actualDeliveryDate, BigDecimal actualAmount) {
        this.deliveredQty = deliveredQty;
        this.defectQty = defectQty;
        this.defectType = defectType;
        this.defectRate = BigDecimal.valueOf(defectQty)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(deliveredQty), 2, RoundingMode.HALF_UP);

        this.actualDeliveryDate = actualDeliveryDate;
        if (this.deliveryDate != null && actualDeliveryDate != null) {
            this.delayDays = (int) ChronoUnit.DAYS.between(this.deliveryDate, actualDeliveryDate);
            this.onTime = this.delayDays <= 0;
        }

        this.actualAmount = actualAmount;
        this.evaluatedAt = LocalDateTime.now();
    }

    public boolean isEvaluated() {
        return this.evaluatedAt != null;
    }
}
