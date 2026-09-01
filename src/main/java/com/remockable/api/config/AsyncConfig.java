package com.remockable.api.config;

import com.remockable.api.common.RequestContext;
import java.util.concurrent.Executor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * AI 工作的背景執行緒池。
 *
 * <p>Phase 1 用單機 {@code ThreadPoolTaskExecutor} —— 9/10 交付期限下這是最快能上線的選擇。
 * 之後要換成 SQS + 獨立 worker 時，只需要替換 {@code AiJobService} 的送件實作，
 * Controller 與 job 輪詢契約都不用動。
 *
 * <p>{@code CallerRunsPolicy}：佇列滿時讓呼叫端執行緒自己跑，而不是丟棄工作。
 * 對使用者而言慢一點好過工作憑空消失。
 */
@Configuration
public class AsyncConfig {

    @Bean("aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ai-job-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        // 背景工作沿用觸發它的 request id，log 才能串起整條鏈路。
        executor.setTaskDecorator(runnable -> {
            String requestId = RequestContext.getRequestId();
            return () -> {
                if (requestId != null) {
                    RequestContext.setRequestId(requestId);
                    MDC.put("requestId", requestId);
                }
                try {
                    runnable.run();
                } finally {
                    MDC.remove("requestId");
                    RequestContext.clear();
                }
            };
        });
        executor.initialize();
        return executor;
    }
}
