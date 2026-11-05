package dev.beatward.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChannelSecretStartupValidatorTest {

    private final NotificationChannelRepository repository =
            mock(NotificationChannelRepository.class);
    private final ChannelSecretCipher unconfiguredCipher = new ChannelSecretCipher(
            new NotificationChannelProperties(null, null));

    @Test
    void refusesToStartWhenChannelsExistWithoutAKey() {
        when(repository.count()).thenReturn(1L);
        ChannelSecretStartupValidator validator =
                new ChannelSecretStartupValidator(repository, unconfiguredCipher);

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BEATWARD_CHANNEL_SECRET_KEY");
    }

    @Test
    void startsWhenNoChannelsExistWithoutAKey() {
        when(repository.count()).thenReturn(0L);
        ChannelSecretStartupValidator validator =
                new ChannelSecretStartupValidator(repository, unconfiguredCipher);

        assertThatCode(validator::afterSingletonsInstantiated).doesNotThrowAnyException();
    }
}
