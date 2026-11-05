package dev.beatward.notification;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelConfigValidatorTest {

    private final ChannelConfigValidator validator = new ChannelConfigValidator();

    @Test
    void normalizesPushPlusAndXizhiCredentials() {
        assertThat(validator.normalize(NotificationChannel.PUSHPLUS, Map.of("token", "  abc123  ")))
                .containsEntry("token", "abc123");
        assertThat(validator.normalize(NotificationChannel.XIZHI, Map.of("key", "key-1")))
                .containsEntry("key", "key-1");
    }

    @Test
    void normalizesEmailAddress() {
        assertThat(validator.normalize(NotificationChannel.EMAIL, Map.of("email", " a@example.com ")))
                .containsEntry("email", "a@example.com");
    }

    @Test
    void rejectsUnsupportedChannelTypes() {
        assertThatThrownBy(() -> validator.parseType("TELEGRAM"))
                .isInstanceOf(dev.beatward.auth.AuthValidationException.class);
        assertThatThrownBy(() -> validator.parseType("DINGTALK_WEBHOOK"))
                .isInstanceOf(dev.beatward.auth.AuthValidationException.class);
    }

    @Test
    void rejectsMissingOrOversizedCredentials() {
        assertThatThrownBy(() -> validator.normalize(NotificationChannel.PUSHPLUS, Map.of()))
                .isInstanceOf(dev.beatward.auth.AuthValidationException.class)
                .hasMessageContaining("token");
        assertThatThrownBy(() -> validator.normalize(
                NotificationChannel.XIZHI, Map.of("key", "x".repeat(256))))
                .isInstanceOf(dev.beatward.auth.AuthValidationException.class);
    }

    @Test
    void rejectsInvalidEmail() {
        assertThatThrownBy(() -> validator.normalize(
                NotificationChannel.EMAIL, Map.of("email", "not-an-email")))
                .isInstanceOf(dev.beatward.auth.AuthValidationException.class)
                .hasMessageContaining("email");
    }

    @Test
    void masksCredentialsWithoutLeakingThem() {
        assertThat(validator.mask(NotificationChannel.PUSHPLUS, Map.of("token", "abcdefgh1234")))
                .isEqualTo("****1234");
        assertThat(validator.mask(NotificationChannel.EMAIL, Map.of("email", "alice@example.com")))
                .isEqualTo("a***@example.com");
        assertThat(validator.mask(NotificationChannel.XIZHI, Map.of("key", "x")))
                .isEqualTo("****");
    }
}
