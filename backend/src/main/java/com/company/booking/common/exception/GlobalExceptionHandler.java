package com.company.booking.common.exception;

import com.company.booking.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 * 将异常按业务语义映射到 HTTP 状态码 + 业务 code。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** 未认证 → 401 */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<R<Void>> handleUnauthorized(UnauthorizedException e) {
        log.debug("unauthorized: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(R.fail(e.getCode(), e.getMessage()));
    }

    /** 已认证但无权限 → 403 */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<R<Void>> handleForbidden(ForbiddenException e) {
        log.warn("forbidden: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(R.fail(e.getCode(), e.getMessage()));
    }

    /** 预订冲突 → 409 */
    @ExceptionHandler(BookingConflictException.class)
    public ResponseEntity<R<Void>> handleBookingConflict(BookingConflictException e) {
        log.warn("booking conflict: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(R.fail(e.getCode(), e.getMessage()));
    }

    /** DB 唯一约束兜底 → 409 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<R<Void>> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("duplicate key (uncaught by service): {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(R.fail(BookingConflictException.CODE, "该时段已被预订"));
    }

    /** 时间参数非法 → 400 */
    @ExceptionHandler(InvalidTimeException.class)
    public ResponseEntity<R<Void>> handleInvalidTime(InvalidTimeException e) {
        log.warn("invalid time: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(e.getCode(), e.getMessage()));
    }

    /** 通用业务异常 → 400 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBiz(BizException e) {
        log.warn("biz error: code={}, msg={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(e.getCode(), e.getMessage()));
    }

    /** Bean Validation 校验失败 → 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("invalid param: {}", detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail("INVALID_PARAM", detail));
    }

    /** 缺失请求头 → 400 */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<R<Void>> handleMissingHeader(MissingRequestHeaderException e) {
        log.warn("missing header: {}", e.getHeaderName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail("MISSING_HEADER", "缺少请求头：" + e.getHeaderName()));
    }

    /** 静态资源 404（如根路径未配 welcome）→ 404 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<R<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(R.fail("NOT_FOUND", "资源不存在"));
    }

    /** 未捕获异常 → 500 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleAny(Exception e) {
        log.error("unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail("INTERNAL_ERROR", "系统繁忙"));
    }

    private String formatFieldError(FieldError fe) {
        return fe.getField() + ": " + fe.getDefaultMessage();
    }
}
