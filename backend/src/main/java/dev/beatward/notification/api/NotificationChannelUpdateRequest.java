package dev.beatward.notification.api;

import java.util.Map;

public record NotificationChannelUpdateRequest(String name, Boolean enabled,
                                               Map<String, String> config) {
}
