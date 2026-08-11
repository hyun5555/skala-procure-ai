package com.lecture.course.service;

import com.lecture.course.dto.CourseDto;
import com.lecture.course.entity.Course;
import com.lecture.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
                // 종료일을 클라이언트가 보내면 호출자마다 따로 고쳐야 하고, 새 호출자가
                // 잊으면 만료 필터가 조용히 아무것도 거르지 않는다. description 은
                // 모든 호출자가 이미 보내고 있으므로 서버가 한 번만 뽑는다.
                // 요청에 명시한 값이 있으면 그것을 우선한다.
                .contractEnd(request.getContractEnd() != null
                        ? request.getContractEnd()
                        : ContractPeriodParser.endDateOf(request.getDescription()))
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
        return activeAndUnexpired(courseRepository.findByStatus(Course.Status.ACTIVE));
    }

    /**
     * 계약이 끝난 품목은 목록에서 뺀다.
     *
     * 살 수 없는 것을 추천하면 안 된다. 조달 등록 데이터는 계약기간이 지나면
     * 실제로 발주가 불가능하다. 단건 조회(getCourse)는 거르지 않는다 —
     * 이미 발주한 건의 상세를 볼 수 있어야 하고, 발주 목록 조립에도 쓰인다.
     */
    private List<CourseDto.CourseResponse> activeAndUnexpired(List<Course> courses) {
        LocalDate today = LocalDate.now();
        return courses.stream()
                .filter(course -> !course.isContractExpired(today))
                .map(CourseDto.CourseResponse::from)
                .collect(Collectors.toList());
    }

    public List<CourseDto.CourseResponse> getCoursesByInstructor(Long instructorId) {
        return courseRepository.findByInstructorId(instructorId).stream()
                .map(CourseDto.CourseResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public CourseDto.CourseResponse updateCourseStatus(Long courseId, Long instructorId, Course.Status status) {
        Course course = findCourseById(courseId);
        if (!course.getInstructorId().equals(instructorId)) {
            throw new IllegalArgumentException("본인이 등록한 품목의 상태만 변경할 수 있습니다");
        }
        course.updateStatus(status);
        return CourseDto.CourseResponse.from(course);
    }

    /**
     * 카테고리별 강의 조회
     */
    public List<CourseDto.CourseResponse> getCoursesByCategory(Course.Category category) {
        return activeAndUnexpired(
                courseRepository.findByCategoryAndStatus(category, Course.Status.ACTIVE));
    }

    /**
     * 품목 수정 (등록한 공급기업만 가능)
     *
     * 게이트웨이가 넣어 주는 X-User-Id 와 등록자를 대조한다. 서비스가 permitAll 이라
     * 이 대조를 빼면 남의 품목을 아무나 고칠 수 있다.
     */
    @Transactional
    public CourseDto.CourseResponse updateCourse(Long id, CourseDto.UpdateRequest request, Long instructorId) {
        Course course = findCourseById(id);

        if (!course.getInstructorId().equals(instructorId)) {
            throw new AccessDeniedException("자신이 등록한 품목만 수정할 수 있습니다");
        }

        // description 은 건드리지 않는다. 조달 명세가 통째로 사라지는 것을 막는다.
        course.update(request.getTitle(), request.getCategory(),
                request.getPrice(), request.getContractEnd());

        return CourseDto.CourseResponse.from(course);
    }

    /**
     * 강의 존재 여부 확인 (Enrollment Service → Course Service REST 호출용)
     */
    public boolean existsCourse(Long id) {
        return courseRepository.existsByIdAndStatus(id, Course.Status.ACTIVE);
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
     * 공급성과 누적 (Enrollment Service 평가 등록 시 호출)
     *
     * 평가받은 품목 하나가 아니라 **그 공급기업의 모든 품목**에 반영한다.
     * 기획안이 "공급업체 품질지표" 라고 정의했고, 품목마다 따로 쌓으면 신규 품목은
     * 언제까지나 실적이 없는 상태로 남아 비교 대상이 되지 못한다.
     */
    @Transactional
    public void applyPerformance(Long courseId, CourseDto.InternalPerformanceRequest request) {
        Course evaluated = findCourseById(courseId);
        List<Course> ownedBySameSupplier = courseRepository.findByInstructorId(evaluated.getInstructorId());

        for (Course course : ownedBySameSupplier) {
            course.applyPerformance(request.getDeliveredQty(), request.getDefectQty(),
                    request.getOnTime(), request.getEstimatedAmount(), request.getActualAmount());
        }
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

        // 계약이 끝난 품목은 추천 후보에서도 뺀다
        LocalDate today = LocalDate.now();
        return courses.stream()
                .filter(course -> !course.isContractExpired(today))
                .sorted((a, b) -> b.getEnrollmentCount() - a.getEnrollmentCount())
                .map(CourseDto.CourseResponse::from)
                .collect(Collectors.toList());
    }

    private Course findCourseById(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("강의를 찾을 수 없습니다: " + id));
    }
}
