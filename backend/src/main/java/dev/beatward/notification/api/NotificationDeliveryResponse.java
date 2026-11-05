package dev.beatward.notification.api;

import dev.beatward.notification.NotificationDeliveryEntity;

import java.time.Instant;

public record NotificationDeliveryResponse(
        Long id,
        Long channelId,
        String channel,
        String channelName,
        String eventType,
        String status,
        int attemptCount,
        Instant lastAttemptAt,
        Instant sentAt,
        String lastErrorSummary) {

    public static NotificationDeliveryResponse from(NotificationDeliveryEntity delivery,
                                                    String channelName) {
        return new NotificationDeliveryResponse(
                delivery.getId(),
                delivery.getChannelId(),
                delivery.getChannel().name(),
                channelName,
                delivery.getEventType().name(),
                delivery.getStatus().name(),
                delivery.getAttemptCount(),
                delivery.getLastAttemptAt(),
                delivery.getSentAt(),
                delivery.getLastErrorSummary());
    }
}
