package dev.beatward.notification.api;

import dev.beatward.notification.ChannelTestStatus;
import dev.beatward.notification.NotificationChannel;
import dev.beatward.notification.NotificationChannelEntity;

import java.time.Instant;

public record NotificationChannelResponse(
        Long id,
        NotificationChannel type,
        String name,
        boolean enabled,
        String maskedConfig,
        Instant lastTestAt,
        ChannelTestStatus lastTestStatus,
        String lastTestError,
        Instant createdAt,
        Instant updatedAt) {

    public static NotificationChannelResponse from(NotificationChannelEntity channel,
                                                   String maskedConfig) {
        return new NotificationChannelResponse(
                channel.getId(),
                channel.getType(),
                channel.getName(),
                channel.isEnabled(),
                maskedConfig,
                channel.getLastTestAt(),
                channel.getLastTestStatus(),
                channel.getLastTestError(),
                channel.getCreatedAt(),
                channel.getUpdatedAt());
    }
}
