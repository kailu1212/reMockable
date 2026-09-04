package com.remockable.api.exception;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

/**
 * 穩定的錯誤碼。前端只依賴 {@code code} 與 {@code messageKey}，不依賴後端的訊息文字。
 *
 * <p><b>為什麼 code 用 enum 名稱而不是負整數：</b>兩個 branch 平行開發時數字碼一定會撞
 * （公司既有專案就為此在 static block 加了唯一性檢查，並留下 -111 撞號改 -115 的紀錄）。
 * 字串碼天生不會撞，log 與前端也都直接看得懂，不必回查對照表。
 *
 * <p><b>為什麼不帶中文文案：</b>Spec §12 明訂「API 只回傳穩定的 error.code 與 message_key；
 * 中文前端文案由產品與設計共同管理」。錯誤文案在這個產品裡是設計稿的一部分
 * （紅字／toast／欄位下方／幾秒消失），PM 與設計要能自己改，不該每次改字都重新部署後端。
 */
@Getter
public enum RespErrCode {

    // ---- 職缺資訊解析（Spec 4.1）----
    JOB_PAGE_UNREADABLE(422, "job_input_try_paste_text", true),
    JOB_URL_NOT_SUPPORTED(422, "job_url_not_supported", true),
    JOB_TEXT_TOO_SHORT(422, "job_input_too_short", true),

    // ---- 履歷（Spec 4.1）----
    RESUME_UNSUPPORTED_TYPE(415, "resume_unsupported_type", true),
    RESUME_EXTRACTION_FAILED(422, "resume_extraction_failed", true),
    RESUME_EMPTY(422, "resume_empty", true),

    // ---- Mock Set（Spec 4.1）----
    MOCKSET_SOURCE_MISSING(400, "mockset_source_missing", true),
    MOCKSET_IMMUTABLE(409, "mockset_immutable", false),
    MOCKSET_LIMIT_REACHED(409, "mockset_limit_reached", false),

    // ---- 題目生成（Spec 4.2 / 4.3）----
    QUESTION_GENERATION_INVALID(502, "question_generation_retry", true),
    QUESTION_GENERATION_BLOCKED(422, "question_generation_blocked", true),
    ADD_QUESTION_LIMIT_REACHED(429, "add_question_limit_reached", false),
    QUESTION_LIMIT_REACHED(409, "question_limit_reached", false),

    // ---- 回答（Spec 4.4）----
    EMPTY_ANSWER(400, "answer_empty", true),
    ANSWER_TOO_SHORT(400, "answer_too_short", true),
    ANSWER_TEXT_TOO_LONG(400, "answer_text_too_long", true),
    ANSWER_TEXT_INVALID_CHARS(400, "answer_text_invalid_chars", true),
    ANSWER_TOO_LONG(400, "answer_too_long", false),
    AUDIO_UNSUPPORTED_TYPE(415, "audio_unsupported_type", true),
    UPLOAD_TOO_LARGE(413, "upload_too_large", true),
    STT_FAILED(502, "stt_failed", true),

    // ---- 分析與參考答案（Spec 4.5）----
    ANALYSIS_UNAVAILABLE(502, "analysis_retry", true),
    REFERENCE_ANSWER_NOT_ALLOWED(409, "reference_answer_not_allowed", false),

    // ---- 模型層（所有 prompt 共用）----
    MODEL_UNAVAILABLE(502, "model_unavailable", true),
    MODEL_QUOTA_EXCEEDED(429, "model_quota_exceeded", false),
    MODEL_OUTPUT_INVALID(502, "model_output_invalid", true),

    // ---- 平台 ----
    PROVIDER_NOT_CONFIGURED(503, "provider_not_configured", false),
    AUTH_TOKEN_EXPIRED(410, "auth_token_expired", true),
    ACCESS_DENIED(401, "access_denied", false),
    RATE_LIMITED(429, "rate_limited", true),
    IDEMPOTENCY_CONFLICT(409, "idempotency_conflict", true),
    VALIDATION_ERROR(400, "validation_error", true),
    NOT_FOUND(404, "not_found", false),
    INTERNAL_ERROR(500, "internal_error", true);

    private static final Map<String, RespErrCode> INSTANCES = new HashMap<>();

    static {
        for (RespErrCode code : values()) {
            INSTANCES.put(code.name(), code);
        }
    }

    private final int status;
    private final String messageKey;
    private final boolean retryable;

    RespErrCode(int status, String messageKey, boolean retryable) {
        this.status = status;
        this.messageKey = messageKey;
        this.retryable = retryable;
    }

    /** 對外的錯誤碼就是 enum 名稱，不另外維護數字對照表。 */
    public String getCode() {
        return name();
    }

    public static RespErrCode of(String code) {
        return INSTANCES.get(code);
    }
}
