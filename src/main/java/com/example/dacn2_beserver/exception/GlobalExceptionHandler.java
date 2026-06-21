package com.example.dacn2_beserver.exception;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import javax.naming.AuthenticationException;
import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    // 1. Handle ApiException (tất cả custom exception của mình)
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex,
                                                            HttpServletRequest request) {

        ErrorCode errorCode = ex.getErrorCode();
        HttpStatus status = errorCode.getHttpStatus();

        ErrorResponse body = ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(ex.getMessage())
                .status(status.value())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(status).body(body);
    }

    // 2. Validation @Valid trên DTO (body JSON)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                      HttpServletRequest request) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));

        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;

        ErrorResponse body = ErrorResponse.builder()
                .code(errorCode.getCode())
                .message("Validation failed")
                .status(errorCode.getHttpStatus().value())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .errors(errors)
                .build();

        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }

    // 3. Validation @Validated trên query param / path variable
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations()
                .forEach(violation -> errors.put(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ));

        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;

        ErrorResponse body = ErrorResponse.builder()
                .code(errorCode.getCode())
                .message("Validation failed")
                .status(errorCode.getHttpStatus().value())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .errors(errors)
                .build();

        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }

    // 4. Spring Security: chưa login / token sai
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex,
                                                                       HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;

        ErrorResponse body = ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(ex.getMessage())
                .status(errorCode.getHttpStatus().value())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }

    // 5. Spring Security: không đủ quyền
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.FORBIDDEN;

        ErrorResponse body = ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(ex.getMessage())
                .status(errorCode.getHttpStatus().value())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }

    // 6. JWT (token hết hạn, sai, bị chỉnh sửa...)
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ErrorResponse> handleJwtException(JwtException ex,
                                                            HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.INVALID_TOKEN;

        ErrorResponse body = ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(ex.getMessage())
                .status(errorCode.getHttpStatus().value())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }

    // 7. Sai method (GET thành POST, v.v.)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                  HttpServletRequest request) {

        ErrorResponse body = ErrorResponse.builder()
                .code(ErrorCode.BAD_REQUEST.getCode())
                .message(ex.getMessage())
                .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    // 8. Lỗi khi call AI server / Google Fit (WebClient / RestTemplate)
    @ExceptionHandler({RestClientException.class})
    public ResponseEntity<ErrorResponse> handleExternalService(Exception ex,
                                                               HttpServletRequest request) {

        ErrorCode errorCode = ErrorCode.EXTERNAL_SERVICE_ERROR;

        ErrorResponse body = ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(ex.getMessage())
                .status(errorCode.getHttpStatus().value())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }

    // 9. Fallback cuối cùng: lỗi không đoán được
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAll(Exception ex,
                                                   HttpServletRequest request) {

        ex.printStackTrace(); // TODO: sau này đổi sang logger

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        ErrorResponse body = ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(ex.getMessage())
                .status(errorCode.getHttpStatus().value())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }
}