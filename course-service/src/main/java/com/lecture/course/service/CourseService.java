package com.lecture.course.service;

import com.lecture.course.dto.CourseDto;
import com.lecture.course.entity.Course;
import com.lecture.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;
    private final UserServiceClient userServiceClient;

    /**
     * 강의 등록 (강사만 가능 - SecurityConfig에서 role 검증)
     *
     * 등록 시점에 공급기업 이름을 한 번 조회해 함께 저장한다.
     * 조회에 실패하면 null 로 두고 등록은 그대로 진행한다.
     */
    @Transactional
    public CourseDto.CourseResponse createCourse(CourseDto.CreateRequest request, Long instructorId) {
        Course course = Course.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .price(request.getPrice())
                .instructorId(instructorId)
                .instructorName(userServiceClient.getUserName(instructorId))
                .build();

        return CourseDto.CourseResponse.from(courseRepository.save(course));
    }

    /**
     * 강의 단건 조회
     */
    public CourseDto.CourseResponse getCourse(Long id) {
        Course course = findCourseById(id);
        return CourseDto.CourseResponse.from(course);
    }

    /**
     * 전체 활성 강의 목록 조회
     */
    public List<CourseDto.CourseResponse> getAllCourses() {
        return courseRepository.findByStatus(Course.Status.ACTIVE).stream()
                .map(CourseDto.CourseResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 카테고리별 강의 조회
     */
    public List<CourseDto.CourseResponse> getCoursesByCategory(Course.Category category) {
        return courseRepository.findByCategoryAndStatus(category, Course.Status.ACTIVE).stream()
                .map(CourseDto.CourseResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 품목 수정 (등록한 공급기업만 가능)
     *
     * 게이트웨이가 넣어 주는 X-User-Id 와 등록자를 대조한다. 서비스가 permitAll 이라
     * 이 대조를 빼면 남의 품목을 아무나 고칠 수 있다.
     */
    @Transactional
    public CourseDto.CourseResponse updateCourse(Long id, CourseDto.CreateRequest request, Long instructorId) {
        Course course = findCourseById(id);

        if (!course.getInstructorId().equals(instructorId)) {
            throw new AccessDeniedException("자신이 등록한 품목만 수정할 수 있습니다");
        }

        // description 은 건드리지 않는다. 조달 명세가 통째로 사라지는 것을 막는다.
        course.update(request.getTitle(), request.getCategory(), request.getPrice());

        return CourseDto.CourseResponse.from(course);
    }

    /**
     * 강의 존재 여부 확인 (Enrollment Service → Course Service REST 호출용)
     */
    public boolean existsCourse(Long id) {
        return courseRepository.existsById(id);
    }

    /**
     * 수강생 수 증가 (Enrollment Service 수강 활성화 시 호출)
     */
    @Transactional
    public void increaseEnrollmentCount(Long courseId) {
        Course course = findCourseById(courseId);
        course.increaseEnrollmentCount();
    }

    /**
     * 거래건수 감소 (Enrollment Service 발주 취소 시 호출)
     */
    @Transactional
    public void decreaseEnrollmentCount(Long courseId) {
        Course course = findCourseById(courseId);
        course.decreaseEnrollmentCount();
    }

    /**
     * 추천 서비스용: 카테고리별 미수강 강의 조회
     * - excludeCourseIds: 이미 수강한 강의 ID 목록
     */
    public List<CourseDto.CourseResponse> getRecommendCourses(
            Course.Category category, List<Long> excludeCourseIds) {

        List<Course> courses = excludeCourseIds.isEmpty()
                ? courseRepository.findByCategoryAndStatus(category, Course.Status.ACTIVE)
                : courseRepository.findByCategoryAndStatusAndIdNotIn(
                        category, Course.Status.ACTIVE, excludeCourseIds);

        // 수강생 수 기준 내림차순 정렬
        return courses.stream()
                .sorted((a, b) -> b.getEnrollmentCount() - a.getEnrollmentCount())
                .map(CourseDto.CourseResponse::from)
                .collect(Collectors.toList());
    }

    private Course findCourseById(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("강의를 찾을 수 없습니다: " + id));
    }
}
