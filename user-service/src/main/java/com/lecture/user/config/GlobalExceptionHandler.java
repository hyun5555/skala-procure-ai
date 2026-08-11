package com.lecture.user.config;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.lecture.user.dto.UserDto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<UserDto.ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(UserDto.ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<UserDto.ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(UserDto.ApiResponse.error(message));
    }

    /**
     * 본문을 역직렬화하지 못한 경우.
     * role 에 STUDENT/INSTRUCTOR 가 아닌 값이 오거나 JSON 자체가 깨진 요청이 여기로 온다.
     * 클라이언트 잘못이므로 500 이 아니라 400 이어야 한다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<UserDto.ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        String message = "요청 본문 형식이 올바르지 않습니다";

        if (e.getCause() instanceof InvalidFormatException cause
                && cause.getTargetType() != null
                && cause.getTargetType().isEnum()) {
            message = "허용되지 않는 값입니다: " + cause.getValue()
                    + " (가능한 값: " + Arrays.toString(cause.getTargetType().getEnumConstants()) + ")";
        }

        return ResponseEntity.badRequest()
                .body(UserDto.ApiResponse.error(message));
    }

    /**
     * 경로 변수 타입이 맞지 않는 경우. GET /api/users/abc 처럼 id 에 숫자가 아닌 값이 온 요청이다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<UserDto.ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(UserDto.ApiResponse.error(e.getName() + " 값이 올바르지 않습니다: " + e.getValue()));
    }

    /**
     * 필수 쿼리 파라미터가 빠진 경우. GET /api/users/register 에 email 이 없는 요청이다.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<UserDto.ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(UserDto.ApiResponse.error(e.getParameterName() + " 파라미터가 필요합니다"));
    }

    /**
     * DB 제약 위반.
     * users 테이블의 유일한 UNIQUE 제약은 email 이므로 중복 가입으로 단정할 수 있다.
     * UserService 의 existsByEmail 선검사와 INSERT 사이에 같은 이메일이 동시에 들어오면 여기로 떨어진다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<UserDto.ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.badRequest()
                .body(UserDto.ApiResponse.error("이미 사용 중인 이메일입니다"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<UserDto.ApiResponse<Void>> handleGeneral(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(UserDto.ApiResponse.error("서버 오류가 발생했습니다"));
    }
}
