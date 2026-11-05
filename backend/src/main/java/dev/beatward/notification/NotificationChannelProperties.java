package dev.beatward.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Base64;

@ConfigurationProperties("beatward")
public record NotificationChannelProperties(String channelSecretKey, String publicBaseUrl) {

    private static final int KEY_BYTES = 32;

    public NotificationChannelProperties {
        channelSecretKey = normalize(channelSecretKey);
        publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
        if (channelSecretKey != null && decodeKey(channelSecretKey) == null) {
            throw new IllegalStateException(
                    "BEATWARD_CHANNEL_SECRET_KEY must decode from Base64 to 32 bytes");
        }
    }

    public byte[] secretKeyBytes() {
        return channelSecretKey == null ? null : decodeKey(channelSecretKey);
    }

    private static byte[] decodeKey(String value) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        return decoded.length == KEY_BYTES ? decoded : null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
