package com.lecture.course.dto;

import com.lecture.course.entity.Course;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CourseDto {

    // 강의 등록 요청
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotBlank(message = "강의 제목은 필수입니다")
        private String title;

        private String description;

        @NotNull(message = "카테고리는 필수입니다")
        private Course.Category category;

        @NotNull(message = "가격은 필수입니다")
        @PositiveOrZero(message = "가격은 0 이상이어야 합니다")
        private BigDecimal price;
    }

    // 품목 수정 요청
    //
    // description 을 받지 않는다. 조달 명세 16개 항목이 그 한 칸에 들어 있는데
    // 등록 폼은 기업구분·세부품명·물품식별번호·납품장소·쇼핑몰등록일자를 수집하지 않는다.
    // CreateRequest 를 그대로 재사용하면 단가 하나만 고쳐도 그 다섯 항목이 사라진다.
    // 시드로 넣은 262건이 수정 한 번에 깎여 나가는 형태다.
    //
    // description 을 고쳐야 하면 별도 경로를 만든다. 여기서는 서버가 기존 값을 지킨다.
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        @NotBlank(message = "제목은 필수입니다")
        @Size(max = 255, message = "제목은 255자 이하여야 합니다")
        private String title;

        @NotNull(message = "카테고리는 필수입니다")
        private Course.Category category;

        @NotNull(message = "가격은 필수입니다")
        @PositiveOrZero(message = "가격은 0 이상이어야 합니다")
        private BigDecimal price;
    }

    // 강의 응답
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CourseResponse {
        private Long id;
        private String title;
        private String description;
        private Course.Category category;
        private BigDecimal price;
        private Long instructorId;
        private String instructorName;
        private Integer enrollmentCount;
        private Course.Status status;
        private LocalDateTime createdAt;

        public static CourseResponse from(Course course) {
            return CourseResponse.builder()
                    .id(course.getId())
                    .title(course.getTitle())
                    .description(course.getDescription())
                    .category(course.getCategory())
                    .price(course.getPrice())
                    .instructorId(course.getInstructorId())
                    .instructorName(course.getInstructorName())
                    .enrollmentCount(course.getEnrollmentCount())
                    .status(course.getStatus())
                    .createdAt(course.getCreatedAt())
                    .build();
        }
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

    // 추천 서비스용 응답 (카테고리 기반 미수강 강의 목록)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecommendResponse {
        private List<CourseResponse> courses;
        private Course.Category category;
    }
}
