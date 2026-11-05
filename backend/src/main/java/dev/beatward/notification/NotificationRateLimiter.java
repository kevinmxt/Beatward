package dev.beatward.notification;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationRateLimiter {

    static final int PER_MINUTE = 10;
    static final int PER_HOUR = 50;
    private static final int MAX_KEYS = 10_000;

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public NotificationRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Returns the number of seconds to wait when the key is over quota, or empty when
     * the send is allowed and recorded.
     */
    public Optional<Long> tryAcquire(Long userId, String channelKey) {
        if (windows.size() > MAX_KEYS) {
            windows.entrySet().removeIf(entry -> entry.getValue().isEmpty(clock.instant()));
        }
        Window window = windows.computeIfAbsent(userId + ":" + channelKey,
                ignored -> new Window());
        return window.tryAcquire(clock.instant());
    }

    static final class Window {

        private final Deque<Instant> timestamps = new ArrayDeque<>();

        synchronized Optional<Long> tryAcquire(Instant now) {
            Instant hourAgo = now.minusSeconds(3600);
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(hourAgo)) {
                timestamps.pollFirst();
            }
            Instant minuteAgo = now.minusSeconds(60);
            long inMinute = timestamps.stream()
                    .filter(timestamp -> !timestamp.isBefore(minuteAgo))
                    .count();
            if (inMinute >= PER_MINUTE) {
                Instant oldest = timestamps.stream()
                        .filter(timestamp -> !timestamp.isBefore(minuteAgo))
                        .findFirst()
                        .orElse(now);
                return Optional.of(secondsUntil(now, oldest, 60));
            }
            if (timestamps.size() >= PER_HOUR) {
                return Optional.of(secondsUntil(now, timestamps.peekFirst(), 3600));
            }
            timestamps.addLast(now);
            return Optional.empty();
        }

        synchronized boolean isEmpty(Instant now) {
            Instant hourAgo = now.minusSeconds(3600);
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(hourAgo)) {
                timestamps.pollFirst();
            }
            return timestamps.isEmpty();
        }

        private static long secondsUntil(Instant now, Instant oldest, long windowSeconds) {
            return Math.max(1, oldest.plusSeconds(windowSeconds).getEpochSecond()
                    - now.getEpochSecond());
        }
    }
}
