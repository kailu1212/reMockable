package com.remockable.api.exception;

import java.util.Map;

/**
 * 所有可預期的業務錯誤都用這個丟出來，由 {@link GlobalExceptionHandler} 轉成統一的錯誤格式。
 *
 * <p>{@code detail} 只寫進伺服器 log，不會回給前端 —— 避免把內部細節洩漏到 API 回應。
 */
public class AppException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> meta;

    public AppException(ErrorCode code) {
        this(code, null, null, null);
    }

    public AppException(ErrorCode code, String detail) {
        this(code, detail, null, null);
    }

    public AppException(ErrorCode code, String detail, Map<String, Object> meta) {
        this(code, detail, meta, null);
    }

    public AppException(ErrorCode code, String detail, Map<String, Object> meta, Throwable cause) {
        super(detail != null ? detail : code.name(), cause);
        this.code = code;
        this.meta = meta;
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }
}
