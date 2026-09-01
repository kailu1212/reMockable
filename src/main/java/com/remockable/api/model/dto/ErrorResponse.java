package com.remockable.api.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.remockable.api.exception.ErrorCode;
import java.util.Map;

/**
 * 統一錯誤格式。任何非 2xx 回應都是這個形狀（見 docs/01-api-interface.md §1.3）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String status, Body error, String requestId) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(String code, String messageKey, boolean retryable, Map<String, Object> meta) {}

    public static ErrorResponse of(ErrorCode code, Map<String, Object> meta, String requestId) {
        return new ErrorResponse(
                "failed",
                new Body(code.name(), code.getMessageKey(), code.isRetryable(), meta),
                requestId);
    }
}
