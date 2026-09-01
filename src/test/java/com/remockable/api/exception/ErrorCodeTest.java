package com.remockable.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 錯誤碼是前後端的契約（Spec §12）。這些測試釘住的是契約性質，不是實作細節。
 */
class ErrorCodeTest {

    @Test
    @DisplayName("每個 code 都有 message_key，且 message_key 不重複")
    void everyCodeHasUniqueMessageKey() {
        long distinct = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getMessageKey)
                .peek(key -> assertThat(key).isNotBlank())
                .collect(Collectors.toSet())
                .size();

        assertThat(distinct).isEqualTo(ErrorCode.values().length);
    }

    @Test
    @DisplayName("message_key 一律 snake_case 且不含中文 —— 中文文案由前端管理")
    void messageKeysAreAsciiSnakeCase() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.getMessageKey())
                    .as("message key of %s", code.name())
                    .matches("[a-z0-9_]+");
        }
    }

    @Test
    @DisplayName("額度用盡類的錯誤不可重試，避免前端做出無效的重試迴圈")
    void quotaErrorsAreNotRetryable() {
        assertThat(ErrorCode.ADD_QUESTION_LIMIT_REACHED.isRetryable()).isFalse();
        assertThat(ErrorCode.QUESTION_LIMIT_REACHED.isRetryable()).isFalse();
        assertThat(ErrorCode.MODEL_QUOTA_EXCEEDED.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("模型暫時性失敗可重試，前端才會顯示「請稍候再試」")
    void transientModelErrorsAreRetryable() {
        assertThat(ErrorCode.MODEL_UNAVAILABLE.isRetryable()).isTrue();
        assertThat(ErrorCode.ANALYSIS_UNAVAILABLE.isRetryable()).isTrue();
        assertThat(ErrorCode.QUESTION_GENERATION_INVALID.isRetryable()).isTrue();
    }
}
