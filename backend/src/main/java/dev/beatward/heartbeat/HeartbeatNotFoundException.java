package dev.beatward.heartbeat;

public class HeartbeatNotFoundException extends RuntimeException {

    public HeartbeatNotFoundException() {
        super("heartbeat not found");
    }
}
