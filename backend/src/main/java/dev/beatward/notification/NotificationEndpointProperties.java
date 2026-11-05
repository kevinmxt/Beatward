package dev.beatward.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("beatward.notification.endpoints")
public record NotificationEndpointProperties(String pushplusApiBaseUrl, String xizhiApiBaseUrl) {

    private static final String DEFAULT_PUSHPLUS_BASE_URL = "https://www.pushplus.plus";
    private static final String DEFAULT_XIZHI_BASE_URL = "https://xizhi.qqoq.net";

    public NotificationEndpointProperties {
        pushplusApiBaseUrl = normalize(pushplusApiBaseUrl, DEFAULT_PUSHPLUS_BASE_URL);
        xizhiApiBaseUrl = normalize(xizhiApiBaseUrl, DEFAULT_XIZHI_BASE_URL);
    }

    private static String normalize(String value, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
