package dev.beatward.notification.api;

import java.util.Map;

public record NotificationChannelCreateRequest(String type, String name, Boolean enabled,
                                               Map<String, String> config) {
}
