package com.remockable.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RespErrTest {

    @Test
    @DisplayName("未指定 status 時沿用 RespErrCode 的預設值")
    void inheritsStatusFromCode() {
        RespErr err = new RespErr(RespErrCode.ADD_QUESTION_LIMIT_REACHED);

        assertThat(err.getStatus()).isEqualTo(429);
        assertThat(err.getCode()).isEqualTo(RespErrCode.ADD_QUESTION_LIMIT_REACHED);
    }

    @Test
    @DisplayName("detail 只進 getMessage()（供 log），不影響對外的 code")
    void detailStaysInternal() {
        RespErr err = new RespErr(RespErrCode.RESUME_EMPTY, "pdf has 0 extractable characters");

        assertThat(err.getMessage()).isEqualTo("pdf has 0 extractable characters");
        assertThat(err.getCode().getMessageKey()).isEqualTo("resume_empty");
    }

    @Test
    @DisplayName("detail 為空白時退回 code 名稱，log 不會出現空訊息")
    void blankDetailFallsBackToCodeName() {
        assertThat(new RespErr(RespErrCode.NOT_FOUND, "  ").getMessage()).isEqualTo("NOT_FOUND");
        assertThat(new RespErr(RespErrCode.NOT_FOUND, (String) null).getMessage()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("putExtra 可鏈式累加，且不會被後續呼叫覆蓋掉先前的值")
    void putExtraAccumulates() {
        RespErr err = new RespErr(RespErrCode.UPLOAD_TOO_LARGE)
                .putExtra("limit", 10_000_000L)
                .putExtra(Map.of("actual", 12_345_678L));

        assertThat(err.getExtra()).containsEntry("limit", 10_000_000L).containsEntry("actual", 12_345_678L);
    }

    @Test
    @DisplayName("putExtra 傳 null 不會清掉既有內容，也不會拋錯")
    void putExtraIgnoresNull() {
        RespErr err = new RespErr(RespErrCode.VALIDATION_ERROR).putExtra("field", "name").putExtra((Map<String, ?>) null);

        assertThat(err.getExtra()).containsEntry("field", "name");
    }
}
