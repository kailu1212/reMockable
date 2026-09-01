package com.remockable.api.controller;

import com.remockable.api.model.dto.HealthResponse;
import com.remockable.api.service.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Meta", description = "健康檢查與非同步 Job 輪詢")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    @Operation(summary = "服務與 provider 就緒狀態",
            description = "前端啟動時讀一次，取得 limits 作為驗證規則的唯一來源。")
    public HealthResponse health() {
        return healthService.check();
    }
}
