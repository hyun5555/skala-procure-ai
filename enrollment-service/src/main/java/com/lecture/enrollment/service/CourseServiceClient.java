package com.lecture.enrollment.service;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseServiceClient {

    private final WebClient.Builder webClientBuilder;

    /**
     * Course Service: 강의 존재 여부 확인 (동기 REST)
     */
    public boolean existsCourse(Long courseId) {
        try {
            Boolean exists = webClientBuilder.build()
                    .get()
                    .uri("http://course-service/api/courses/internal/exists/{id}", courseId)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();

            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("[CourseServiceClient] 강의 존재 확인 실패 - courseId: {}, error: {}",
                    courseId, e.getMessage());
            throw new RuntimeException("Course Service 연결 실패");
        }
    }

    /**
     * Course Service: 강의 상세 조회
     * - 내 수강 목록 응답에 course 정보를 붙일 때 사용
     * - course-service 쪽에 GET /api/courses/internal/{id} 엔드포인트가 있어야 함
     */
    public Map<String, Object> getCourse(Long courseId) {
        try {
            Map<String, Object> responseBody = webClientBuilder.build()
                    .get()
                    .uri("http://course-service/api/courses/internal/{id}", courseId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (responseBody == null) {
                throw new RuntimeException("Course Service 응답 본문이 비어 있습니다.");
            }

            log.info("[CourseServiceClient] 강의 상세 조회 성공 - courseId: {}", courseId);
            log.debug("[CourseServiceClient] 강의 상세 응답 - courseId: {}, body: {}", courseId, responseBody);

            /*
             * 응답 형태가 다음 둘 중 하나일 수 있으므로 둘 다 처리
             *
             * 1) 래퍼 응답
             * {
             *   "success": true,
             *   "message": "성공",
             *   "data": { ...course fields... }
             * }
             *
             * 2) 바로 강의 객체 반환
             * {
             *   "id": 1,
             *   "title": "...",
             *   ...
             * }
             */
            Object data = responseBody.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> courseMap = (Map<String, Object>) dataMap;
                return courseMap;
            }

            return responseBody;
        } catch (Exception e) {
            log.error("[CourseServiceClient] 강의 상세 조회 실패 - courseId: {}, error: {}",
                    courseId, e.getMessage());
            throw new RuntimeException("Course Service 강의 상세 조회 실패");
        }
    }

    /**
     * Course Service: 수강생 수 증가 (수강 활성화 시 호출)
     */
    public void increaseEnrollmentCount(Long courseId) {
        try {
            webClientBuilder.build()
                    .post()
                    .uri("http://course-service/api/courses/internal/{id}/enrollment-count", courseId)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("[CourseServiceClient] 수강생 수 증가 완료 - courseId: {}", courseId);
        } catch (Exception e) {
            log.error("[CourseServiceClient] 수강생 수 증가 실패 - courseId: {}, error: {}",
                    courseId, e.getMessage());
        }
    }

    /**
     * Course Service: 거래건수 감소 (발주 취소 시 호출)
     *
     * 실패해도 예외를 던지지 않는다. 증가 쪽과 같은 방침이다.
     * 거래건수는 지표일 뿐이고 이것 때문에 취소가 막히면 사용자가 더 곤란하다.
     */
    public void decreaseEnrollmentCount(Long courseId) {
        try {
            webClientBuilder.build()
                    .post()
                    .uri("http://course-service/api/courses/internal/{id}/enrollment-count/decrease", courseId)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("[CourseServiceClient] 거래건수 감소 완료 - courseId: {}", courseId);
        } catch (Exception e) {
            log.error("[CourseServiceClient] 거래건수 감소 실패 - courseId: {}, error: {}",
                    courseId, e.getMessage());
        }
    }

    /**
     * Course Service: 공급성과 누적 (평가 등록 시)
     *
     * 여기는 예외를 던진다. 거래건수와 달리 이 값은 기획안의 핵심 차별점인
     * 추천 근거로 쓰인다. 조용히 실패하면 평가는 등록됐는데 지표에는 반영되지 않아
     * 두 값이 어긋난 채로 남는다. 실패를 알려 재시도할 수 있게 한다.
     */
    public void applyPerformance(Long courseId, Long deliveredQty, Long defectQty, Boolean onTime,
                                 BigDecimal estimatedAmount, BigDecimal actualAmount) {
        try {
            webClientBuilder.build()
                    .post()
                    .uri("http://course-service/api/courses/internal/{id}/performance", courseId)
                    .bodyValue(new PerformanceRequest(deliveredQty, defectQty, onTime,
                            estimatedAmount, actualAmount))
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("[CourseServiceClient] 공급성과 누적 완료 - courseId: {}, 납품 {}, 불량 {}, 납기준수 {}",
                    courseId, deliveredQty, defectQty, onTime);
        } catch (Exception e) {
            log.error("[CourseServiceClient] 공급성과 누적 실패 - courseId: {}, error: {}",
                    courseId, e.getMessage());
            throw new RuntimeException("공급성과를 공급기업 지표에 반영하지 못했습니다");
        }
    }

    @Getter
    @NoArgsConstructor
    static class PerformanceRequest {
        private Long deliveredQty;
        private Long defectQty;
        private Boolean onTime;
        private BigDecimal estimatedAmount;
        private BigDecimal actualAmount;

        PerformanceRequest(Long deliveredQty, Long defectQty, Boolean onTime,
                           BigDecimal estimatedAmount, BigDecimal actualAmount) {
            this.deliveredQty = deliveredQty;
            this.defectQty = defectQty;
            this.onTime = onTime;
            this.estimatedAmount = estimatedAmount;
            this.actualAmount = actualAmount;
        }
    }
}