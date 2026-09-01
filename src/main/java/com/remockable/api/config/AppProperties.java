package com.remockable.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 產品層級的限制值。
 *
 * <p>這些數字同時是後端驗證規則與前端驗證規則的來源 —— 前端透過 {@code GET /api/health}
 * 讀取，因此**不要在前後端任一側寫死**（docs/01-api-interface.md §5.1）。
 */
@ConfigurationProperties(prefix = "remockable")
public record AppProperties(Limits limits, Cors cors) {

    public record Limits(
            long maxResumeBytes,
            long maxAudioBytes,
            int maxAnswerSeconds,
            int minAnswerChars,
            int maxAnswerChars,
            int dailyAddQuestionLimit,
            int maxQuestionsPerCategory) {}

    public record Cors(java.util.List<String> allowedOrigins) {}
}
