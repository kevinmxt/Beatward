package dev.beatward.auth;

public class InvalidNewPasswordException extends RuntimeException {

    public InvalidNewPasswordException(String message) {
        super(message);
    }
}
