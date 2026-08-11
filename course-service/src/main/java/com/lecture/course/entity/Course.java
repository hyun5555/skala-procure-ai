package com.lecture.course.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "courses")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    // 강사 ID (users 테이블 참조 - 직접 JOIN 없이 ID만 보관)
    @Column(nullable = false)
    private Long instructorId;

    // 공급기업 이름. 등록 시 user-service 에서 한 번 조회해 여기에 둔다.
    // 조회 때마다 부르면 목록 한 번에 품목 수만큼 호출이 나간다.
    // 조회에 실패했거나 이 컬럼이 생기기 전에 등록된 품목은 null 이다.
    @Column(length = 100)
    private String instructorName;

    // 수강생 수 (추천 서비스 정렬 기준)
    @Column(nullable = false)
    @Builder.Default
    private Integer enrollmentCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.ACTIVE;

    // 조달 계약 종료일. description 의 '계약기간: 20240131~20250331' 에서 뽑아 둔다.
    // 자유 텍스트로만 두면 만료 여부로 거를 수가 없다. 값이 없으면 만료 판정을 하지 않는다.
    @Column(name = "contract_end")
    private LocalDate contractEnd;

    // ── 공급기업 누적 성과지표 (QCD) ──────────────────────────
    // 같은 공급기업의 모든 품목이 같은 값을 갖는다. 기획안이 "공급업체 품질지표" 라고
    // 정의했기 때문이다. 품목 단위로 쌓으면 품목마다 한두 건씩 흩어져 지표가 의미를 잃는다.
    // 화면과 추천의 단위가 품목이라 품목 행에 실어 둔다.

    @Column(name = "total_delivered_qty", nullable = false)
    @Builder.Default
    private Long totalDeliveredQty = 0L;

    @Column(name = "total_defect_qty", nullable = false)
    @Builder.Default
    private Long totalDefectQty = 0L;

    @Column(name = "evaluated_count", nullable = false)
    @Builder.Default
    private Integer evaluatedCount = 0;

    @Column(name = "on_time_count", nullable = false)
    @Builder.Default
    private Integer onTimeCount = 0;

    // 누적 불량수량 ÷ 누적 납품수량 × 100. 평가 이력이 없으면 null 이다.
    // 0.00 과 null 은 다르다. 앞은 무결점, 뒤는 아직 모른다는 뜻이다.
    @Column(name = "defect_rate", precision = 5, scale = 2)
    private BigDecimal defectRate;

    // 납기 준수 건수 ÷ 평가 건수 × 100 — OTD (On-Time Delivery)
    @Column(name = "on_time_rate", precision = 5, scale = 2)
    private BigDecimal onTimeRate;

    // Cost — 실제 청구금액을 적어 낸 건만 쌓는다. 비용을 비운 평가는 두 합계 모두 건드리지 않는다
    @Column(name = "total_estimated_amount", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalEstimatedAmount = BigDecimal.ZERO;

    @Column(name = "total_actual_amount", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalActualAmount = BigDecimal.ZERO;

    // (실제청구 − 견적) ÷ 견적 × 100 — PPV (Purchase Price Variance)
    // 양수면 견적을 초과한 것이다. 비용 평가 이력이 없으면 null
    @Column(name = "cost_variance_rate", precision = 7, scale = 2)
    private BigDecimal costVarianceRate;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum Category {
        BACKEND, FRONTEND, DEVOPS, DATA_SCIENCE, MOBILE, SECURITY, DATABASE, OTHER
    }

    public enum Status {
        ACTIVE, INACTIVE
    }

    /**
     * 계약이 끝난 품목인가. 종료일이 없으면 판정하지 않는다(만료 아님으로 본다).
     */
    public boolean isContractExpired(LocalDate today) {
        return contractEnd != null && contractEnd.isBefore(today);
    }

    public void increaseEnrollmentCount() {
        this.enrollmentCount++;
    }

    public void updateStatus(Status status) {
        this.status = status;
    }

    /**
     * 발주가 취소되면 거래건수를 되돌린다.
     * 기획안의 누적 거래건수에 그대로 대응하는 값이라 취소분이 남아 있으면 지표가 부풀려진다.
     * 0 미만으로 내려가지 않게 막는다. 취소 요청이 중복으로 와도 음수가 되어서는 안 된다.
     */
    public void decreaseEnrollmentCount() {
        if (this.enrollmentCount > 0) {
            this.enrollmentCount--;
        }
    }

    /**
     * 공급성과 평가 한 건을 누적한다.
     *
     * 비율을 매번 다시 계산하지 않고 원시 수량을 쌓아 두었다가 나눈다. 평균의 평균을
     * 내면 발주 규모가 작은 건이 큰 건과 같은 무게를 갖게 되어 지표가 왜곡된다.
     *
     * onTime 이 null 이면 납기를 판정할 수 없었던 건이다. 평가 건수에는 넣되
     * 준수 건수에는 넣지 않으면 준수율이 부당하게 낮아지므로, 그런 건은
     * 납기 통계에서 아예 뺀다. 품질 통계에는 그대로 들어간다.
     */
    public void applyPerformance(Long deliveredQty, Long defectQty, Boolean onTime,
                                 BigDecimal estimatedAmount, BigDecimal actualAmount) {
        this.totalDeliveredQty += deliveredQty;
        this.totalDefectQty += defectQty;
        this.defectRate = BigDecimal.valueOf(this.totalDefectQty)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(this.totalDeliveredQty), 2, RoundingMode.HALF_UP);

        if (onTime != null) {
            this.evaluatedCount++;
            if (onTime) {
                this.onTimeCount++;
            }
            this.onTimeRate = BigDecimal.valueOf(this.onTimeCount)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(this.evaluatedCount), 2, RoundingMode.HALF_UP);
        }

        // 비용은 실제 청구금액을 적어 낸 건만 쌓는다. 비운 건까지 견적만 더하면
        // 분모가 커져 비용 초과가 실제보다 작아 보인다.
        if (actualAmount != null && estimatedAmount != null
                && estimatedAmount.compareTo(BigDecimal.ZERO) > 0) {
            this.totalEstimatedAmount = this.totalEstimatedAmount.add(estimatedAmount);
            this.totalActualAmount = this.totalActualAmount.add(actualAmount);
            this.costVarianceRate = this.totalActualAmount.subtract(this.totalEstimatedAmount)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(this.totalEstimatedAmount, 2, RoundingMode.HALF_UP);
        }
    }

    /**
     * 공급기업이 등록한 품목 정보를 고친다.
     *
     * **description 은 바꾸지 않는다.** 조달 명세 16개 항목이 그 한 칸에 들어 있는데
     * 등록 폼은 기업구분·세부품명·물품식별번호·납품장소·쇼핑몰등록일자를 수집하지 않는다.
     * 그 폼을 수정 화면으로 재사용하면 단가 하나만 고쳐도 다섯 항목이 사라진다.
     *
     * 등록자와 거래건수, 공급기업 이름, 누적 성과지표도 바뀌지 않는다.
     */
    public void update(String title, Category category, BigDecimal price, LocalDate contractEnd) {
        this.title = title;
        this.category = category;
        this.price = price;
        this.contractEnd = contractEnd;
    }
}
