package com.remockable.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI：{@code http://localhost:8080/swagger-ui.html}
 *
 * <p>手寫的權威規格在 {@code docs/openapi.yaml}；這裡產生的是實作現況，
 * 兩者對照可以看出哪些 endpoint 還沒實作完。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI remockableOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("reMockable Backend API")
                        .version("0.1.0")
                        .description("""
                                reMockable MVP 後端 API。對應 Spec v0.8.0。

                                認證：Authorization: Bearer <token>
                                - P1A/P1B/P2A/P2B：optional，後端忽略（以無帳號流程驗證）
                                - P2C 起：required

                                權威規格見 repo 內 docs/openapi.yaml。
                                """))
                .schemaRequirement("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));
    }
}
