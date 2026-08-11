package com.lecture.user.controller;

import com.lecture.user.dto.UserDto;
import com.lecture.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * POST /users/register - 회원가입 (인증 불필요)
     */
    @PostMapping("/register")
    public ResponseEntity<UserDto.ApiResponse<UserDto.UserResponse>> register(
            @Valid @RequestBody UserDto.RegisterRequest request) {
        UserDto.UserResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserDto.ApiResponse.success(response));
    }

    /**
     * GET /users/register?email=... - 이메일 사용 가능 여부 (인증 불필요)
     *
     * 게이트웨이가 토큰 없이 통과시키는 경로는 /api/users/register 하나뿐이다.
     * 가입 화면은 로그인 전이므로 신규 경로를 만들면 401 에 막혀 도달하지 못한다.
     * 그래서 새 경로 대신 이미 열려 있는 경로에 GET 을 얹었다.
     * 게이트웨이는 이미지로만 제공되어 허용 규칙을 바꿀 수 없다.
     */
    @GetMapping("/register")
    public ResponseEntity<UserDto.ApiResponse<UserDto.EmailAvailability>> checkEmail(
            @RequestParam String email) {
        return ResponseEntity.ok(
                UserDto.ApiResponse.success(userService.checkEmailAvailability(email))
        );
    }

    /**
     * GET /users/{id} - 사용자 조회 (인증 필요)
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDto.ApiResponse<UserDto.UserResponse>> getUser(
            @PathVariable Long id) {
        UserDto.UserResponse response = userService.getUserById(id);
        return ResponseEntity.ok(UserDto.ApiResponse.success(response));
    }

    /**
     * GET /users/me - 내 정보 조회
     * API Gateway가 전달한 X-User-Id 헤더(숫자 userId)를 사용
     */
    @GetMapping("/me")
    public ResponseEntity<UserDto.ApiResponse<UserDto.UserResponse>> getMe(
            @RequestHeader("X-User-Id") Long userId) {

        UserDto.UserResponse response = userService.getUserById(userId);
        return ResponseEntity.ok(UserDto.ApiResponse.success(response));
    }

    /**
     * GET /users/internal/{id} - 서비스 간 내부 호출용 (Client Credentials)
     */
    @GetMapping("/internal/{id}")
    public ResponseEntity<UserDto.UserResponse> getUserInternal(@PathVariable Long id) {
        UserDto.UserResponse response = userService.getUserById(id);
        return ResponseEntity.ok(response);
    }
}