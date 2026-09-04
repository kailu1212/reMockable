package com.remockable.api.exception;

import com.remockable.api.common.RequestContext;
import com.remockable.api.model.dto.ErrorResp;
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
 * 把所有例外轉成統一的 {@link ErrorResp}。
 *
 * <p>刻意不把例外訊息回給前端：它可能含內部路徑或 SQL 片段。
 * 前端需要的只有 {@code code}、{@code messageKey} 與 {@code retryable}。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RespErr.class)
    public ResponseEntity<ErrorResp> handleRespErr(RespErr ex) {
        if (ex.getStatus() >= 500) {
            log.error("resp_err code={} status={} detail={}", ex.getCode(), ex.getStatus(), ex.getMessage(), ex);
        } else {
            log.warn("resp_err code={} status={} detail={}", ex.getCode(), ex.getStatus(), ex.getMessage());
        }
        return ResponseEntity.status(ex.getStatus())
                .body(ErrorResp.of(ex.getCode(), ex.getExtra(), RequestContext.getRequestId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResp> handleBeanValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> extra = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .ifPresent(fieldError -> {
                    extra.put("field", fieldError.getField());
                    extra.put("reason", fieldError.getDefaultMessage());
                });
        log.warn("validation_error extra={}", extra);
        return build(RespErrCode.VALIDATION_ERROR, extra.isEmpty() ? null : extra);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResp> handleMalformedRequest(Exception ex) {
        log.warn("malformed_request detail={}", ex.getMessage());
        return build(RespErrCode.VALIDATION_ERROR, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResp> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("upload_too_large detail={}", ex.getMessage());
        return build(RespErrCode.UPLOAD_TOO_LARGE, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResp> handleNoResource(NoResourceFoundException ex) {
        return build(RespErrCode.NOT_FOUND, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResp> handleUnexpected(Exception ex) {
        log.error("unhandled_exception", ex);
        return build(RespErrCode.INTERNAL_ERROR, null);
    }

    private ResponseEntity<ErrorResp> build(RespErrCode code, Map<String, Object> extra) {
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResp.of(code, extra, RequestContext.getRequestId()));
    }
}
