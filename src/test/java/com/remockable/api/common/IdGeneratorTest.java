package com.remockable.api.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdGeneratorTest {

    @Test
    @DisplayName("ID 帶前綴，且 ULID 部分固定 26 字元")
    void producesPrefixedUlid() {
        String id = IdGenerator.newId(IdGenerator.MOCKSET);

        assertThat(id).startsWith("ms_");
        assertThat(id.substring(3)).hasSize(26);
    }

    @Test
    @DisplayName("只使用 Crockford Base32 字元，不含容易混淆的 I L O U")
    void usesCrockfordAlphabet() {
        String ulid = IdGenerator.ulid();

        assertThat(ulid).matches("[0-9A-HJKMNP-TV-Z]{26}");
    }

    @Test
    @DisplayName("大量產生不重複")
    void isUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(IdGenerator.ulid());
        }

        assertThat(seen).hasSize(10_000);
    }

    @Test
    @DisplayName("時間前綴讓字典序等於時間序，分頁與除錯才好用")
    void isTimeOrdered() throws InterruptedException {
        String earlier = IdGenerator.ulid();
        Thread.sleep(5);
        String later = IdGenerator.ulid();

        assertThat(earlier.compareTo(later)).isNegative();
    }
}
