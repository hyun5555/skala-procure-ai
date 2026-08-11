package com.lecture.course.service;

import com.lecture.course.dto.CourseDto;
import com.lecture.course.entity.Course;
import com.lecture.course.repository.CourseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private CourseService courseService;

    @Test
    void ownerCanMarkCourseAsSoldOut() {
        Course course = Course.builder()
                .id(7L)
                .instructorId(3L)
                .status(Course.Status.ACTIVE)
                .build();
        when(courseRepository.findById(7L)).thenReturn(Optional.of(course));

        CourseDto.CourseResponse response = courseService.updateCourseStatus(7L, 3L, Course.Status.INACTIVE);

        assertThat(response.getStatus()).isEqualTo(Course.Status.INACTIVE);
    }

    @Test
    void anotherInstructorCannotChangeCourseStatus() {
        Course course = Course.builder()
                .id(7L)
                .instructorId(3L)
                .status(Course.Status.ACTIVE)
                .build();
        when(courseRepository.findById(7L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.updateCourseStatus(7L, 99L, Course.Status.INACTIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("본인이 등록한 품목");
    }
}
