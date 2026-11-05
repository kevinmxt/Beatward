package dev.beatward.notification;

import java.util.Map;

public interface UserChannelSender {

    NotificationChannel type();

    void send(Map<String, String> credentials, String title, String content);

    default String sanitize(String message) {
        return message;
    }
}
