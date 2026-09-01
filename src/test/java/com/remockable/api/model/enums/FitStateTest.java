package com.remockable.api.model.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 燈號門檻是產品規則（Spec D-025、AC-13），不是實作細節 ——
 * 綠燈 90 分以上、黃燈 60–89 分、紅燈未滿 60 分。
 * 邊界值特別容易寫錯，所以逐一釘住。
 */
class FitStateTest {

    @ParameterizedTest(name = "score {0} -> {1}")
    @CsvSource({
        "100, GREEN",
        "90,  GREEN",
        "89,  YELLOW",
        "60,  YELLOW",
        "59,  RED",
        "0,   RED"
    })
    @DisplayName("分數對應到正確燈號，含 90 與 60 的邊界")
    void mapsScoreToState(int score, FitState expected) {
        assertThat(FitState.fromScore(score)).isEqualTo(expected);
    }

    @Test
    @DisplayName("只有三個燈號，沒有第四種狀態")
    void hasExactlyThreeStates() {
        assertThat(FitState.values()).containsExactly(FitState.RED, FitState.YELLOW, FitState.GREEN);
    }
}
