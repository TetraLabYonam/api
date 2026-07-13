package com.example.attempt.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;

/**
 * 전역 예외 처리 핸들러
 * 모든 컨트롤러에서 발생하는 예외를 일관되게 처리
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * ResourceNotFoundException 처리
     * HTTP 404 (Not Found) 응답
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex,
            WebRequest request) {

        log.error("리소스를 찾을 수 없음: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                ex.getMessage(),
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorResponse);
    }

    /**
     * IllegalArgumentException 처리
     * HTTP 400 (Bad Request) 응답
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            WebRequest request) {

        log.error("잘못된 요청: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                ex.getMessage(),
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    /**
     * IllegalStateException 처리
     * HTTP 409 (Conflict) 응답 — 이미 처리된 상태, 동의 미완료, 위치 반경 밖 등
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException ex,
            WebRequest request) {

        log.warn("처리할 수 없는 상태: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                ex.getMessage(),
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorResponse);
    }

    /**
     * NoResourceFoundException 처리
     * WebSocket 등의 정적 리소스 404 에러를 조용히 처리
     * HTTP 404 (Not Found) 응답
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(
            NoResourceFoundException ex,
            WebRequest request) {

        String path = request.getDescription(false).replace("uri=", "");

        // WebSocket 관련 경로는 debug 레벨로 로그
        if (path.contains("/ws")) {
            log.debug("WebSocket 리소스를 찾을 수 없음: {}", path);
        } else {
            log.warn("리소스를 찾을 수 없음: {}", path);
        }

        ErrorResponse errorResponse = new ErrorResponse(
                "요청한 리소스를 찾을 수 없습니다.",
                LocalDateTime.now(),
                path
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorResponse);
    }

    /**
     * AuthenticationException 처리
     * HTTP 401 (Unauthorized) 응답
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex,
            WebRequest request) {

        log.warn("인증 실패: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                "인증에 실패했습니다. 로그인이 필요합니다.",
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(errorResponse);
    }

    /**
     * AccessDeniedException 처리
     * HTTP 403 (Forbidden) 응답
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex,
            WebRequest request) {

        log.warn("권한 없음: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                "접근 권한이 없습니다.",
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(errorResponse);
    }

    /**
     * 일반 Exception 처리
     * HTTP 500 (Internal Server Error) 응답
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception ex,
            WebRequest request) {

        log.error("서버 내부 오류 발생: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
                "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }
}