package com.remockable.api.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.remockable.api.exception.RespErrCode;
import java.util.Map;
import lombok.Data;

/**
 * 統一錯誤回應。HTTP status 照 {@link RespErrCode} 的定義回，不會永遠回 200。
 *
 * <p>{@code timestamp} 與 {@code extra} 沿用團隊既有的 CommonResp / RespErr 風格。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResp {

    private long timestamp = System.currentTimeMillis();

    /** 穩定機器碼，等於 RespErrCode 的 enum 名稱。 */
    private String code;

    /** 前端查文案表的鍵。後端不回中文句子（Spec §12）。 */
    private String messageKey;

    /** true 時前端應提供「請稍候再試」與重試入口。 */
    private boolean retryable;

    private String requestId;

    private Map<String, Object> extra;

    public static ErrorResp of(RespErrCode code, Map<String, Object> extra, String requestId) {
        ErrorResp resp = new ErrorResp();
        resp.setCode(code.getCode());
        resp.setMessageKey(code.getMessageKey());
        resp.setRetryable(code.isRetryable());
        resp.setRequestId(requestId);
        resp.setExtra(extra);
        return resp;
    }
}
