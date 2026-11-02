package dev.beatward.heartbeat;

public class HttpsRequiredException extends RuntimeException {

    public HttpsRequiredException() {
        super("HTTPS is required");
    }
}
