package com.remockable.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 錯誤碼是前後端的契約（Spec §12）。這些測試釘住的是契約性質，不是實作細節。
 */
class RespErrCodeTest {

    @Test
    @DisplayName("每個 code 都有 messageKey，且 messageKey 不重複")
    void everyCodeHasUniqueMessageKey() {
        long distinct = Arrays.stream(RespErrCode.values())
                .map(RespErrCode::getMessageKey)
                .peek(key -> assertThat(key).isNotBlank())
                .collect(Collectors.toSet())
                .size();

        assertThat(distinct).isEqualTo(RespErrCode.values().length);
    }

    @Test
    @DisplayName("messageKey 一律 snake_case 且不含中文 —— 中文文案由前端管理（Spec §12）")
    void messageKeysAreAsciiSnakeCase() {
        for (RespErrCode code : RespErrCode.values()) {
            assertThat(code.getMessageKey())
                    .as("message key of %s", code.name())
                    .matches("[a-z0-9_]+");
        }
    }

    @Test
    @DisplayName("對外的 code 就是 enum 名稱，不另外維護數字對照表")
    void codeIsEnumName() {
        assertThat(RespErrCode.JOB_PAGE_UNREADABLE.getCode()).isEqualTo("JOB_PAGE_UNREADABLE");
        assertThat(RespErrCode.of("JOB_PAGE_UNREADABLE")).isEqualTo(RespErrCode.JOB_PAGE_UNREADABLE);
        assertThat(RespErrCode.of("NOT_A_REAL_CODE")).isNull();
    }

    @Test
    @DisplayName("HTTP status 一律落在 4xx / 5xx，不會出現 always-200 的反模式")
    void statusIsAlwaysAnErrorStatus() {
        for (RespErrCode code : RespErrCode.values()) {
            assertThat(code.getStatus())
                    .as("status of %s", code.name())
                    .isBetween(400, 599);
        }
    }

    @Test
    @DisplayName("額度用盡類的錯誤不可重試，避免前端做出無效的重試迴圈")
    void quotaErrorsAreNotRetryable() {
        assertThat(RespErrCode.ADD_QUESTION_LIMIT_REACHED.isRetryable()).isFalse();
        assertThat(RespErrCode.QUESTION_LIMIT_REACHED.isRetryable()).isFalse();
        assertThat(RespErrCode.MODEL_QUOTA_EXCEEDED.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("模型暫時性失敗可重試，前端才會顯示「請稍候再試」")
    void transientModelErrorsAreRetryable() {
        assertThat(RespErrCode.MODEL_UNAVAILABLE.isRetryable()).isTrue();
        assertThat(RespErrCode.ANALYSIS_UNAVAILABLE.isRetryable()).isTrue();
        assertThat(RespErrCode.QUESTION_GENERATION_INVALID.isRetryable()).isTrue();
    }
}
