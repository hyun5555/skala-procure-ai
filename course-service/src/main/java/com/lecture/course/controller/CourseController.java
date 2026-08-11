package com.lecture.course.controller;

import com.lecture.course.dto.CourseDto;
import com.lecture.course.entity.Course;
import com.lecture.course.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    /**
     * POST /courses - 강의 등록 (강사만)
     * Gateway에서 전달한 X-User-Id 헤더로 강사 ID 추출
     */
    @PostMapping
    public ResponseEntity<CourseDto.ApiResponse<CourseDto.CourseResponse>> createCourse(
            @Valid @RequestBody CourseDto.CreateRequest request,
            @RequestHeader("X-User-Id") Long instructorId) {

        CourseDto.CourseResponse response = courseService.createCourse(request, instructorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CourseDto.ApiResponse.success(response));
    }

    /**
     * GET /courses - 전체 강의 목록
     */
    @GetMapping
    public ResponseEntity<CourseDto.ApiResponse<List<CourseDto.CourseResponse>>> getAllCourses() {
        return ResponseEntity.ok(
                CourseDto.ApiResponse.success(courseService.getAllCourses())
        );
    }

    @GetMapping("/my")
    public ResponseEntity<CourseDto.ApiResponse<List<CourseDto.CourseResponse>>> getMyCourses(
            @RequestHeader("X-User-Id") Long instructorId) {
        return ResponseEntity.ok(
                CourseDto.ApiResponse.success(courseService.getCoursesByInstructor(instructorId))
        );
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<CourseDto.ApiResponse<CourseDto.CourseResponse>> updateCourseStatus(
            @PathVariable Long id,
            @Valid @RequestBody CourseDto.StatusRequest request,
            @RequestHeader("X-User-Id") Long instructorId) {
        return ResponseEntity.ok(
                CourseDto.ApiResponse.success(courseService.updateCourseStatus(id, instructorId, request.getStatus()))
        );
    }

    /**
     * PUT /courses/{id} - 품목 수정 (등록한 공급기업만)
     * Gateway가 전달한 X-User-Id 로 등록자를 대조한다
     */
    @PutMapping("/{id}")
    public ResponseEntity<CourseDto.ApiResponse<CourseDto.CourseResponse>> updateCourse(
            @PathVariable Long id,
            @Valid @RequestBody CourseDto.UpdateRequest request,
            @RequestHeader("X-User-Id") Long instructorId) {

        CourseDto.CourseResponse response = courseService.updateCourse(id, request, instructorId);
        return ResponseEntity.ok(CourseDto.ApiResponse.success(response));
    }

    /**
     * GET /courses/{id} - 강의 상세
     */
    @GetMapping("/{id}")
    public ResponseEntity<CourseDto.ApiResponse<CourseDto.CourseResponse>> getCourse(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                CourseDto.ApiResponse.success(courseService.getCourse(id))
        );
    }

    /**
     * GET /courses/category/{category} - 카테고리별 강의
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<CourseDto.ApiResponse<List<CourseDto.CourseResponse>>> getCoursesByCategory(
            @PathVariable Course.Category category) {
        return ResponseEntity.ok(
                CourseDto.ApiResponse.success(courseService.getCoursesByCategory(category))
        );
    }

    /**
     * GET /courses/internal/exists/{id} - 강의 존재 여부 (Enrollment Service 호출)
     */
    @GetMapping("/internal/exists/{id}")
    public ResponseEntity<Boolean> existsCourse(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.existsCourse(id));
    }

    /**
     * GET /courses/internal/{id} - 강의 상세 조회 (Enrollment Service 내부 호출용)
     * - 내 수강 목록 응답 조립 시 사용
     * - 래퍼 없이 CourseResponse만 직접 반환
     */
    @GetMapping("/internal/{id}")
    public ResponseEntity<CourseDto.CourseResponse> getCourseInternal(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.getCourse(id));
    }

    /**
     * POST /courses/internal/{id}/enrollment-count - 수강생 수 증가 (Enrollment Service 호출)
     */
    @PostMapping("/internal/{id}/enrollment-count")
    public ResponseEntity<Void> increaseEnrollmentCount(@PathVariable Long id) {
        courseService.increaseEnrollmentCount(id);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /courses/internal/{id}/enrollment-count/decrease - 거래건수 감소
     * Enrollment Service 가 발주 취소 시 호출한다
     */
    @PostMapping("/internal/{id}/enrollment-count/decrease")
    public ResponseEntity<Void> decreaseEnrollmentCount(@PathVariable Long id) {
        courseService.decreaseEnrollmentCount(id);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /courses/internal/{id}/performance - 공급성과 누적
     * Enrollment Service 가 평가 등록 시 호출한다
     */
    @PostMapping("/internal/{id}/performance")
    public ResponseEntity<Void> applyPerformance(
            @PathVariable Long id,
            @RequestBody CourseDto.InternalPerformanceRequest request) {

        courseService.applyPerformance(id, request);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /courses/internal/recommend - 추천 서비스용 미수강 강의 조회
     * category: 카테고리, excludeIds: 이미 수강한 강의 ID 목록
     */
    @GetMapping("/internal/recommend")
    public ResponseEntity<List<CourseDto.CourseResponse>> getRecommendCourses(
            @RequestParam Course.Category category,
            @RequestParam(defaultValue = "") List<Long> excludeIds) {
        return ResponseEntity.ok(courseService.getRecommendCourses(category, excludeIds));
    }
}
