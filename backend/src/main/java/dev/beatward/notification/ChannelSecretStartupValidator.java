package dev.beatward.notification;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

@Component
public class ChannelSecretStartupValidator implements SmartInitializingSingleton {

    private final NotificationChannelRepository channelRepository;
    private final ChannelSecretCipher cipher;

    public ChannelSecretStartupValidator(NotificationChannelRepository channelRepository,
                                         ChannelSecretCipher cipher) {
        this.channelRepository = channelRepository;
        this.cipher = cipher;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (channelRepository.count() > 0 && !cipher.isConfigured()) {
            throw new IllegalStateException(
                    "BEATWARD_CHANNEL_SECRET_KEY is required when notification channels exist");
        }
    }
}
