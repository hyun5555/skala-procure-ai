package com.lecture.course.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClient {

    private final WebClient.Builder webClientBuilder;

    /**
     * User Service: 공급기업 이름 조회 (동기 REST)
     *
     * 품목 등록 시 한 번만 부른다. 조회 때마다 부르면 목록 한 번에
     * 품목 수만큼 호출이 나간다.
     *
     * 실패해도 예외를 던지지 않고 null 을 돌려준다. 이름을 못 얻는 것이
     * 품목 등록을 막을 이유는 없다. 화면은 이름이 없으면 '공급기업' 으로
     * 떨어지므로 등록 자체는 성사시키는 편이 낫다.
     */
    public String getUserName(Long userId) {
        try {
            Map<String, Object> body = webClientBuilder.build()
                    .get()
                    .uri("http://user-service/api/users/internal/{id}", userId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (body == null) {
                log.warn("[UserServiceClient] 응답 본문이 비어 있습니다 - userId: {}", userId);
                return null;
            }

            Object name = body.get("name");
            if (name == null) {
                // 응답이 { success, message, data } 로 감싸이도록 바뀌면 예외 없이
                // 조용히 null 이 된다. 화면은 '공급기업' 으로 떨어지므로 아무도
                // 눈치채지 못한다. 한 줄 남겨 두면 원인을 찾는 시간이 크게 준다.
                log.warn("[UserServiceClient] 응답에 name 이 없다 - userId: {}, body: {}", userId, body);
                return null;
            }
            return name.toString();
        } catch (Exception e) {
            log.warn("[UserServiceClient] 사용자 이름 조회 실패 - userId: {}, error: {}",
                    userId, e.getMessage());
            return null;
        }
    }
}
