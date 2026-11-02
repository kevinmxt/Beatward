package dev.beatward.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("beatward.notification")
public record NotificationProperties(
        @DefaultValue("5s") Duration dispatchInterval,
        @DefaultValue("50") int batchSize,
        @DefaultValue("true") boolean schedulingEnabled) {

    static final Duration MIN_DISPATCH_INTERVAL = Duration.ofMillis(100);
    static final int MAX_BATCH_SIZE = 500;

    public NotificationProperties {
        if (dispatchInterval == null) {
            dispatchInterval = Duration.ofSeconds(5);
        }
        if (dispatchInterval.compareTo(MIN_DISPATCH_INTERVAL) < 0) {
            throw new IllegalStateException(
                    "beatward.notification.dispatch-interval must be at least 100ms");
        }
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalStateException(
                    "beatward.notification.batch-size must be between 1 and " + MAX_BATCH_SIZE);
        }
    }
}
