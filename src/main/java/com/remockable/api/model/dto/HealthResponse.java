package com.remockable.api.model.dto;

import java.util.Map;

/**
 * {@code GET /api/health} 的回應。
 *
 * <p>{@code limits} 是前端驗證規則的唯一來源 —— 前端啟動時讀一次，
 * 不要把 100/2000 字元、90 秒、3 題、10 MB 寫死在程式碼裡。
 */
public record HealthResponse(
        String status,
        String service,
        String version,
        String requestId,
        long uptimeSeconds,
        Map<String, Provider> providers,
        Limits limits) {

    public record Provider(boolean configured) {}

    public record Limits(
            long maxResumeBytes,
            long maxAudioBytes,
            int maxAnswerSeconds,
            int minAnswerChars,
            int maxAnswerChars,
            int dailyAddQuestionLimit,
            int maxQuestionsPerCategory) {}
}
