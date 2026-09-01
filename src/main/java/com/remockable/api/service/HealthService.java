package com.remockable.api.service;

import com.remockable.api.common.RequestContext;
import com.remockable.api.config.AppProperties;
import com.remockable.api.model.dto.HealthResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private static final Instant STARTED_AT = Instant.now();

    private final AppProperties properties;
    private final String version;
    private final boolean llmConfigured;
    private final boolean sttConfigured;

    public HealthService(
            AppProperties properties,
            @Value("${remockable.version:0.1.0}") String version,
            @Value("${remockable.providers.llm.api-key:}") String llmApiKey,
            @Value("${remockable.providers.stt.api-key:}") String sttApiKey) {
        this.properties = properties;
        this.version = version;
        // 只回報「有沒有設定」，絕不回報金鑰本身或其片段。
        this.llmConfigured = !llmApiKey.isBlank();
        this.sttConfigured = !sttApiKey.isBlank();
    }

    public HealthResponse check() {
        Map<String, HealthResponse.Provider> providers = new LinkedHashMap<>();
        providers.put("llm", new HealthResponse.Provider(llmConfigured));
        providers.put("stt", new HealthResponse.Provider(sttConfigured));

        AppProperties.Limits limits = properties.limits();
        return new HealthResponse(
                "ok",
                "remockable-api",
                version,
                RequestContext.getRequestId(),
                Duration.between(STARTED_AT, Instant.now()).toSeconds(),
                providers,
                new HealthResponse.Limits(
                        limits.maxResumeBytes(),
                        limits.maxAudioBytes(),
                        limits.maxAnswerSeconds(),
                        limits.minAnswerChars(),
                        limits.maxAnswerChars(),
                        limits.dailyAddQuestionLimit(),
                        limits.maxQuestionsPerCategory()));
    }
}
