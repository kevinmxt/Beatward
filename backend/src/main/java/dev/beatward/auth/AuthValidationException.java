package dev.beatward.auth;

public class AuthValidationException extends RuntimeException {

    public AuthValidationException(String message) {
        super(message);
    }
}
