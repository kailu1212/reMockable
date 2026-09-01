package com.remockable.api.model.enums;

/**
 * Fit 燈號（Spec D-025）。
 *
 * <p>門檻：綠燈 90 分以上、黃燈 60–89 分、紅燈未滿 60 分。
 *
 * <p><b>numeric score 存在資料庫，但絕不出現在任何 API response</b> ——
 * Spec D-025 明訂「Server 保存 numeric score，UI 不顯示數字」。
 */
public enum FitState {
    RED,
    YELLOW,
    GREEN;

    private static final int GREEN_THRESHOLD = 90;
    private static final int YELLOW_THRESHOLD = 60;

    /** 由分數推導燈號。模型同時回傳 state 與 score 時，用這個驗證兩者一致。 */
    public static FitState fromScore(int score) {
        if (score >= GREEN_THRESHOLD) {
            return GREEN;
        }
        return score >= YELLOW_THRESHOLD ? YELLOW : RED;
    }
}
