package dev.beatward.notification;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-05T00:00:00Z"));
    private final NotificationRateLimiter limiter = new NotificationRateLimiter(clock);

    @Test
    void allowsTenSendsPerMinute() {
        for (int i = 0; i < NotificationRateLimiter.PER_MINUTE; i++) {
            assertThat(limiter.tryAcquire(1L, "channel-1")).isEmpty();
        }

        assertThat(limiter.tryAcquire(1L, "channel-1")).contains(60L);
        clock.advance(Duration.ofSeconds(61));
        assertThat(limiter.tryAcquire(1L, "channel-1")).isEmpty();
    }

    @Test
    void allowsFiftySendsPerHour() {
        for (int minute = 0; minute < 5; minute++) {
            for (int i = 0; i < NotificationRateLimiter.PER_MINUTE; i++) {
                assertThat(limiter.tryAcquire(1L, "channel-1")).isEmpty();
            }
            clock.advance(Duration.ofSeconds(61));
        }

        assertThat(limiter.tryAcquire(1L, "channel-1")).isPresent();
    }

    @Test
    void limitsEachChannelKeyIndependently() {
        for (int i = 0; i < NotificationRateLimiter.PER_MINUTE; i++) {
            limiter.tryAcquire(1L, "channel-1");
        }

        assertThat(limiter.tryAcquire(1L, "channel-1")).isPresent();
        assertThat(limiter.tryAcquire(1L, "channel-2")).isEmpty();
        assertThat(limiter.tryAcquire(2L, "channel-1")).isEmpty();
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
