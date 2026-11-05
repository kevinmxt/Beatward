package dev.beatward.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("beatward.smtp")
public record SmtpProperties(
        String host,
        Integer port,
        String username,
        String password,
        String from,
        Boolean starttls,
        Boolean ssl,
        Integer connectTimeoutMs,
        Integer readTimeoutMs,
        Integer writeTimeoutMs) {

    public SmtpProperties {
        host = normalize(host);
        username = normalize(username);
        password = password == null || password.isBlank() ? null : password;
        from = normalize(from);
        if (port == null) {
            port = 587;
        }
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("beatward.smtp.port must be between 1 and 65535");
        }
        starttls = starttls == null || starttls;
        ssl = ssl != null && ssl;
        if (connectTimeoutMs == null) {
            connectTimeoutMs = 5000;
        }
        if (readTimeoutMs == null) {
            readTimeoutMs = 10000;
        }
        if (writeTimeoutMs == null) {
            writeTimeoutMs = 10000;
        }
    }

    public boolean isConfigured() {
        return host != null && from != null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
