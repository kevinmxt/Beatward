package dev.beatward.auth;

public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException() {
        super("authentication is required");
    }
}
