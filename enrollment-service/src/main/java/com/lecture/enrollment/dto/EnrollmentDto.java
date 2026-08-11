package com.lecture.enrollment.dto;

import com.lecture.enrollment.entity.Enrollment;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class EnrollmentDto {

    // 수강신청 요청
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnrollRequest {
        @NotNull(message = "강의 ID는 필수입니다")
        private Long courseId;

        @NotNull(message = "발주 수량은 필수입니다")
        @Positive(message = "발주 수량은 1 이상이어야 합니다")
        private Long quantity;

        @NotBlank(message = "단위는 필수입니다")
        @Size(max = 20, message = "단위는 20자 이하여야 합니다")
        private String unit;

        @NotNull(message = "희망 납품일은 필수입니다")
        @FutureOrPresent(message = "희망 납품일은 오늘 이후여야 합니다")
        private LocalDate deliveryDate;

        @NotBlank(message = "납품 장소는 필수입니다")
        @Size(max = 100, message = "납품 장소는 100자 이하여야 합니다")
        private String deliveryPlace;

        @Size(max = 300, message = "요청사항은 300자 이하여야 합니다")
        private String notes;

        @NotBlank(message = "담당자명은 필수입니다")
        @Size(max = 30, message = "담당자명은 30자 이하여야 합니다")
        private String contactName;

        @NotBlank(message = "담당자 연락처는 필수입니다")
        @Size(max = 30, message = "담당자 연락처는 30자 이하여야 합니다")
        private String contactPhone;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QualityRequest {
        @NotNull(message = "납품 수량은 필수입니다")
        @Positive(message = "납품 수량은 1 이상이어야 합니다")
        private Long deliveredQuantity;

        @NotNull(message = "불량 수량은 필수입니다")
        @PositiveOrZero(message = "불량 수량은 0 이상이어야 합니다")
        private Long defectQuantity;

        @NotBlank(message = "불량 유형은 필수입니다")
        @Size(max = 100, message = "불량 유형은 100자 이하여야 합니다")
        private String defectType;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QualityResponse {
        private Long deliveredQuantity;
        private Long defectQuantity;
        private String defectType;
        private BigDecimal defectRate;
        private LocalDateTime updatedAt;

        public static QualityResponse from(Enrollment enrollment) {
            if (enrollment.getDeliveredQty() == null) return null;
            return QualityResponse.builder()
                    .deliveredQuantity(enrollment.getDeliveredQty())
                    .defectQuantity(enrollment.getDefectQty())
                    .defectType(enrollment.getDefectType())
                    .defectRate(enrollment.getDefectRate())
                    .updatedAt(enrollment.getEvaluatedAt())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderRequestResponse {
        private Long quantity;
        private String unit;
        private LocalDate deliveryDate;
        private String deliveryPlace;
        private String notes;
        private String contactName;
        private String contactPhone;
        private BigDecimal estimatedTotal;

        public static OrderRequestResponse from(Enrollment enrollment) {
            if (enrollment.getQuantity() == null) {
                return null;
            }
            return OrderRequestResponse.builder()
                    .quantity(enrollment.getQuantity())
                    .unit(enrollment.getUnit())
                    .deliveryDate(enrollment.getDeliveryDate())
                    .deliveryPlace(enrollment.getDeliveryPlace())
                    .notes(enrollment.getNotes())
                    .contactName(enrollment.getContactName())
                    .contactPhone(enrollment.getContactPhone())
                    .estimatedTotal(enrollment.getEstimatedTotal())
                    .build();
        }
    }

    // 공급성과 평가 등록 요청 (납품 후 구매기업이 지난 발주에 입력한다)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PerformanceRequest {
        // Quality
        @NotNull(message = "납품수량은 필수입니다")
        @Positive(message = "납품수량은 1 이상이어야 합니다")
        private Long deliveredQty;

        @NotNull(message = "불량수량은 필수입니다")
        @PositiveOrZero(message = "불량수량은 0 이상이어야 합니다")
        private Long defectQty;

        // 불량이 없으면 비워 둘 수 있다
        @Size(max = 100, message = "불량유형은 100자 이내여야 합니다")
        private String defectType;

        // Delivery — 희망 납품일은 발주 때 이미 받았으므로 실제 납품일만 받는다
        @NotNull(message = "실제 납품일은 필수입니다")
        private LocalDate actualDeliveryDate;

        // Cost — 선택. 비우면 비용 축은 평가하지 않는다
        @PositiveOrZero(message = "실제 청구금액은 0 이상이어야 합니다")
        private BigDecimal actualAmount;
    }

    // 공급성과 평가 결과 (평가 전에는 null)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PerformanceResponse {
        private Long deliveredQty;
        private Long defectQty;
        private String defectType;
        private BigDecimal defectRate;
        private LocalDate deliveryDate;        // 희망 (발주 시)
        private LocalDate actualDeliveryDate;  // 실제
        private Boolean onTime;
        private Integer delayDays;
        private BigDecimal estimatedTotal;     // 견적
        private BigDecimal actualAmount;       // 실제 청구
        private LocalDateTime evaluatedAt;

        public static PerformanceResponse from(Enrollment enrollment) {
            if (!enrollment.isEvaluated()) {
                return null;
            }
            return PerformanceResponse.builder()
                    .deliveredQty(enrollment.getDeliveredQty())
                    .defectQty(enrollment.getDefectQty())
                    .defectType(enrollment.getDefectType())
                    .defectRate(enrollment.getDefectRate())
                    .deliveryDate(enrollment.getDeliveryDate())
                    .actualDeliveryDate(enrollment.getActualDeliveryDate())
                    .onTime(enrollment.getOnTime())
                    .delayDays(enrollment.getDelayDays())
                    .estimatedTotal(enrollment.getEstimatedTotal())
                    .actualAmount(enrollment.getActualAmount())
                    .evaluatedAt(enrollment.getEvaluatedAt())
                    .build();
        }
    }

    // 강의 요약 정보 (내 수강 목록 표시용)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CourseSummary {
        private Long id;
        private String title;
        private String description;
        private String category;
        private Integer price;
        private String thumbnail;
        private String instructorName;
        private Integer enrollmentCount;
        // 이 품목을 등록한 공급기업의 누적 성과지표
        private BigDecimal defectRate;
        private BigDecimal onTimeRate;
        private BigDecimal costVarianceRate;
        private Integer evaluatedCount;
    }

    // 수강 응답
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnrollmentResponse {
        private Long id;
        private Long userId;
        private Long courseId;
        private Enrollment.Status status;
        private LocalDateTime createdAt;
        private OrderRequestResponse orderRequest;
        private PerformanceResponse performance;
        private QualityResponse quality;

        // 추가
        private CourseSummary course;

        public static EnrollmentResponse from(Enrollment enrollment) {
            return EnrollmentResponse.builder()
                    .id(enrollment.getId())
                    .userId(enrollment.getUserId())
                    .courseId(enrollment.getCourseId())
                    .status(enrollment.getStatus())
                    .createdAt(enrollment.getCreatedAt())
                    .orderRequest(OrderRequestResponse.from(enrollment))
                    .performance(PerformanceResponse.from(enrollment))
                    .quality(QualityResponse.from(enrollment))
                    .build();
        }

        public static EnrollmentResponse from(Enrollment enrollment, CourseSummary course) {
            return EnrollmentResponse.builder()
                    .id(enrollment.getId())
                    .userId(enrollment.getUserId())
                    .courseId(enrollment.getCourseId())
                    .status(enrollment.getStatus())
                    .createdAt(enrollment.getCreatedAt())
                    .course(course)
                    .orderRequest(OrderRequestResponse.from(enrollment))
                    .performance(PerformanceResponse.from(enrollment))
                    .quality(QualityResponse.from(enrollment))
                    .build();
        }
    }

    // 추천 서비스용: 수강 이력 조회 응답
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnrollmentHistoryResponse {
        private Long userId;
        private List<Long> activeCourseIds;
    }

    // 공통 API 응답 래퍼
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            return ApiResponse.<T>builder()
                    .success(true)
                    .message("성공")
                    .data(data)
                    .build();
        }

        public static <T> ApiResponse<T> error(String message) {
            return ApiResponse.<T>builder()
                    .success(false)
                    .message(message)
                    .build();
        }
    }
}
