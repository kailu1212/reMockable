package com.remockable.api.exception;

import com.remockable.api.common.RequestContext;
import com.remockable.api.model.dto.ErrorResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 把所有例外轉成統一的錯誤格式（docs/01-api-interface.md §1.3）。
 *
 * <p>刻意不回傳例外訊息給前端：訊息可能含內部路徑或 SQL 片段。
 * 前端需要的只有 {@code code} 與 {@code message_key}。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleApp(AppException ex) {
        ErrorCode code = ex.getCode();
        if (code.getStatus().is5xxServerError()) {
            log.error("app_error code={} detail={}", code.name(), ex.getMessage(), ex);
        } else {
            log.warn("app_error code={} detail={}", code.name(), ex.getMessage());
        }
        return build(code, ex.getMeta());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> meta = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .ifPresent(fieldError -> {
                    meta.put("field", fieldError.getField());
                    meta.put("reason", fieldError.getDefaultMessage());
                });
        log.warn("validation_error meta={}", meta);
        return build(ErrorCode.VALIDATION_ERROR, meta.isEmpty() ? null : meta);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception ex) {
        log.warn("malformed_request detail={}", ex.getMessage());
        return build(ErrorCode.VALIDATION_ERROR, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("upload_too_large detail={}", ex.getMessage());
        return build(ErrorCode.UPLOAD_TOO_LARGE, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return build(ErrorCode.NOT_FOUND, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("unhandled_exception", ex);
        return build(ErrorCode.INTERNAL_ERROR, null);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, Map<String, Object> meta) {
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, meta, RequestContext.getRequestId()));
    }
}
