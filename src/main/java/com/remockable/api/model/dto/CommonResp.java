package com.remockable.api.model.dto;

import lombok.Data;

/**
 * 單筆資料的統一回應包裝。
 *
 * <p>刻意不放 {@code code} / {@code message} —— 成敗由 HTTP status 表達。
 * 把成敗塞進 body 並永遠回 200 會讓 ALB／CloudWatch 的 5xx 告警失效、
 * 瀏覽器 devtools 看不出哪支失敗、CDN 把錯誤回應當成功快取。
 *
 * <p>錯誤回應見 {@link ErrorResp}。
 */
@Data
public class CommonResp<DATA> {

    private long timestamp = System.currentTimeMillis();
    private DATA data;

    public static <DATA> CommonResp<DATA> resp(DATA data) {
        CommonResp<DATA> resp = new CommonResp<>();
        resp.setData(data);
        return resp;
    }
}
