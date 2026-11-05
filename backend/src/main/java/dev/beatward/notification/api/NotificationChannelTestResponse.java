package dev.beatward.notification.api;

import dev.beatward.notification.ChannelTestStatus;

import java.time.Instant;

public record NotificationChannelTestResponse(ChannelTestStatus status, String message,
                                              Instant testedAt) {
}
