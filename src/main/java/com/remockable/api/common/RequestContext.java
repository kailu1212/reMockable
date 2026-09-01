package com.remockable.api.common;

/**
 * 目前請求的 request id，供 log 與錯誤回應使用。
 *
 * <p>用 ThreadLocal 而非把 id 一路傳參，是為了讓 Service 層不需要為了記 log 而改簽章。
 * 非同步工作會顯式把值帶過去（見 AsyncConfig 的 decorator）。
 */
public final class RequestContext {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    private RequestContext() {}

    public static void setRequestId(String requestId) {
        REQUEST_ID.set(requestId);
    }

    public static String getRequestId() {
        return REQUEST_ID.get();
    }

    public static void clear() {
        REQUEST_ID.remove();
    }
}
