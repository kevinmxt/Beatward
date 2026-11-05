package dev.beatward.notification;

public class ChannelLimitExceededException extends RuntimeException {

    public ChannelLimitExceededException() {
        super("at most 3 notification channels may be enabled");
    }
}
