package dev.beatward.notification;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelSecretCipherTest {

    private final byte[] key = randomKey();
    private final ChannelSecretCipher cipher = new ChannelSecretCipher(
            new NotificationChannelProperties(Base64.getEncoder().encodeToString(key), null));

    @Test
    void encryptsAndDecryptsWithRandomIv() {
        String first = cipher.encrypt("pushplus-token-1234");
        String second = cipher.encrypt("pushplus-token-1234");

        assertThat(first).isNotEqualTo(second);
        assertThat(first).doesNotContain("pushplus-token-1234");
        assertThat(cipher.decrypt(first)).isEqualTo("pushplus-token-1234");
        assertThat(cipher.decrypt(second)).isEqualTo("pushplus-token-1234");
    }

    @Test
    void rejectsTamperedCiphertext() {
        String encrypted = cipher.encrypt("secret");
        byte[] bytes = Base64.getDecoder().decode(encrypted);
        bytes[bytes.length - 1] ^= 0x01;

        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(bytes)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reportsWhetherTheKeyIsConfigured() {
        assertThat(cipher.isConfigured()).isTrue();
        assertThat(new ChannelSecretCipher(new NotificationChannelProperties(null, null))
                .isConfigured()).isFalse();
    }

    @Test
    void rejectsMissingKeyWhenEncrypting() {
        ChannelSecretCipher unconfigured =
                new ChannelSecretCipher(new NotificationChannelProperties(null, null));

        assertThatThrownBy(() -> unconfigured.encrypt("secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BEATWARD_CHANNEL_SECRET_KEY");
    }

    @Test
    void rejectsKeysThatAreNot32Bytes() {
        assertThatThrownBy(() -> new NotificationChannelProperties(
                Base64.getEncoder().encodeToString(new byte[16]), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new NotificationChannelProperties("not-base64!", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    private static byte[] randomKey() {
        byte[] value = new byte[32];
        new SecureRandom().nextBytes(value);
        return value;
    }
}
