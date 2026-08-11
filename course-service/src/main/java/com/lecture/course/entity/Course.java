package com.lecture.course.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
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

    public void increaseEnrollmentCount() {
        this.enrollmentCount++;
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
     * 공급기업이 등록한 품목 정보를 고친다.
     *
     * **description 은 바꾸지 않는다.** 조달 명세 16개 항목이 그 한 칸에 들어 있는데
     * 등록 폼은 기업구분·세부품명·물품식별번호·납품장소·쇼핑몰등록일자를 수집하지 않는다.
     * 그 폼을 수정 화면으로 재사용하면 단가 하나만 고쳐도 다섯 항목이 사라진다.
     *
     * 등록자와 거래건수도 바뀌지 않는다.
     */
    public void update(String title, Category category, BigDecimal price) {
        this.title = title;
        this.category = category;
        this.price = price;
    }
}
