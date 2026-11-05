package dev.beatward.notification;

public class NotificationRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public NotificationRateLimitedException(long retryAfterSeconds) {
        super("notification rate limit exceeded");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
