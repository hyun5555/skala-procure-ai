package com.lecture.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    // enrollments.estimated_total 과 같은 폭이어야 한다.
    // DECIMAL(10,2) 는 99,999,999.99 가 한계라 단가 2,524만원짜리를 넉 대만
    // 주문해도 넘친다. ddl-auto: update 는 기존 컬럼 폭을 넓히지 않으므로
    // 이미 만들어진 DB 는 ALTER TABLE 을 한 번 실행해야 한다.
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.PENDING;

    // 외부 PG사 거래 ID (실습에서는 UUID로 대체)
    @Column(name = "transaction_id", unique = true)
    private String transactionId;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING,    // 결제 대기
        COMPLETED,  // 결제 완료
        FAILED,     // 결제 실패
        CANCELLED   // 취소
    }

    public void complete(String transactionId) {
        this.status = Status.COMPLETED;
        this.transactionId = transactionId;
    }

    public void fail() {
        this.status = Status.FAILED;
    }

    /**
     * 발주가 취소되면 결제도 함께 취소로 표시한다.
     * 실제 환불은 PG 연동이 필요하고 이 실습에는 PG 가 없으므로 상태만 바꾼다.
     * transactionId 는 지우지 않는다. 어떤 거래가 취소된 것인지 남아야 한다.
     */
    public void cancel() {
        this.status = Status.CANCELLED;
    }
}
