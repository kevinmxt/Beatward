package dev.beatward.heartbeat;

public class NotHeartbeatMonitorException extends RuntimeException {

    public NotHeartbeatMonitorException() {
        super("monitor is not a heartbeat monitor");
    }
}
