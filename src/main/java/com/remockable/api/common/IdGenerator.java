package com.remockable.api.common;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * 產生帶前綴的 ULID 識別碼，例如 {@code ms_01J8X2K4M7QRSTVWXYZ0123}。
 *
 * <p>不用自增整數的理由：ID 會出現在 URL 與前端 localStorage，自增整數可被列舉。
 * ULID 前 48 bit 是毫秒時間戳，因此字典序即時間序，分頁與除錯都比 UUID v4 方便。
 */
public final class IdGenerator {

    /** Crockford Base32：去掉 I、L、O、U，避免與 1、0 混淆。 */
    private static final char[] ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final String MOCKSET = "ms";
    public static final String JOB_POSTING = "jp";
    public static final String RESUME = "res";
    public static final String QUESTION_SET = "qs";
    public static final String QUESTION = "q";
    public static final String ATTEMPT = "att";
    public static final String ANALYSIS = "ana";
    public static final String REFERENCE_ANSWER = "ref";
    public static final String JOB = "job";
    public static final String USER = "usr";
    public static final String REQUEST = "req";

    private IdGenerator() {}

    /** @return {@code <prefix>_<26 字元 ULID>} */
    public static String newId(String prefix) {
        return prefix + "_" + ulid();
    }

    public static String ulid() {
        long timestamp = Instant.now().toEpochMilli();
        byte[] randomness = new byte[10];
        RANDOM.nextBytes(randomness);

        char[] out = new char[26];
        // 時間部分：48 bit → 10 個 Base32 字元
        for (int i = 9; i >= 0; i--) {
            out[i] = ENCODING[(int) (timestamp & 0x1F)];
            timestamp >>>= 5;
        }
        // 隨機部分：80 bit → 16 個 Base32 字元
        int bitBuffer = 0;
        int bitCount = 0;
        int outIndex = 10;
        for (byte b : randomness) {
            bitBuffer = (bitBuffer << 8) | (b & 0xFF);
            bitCount += 8;
            while (bitCount >= 5) {
                bitCount -= 5;
                out[outIndex++] = ENCODING[(bitBuffer >>> bitCount) & 0x1F];
            }
        }
        return new String(out);
    }
}
