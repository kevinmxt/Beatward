package dev.beatward.notification;

import dev.beatward.auth.AuthValidationException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ChannelConfigValidator {

    private static final Set<NotificationChannel> SUPPORTED = Set.of(
            NotificationChannel.PUSHPLUS, NotificationChannel.XIZHI, NotificationChannel.EMAIL);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_CREDENTIAL_LENGTH = 255;
    private static final int MAX_EMAIL_LENGTH = 254;

    public NotificationChannel parseType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            throw new AuthValidationException("type is required");
        }
        NotificationChannel type;
        try {
            type = NotificationChannel.valueOf(rawType.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new AuthValidationException("type must be PUSHPLUS, XIZHI or EMAIL");
        }
        if (!SUPPORTED.contains(type)) {
            throw new AuthValidationException("type must be PUSHPLUS, XIZHI or EMAIL");
        }
        return type;
    }

    public Map<String, String> normalize(NotificationChannel type, Map<String, String> config) {
        if (config == null) {
            throw new AuthValidationException("config is required");
        }
        return switch (type) {
            case PUSHPLUS -> Map.of("token", credential(config.get("token"), "token",
                    MAX_CREDENTIAL_LENGTH));
            case XIZHI -> Map.of("key", credential(config.get("key"), "key",
                    MAX_CREDENTIAL_LENGTH));
            case EMAIL -> Map.of("email", email(config.get("email")));
            default -> throw new AuthValidationException("unsupported channel type");
        };
    }

    public String mask(NotificationChannel type, Map<String, String> config) {
        String value = config.get(credentialField(type));
        if (value == null || value.isBlank()) {
            return "****";
        }
        if (type == NotificationChannel.EMAIL) {
            int at = value.indexOf('@');
            return at > 0
                    ? value.charAt(0) + "***" + value.substring(at)
                    : "****";
        }
        return value.length() <= 4 ? "****" : "****" + value.substring(value.length() - 4);
    }

    public String credentialField(NotificationChannel type) {
        return switch (type) {
            case PUSHPLUS -> "token";
            case XIZHI -> "key";
            case EMAIL -> "email";
            default -> throw new AuthValidationException("unsupported channel type");
        };
    }

    private static String credential(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new AuthValidationException(field + " is required");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new AuthValidationException(field + " is too long");
        }
        return normalized;
    }

    private static String email(String value) {
        String normalized = credential(value, "email", MAX_EMAIL_LENGTH);
        if (!EMAIL.matcher(normalized).matches()) {
            throw new AuthValidationException("email is invalid");
        }
        return normalized;
    }
}
