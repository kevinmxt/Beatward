package dev.beatward.notification.api;

import dev.beatward.notification.NotificationDeliveryEntity;

import java.time.Instant;

public record NotificationDeliveryResponse(
        Long id,
        String channel,
        String eventType,
        String status,
        int attemptCount,
        Instant lastAttemptAt,
        Instant sentAt,
        String lastErrorSummary) {

    public static NotificationDeliveryResponse from(NotificationDeliveryEntity delivery) {
        return new NotificationDeliveryResponse(
                delivery.getId(),
                delivery.getChannel().name(),
                delivery.getEventType().name(),
                delivery.getStatus().name(),
                delivery.getAttemptCount(),
                delivery.getLastAttemptAt(),
                delivery.getSentAt(),
                delivery.getLastErrorSummary());
    }
}
