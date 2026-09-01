package com.remockable.api.exception;

import org.springframework.http.HttpStatus;

/**
 * 穩定的機器可讀錯誤碼。
 *
 * <p>對應 Spec §12：「API 只回傳穩定的 error.code 與 message_key；中文前端文案由產品與設計共同管理」。
 * 因此這個 enum 一律不帶中文句子 —— 前端拿 {@code messageKey} 去查自己的文案表。
 *
 * <p>沿用 prototype {@code server/lib/errors.js} 的既有契約，前端可直接複用 {@code src/i18n/errors.js}。
 */
public enum ErrorCode {

    // ---- 職缺資訊解析（Spec 4.1）----
    JOB_PAGE_UNREADABLE(HttpStatus.UNPROCESSABLE_ENTITY, "job_input_try_paste_text", true),
    JOB_URL_NOT_SUPPORTED(HttpStatus.UNPROCESSABLE_ENTITY, "job_url_not_supported", true),
    JOB_TEXT_TOO_SHORT(HttpStatus.UNPROCESSABLE_ENTITY, "job_input_too_short", true),

    // ---- 履歷（Spec 4.1）----
    RESUME_UNSUPPORTED_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "resume_unsupported_type", true),
    RESUME_EXTRACTION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "resume_extraction_failed", true),
    RESUME_EMPTY(HttpStatus.UNPROCESSABLE_ENTITY, "resume_empty", true),

    // ---- Mock Set（Spec 4.1）----
    MOCKSET_SOURCE_MISSING(HttpStatus.BAD_REQUEST, "mockset_source_missing", true),
    MOCKSET_IMMUTABLE(HttpStatus.CONFLICT, "mockset_immutable", false),
    MOCKSET_LIMIT_REACHED(HttpStatus.CONFLICT, "mockset_limit_reached", false),

    // ---- 題目生成（Spec 4.2 / 4.3）----
    QUESTION_GENERATION_INVALID(HttpStatus.BAD_GATEWAY, "question_generation_retry", true),
    QUESTION_GENERATION_BLOCKED(HttpStatus.UNPROCESSABLE_ENTITY, "question_generation_blocked", true),
    ADD_QUESTION_LIMIT_REACHED(HttpStatus.TOO_MANY_REQUESTS, "add_question_limit_reached", false),
    QUESTION_LIMIT_REACHED(HttpStatus.CONFLICT, "question_limit_reached", false),

    // ---- 回答（Spec 4.4）----
    EMPTY_ANSWER(HttpStatus.BAD_REQUEST, "answer_empty", true),
    ANSWER_TOO_SHORT(HttpStatus.BAD_REQUEST, "answer_too_short", true),
    ANSWER_TEXT_TOO_LONG(HttpStatus.BAD_REQUEST, "answer_text_too_long", true),
    ANSWER_TEXT_INVALID_CHARS(HttpStatus.BAD_REQUEST, "answer_text_invalid_chars", true),
    ANSWER_TOO_LONG(HttpStatus.BAD_REQUEST, "answer_too_long", false),
    AUDIO_UNSUPPORTED_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "audio_unsupported_type", true),
    UPLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "upload_too_large", true),
    STT_FAILED(HttpStatus.BAD_GATEWAY, "stt_failed", true),

    // ---- 分析與參考答案（Spec 4.5）----
    ANALYSIS_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "analysis_retry", true),
    REFERENCE_ANSWER_NOT_ALLOWED(HttpStatus.CONFLICT, "reference_answer_not_allowed", false),

    // ---- 模型層（所有 prompt 共用）----
    MODEL_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "model_unavailable", true),
    MODEL_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "model_quota_exceeded", false),
    MODEL_OUTPUT_INVALID(HttpStatus.BAD_GATEWAY, "model_output_invalid", true),

    // ---- 平台 ----
    PROVIDER_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "provider_not_configured", false),
    AUTH_TOKEN_EXPIRED(HttpStatus.GONE, "auth_token_expired", true),
    ACCESS_DENIED(HttpStatus.UNAUTHORIZED, "access_denied", false),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", true),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "idempotency_conflict", true),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "validation_error", true),
    NOT_FOUND(HttpStatus.NOT_FOUND, "not_found", false),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", true);

    private final HttpStatus status;
    private final String messageKey;
    private final boolean retryable;

    ErrorCode(HttpStatus status, String messageKey, boolean retryable) {
        this.status = status;
        this.messageKey = messageKey;
        this.retryable = retryable;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
