package com.remockable.api.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;

/**
 * 所有可預期的業務錯誤都用這個丟出來，由 {@link GlobalExceptionHandler} 轉成 {@link
 * com.remockable.api.model.dto.ErrorResp}。
 *
 * <p><b>getMessage() 只寫進伺服器 log，絕不回給前端</b> —— 它可能含內部路徑、SQL 片段或
 * 使用者輸入。前端需要的只有 {@code code}、{@code messageKey} 與 {@code retryable}。
 */
@Getter
public class RespErr extends RuntimeException {

    private final RespErrCode code;
    private final int status;
    private transient Map<String, Object> extra;

    public RespErr(RespErrCode code) {
        super(code.name());
        this.code = code;
        this.status = code.getStatus();
    }

    public RespErr(RespErrCode code, String detail) {
        super(isBlank(detail) ? code.name() : detail);
        this.code = code;
        this.status = code.getStatus();
    }

    public RespErr(RespErrCode code, String detail, Throwable cause) {
        super(isBlank(detail) ? code.name() : detail, cause);
        this.code = code;
        this.status = code.getStatus();
    }

    public RespErr(RespErrCode code, Throwable cause) {
        super(cause == null ? code.name() : cause.getMessage(), cause);
        this.code = code;
        this.status = code.getStatus();
    }

    /** 覆寫 HTTP status 的情境很少，但保留給同一個 code 在不同流程需要不同 status 時使用。 */
    public RespErr(int status, RespErrCode code, String detail) {
        super(isBlank(detail) ? code.name() : detail, null);
        this.code = code;
        this.status = status;
    }

    public static RespErr format(RespErrCode code, String format, Object... args) {
        return new RespErr(code, String.format(format, args));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public RespErr putExtra(String key, Object value) {
        if (extra == null) {
            extra = new LinkedHashMap<>();
        }
        extra.put(key, value);
        return this;
    }

    public RespErr putExtra(Map<String, ?> values) {
        if (values == null) {
            return this;
        }
        if (extra == null) {
            extra = new LinkedHashMap<>(values);
        } else {
            extra.putAll(values);
        }
        return this;
    }
}
