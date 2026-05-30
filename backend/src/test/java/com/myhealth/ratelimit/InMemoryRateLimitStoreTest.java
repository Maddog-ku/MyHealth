package com.myhealth.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class InMemoryRateLimitStoreTest {

    private final MutableClock clock = new MutableClock();
    private final InMemoryRateLimitStore store = new InMemoryRateLimitStore(clock);

    @Test
    void check_removesExpiredWindowsOpportunistically() {
        for (int i = 0; i < 255; i++) {
            store.check("key-%d".formatted(i), 1, Duration.ofMinutes(1));
        }
        assertThat(store.windowCount()).isEqualTo(255);

        clock.advance(Duration.ofMinutes(2));
        store.check("new-key", 1, Duration.ofMinutes(1));

        assertThat(store.windowCount()).isEqualTo(1);
    }

    private static class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-05-30T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
