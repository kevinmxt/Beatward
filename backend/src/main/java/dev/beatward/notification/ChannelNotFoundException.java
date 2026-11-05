package dev.beatward.notification;

public class ChannelNotFoundException extends RuntimeException {

    public ChannelNotFoundException(Long id) {
        super("notification channel " + id + " was not found");
    }
}
