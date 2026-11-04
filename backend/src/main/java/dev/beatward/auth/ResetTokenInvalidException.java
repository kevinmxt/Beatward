package dev.beatward.auth;

public class ResetTokenInvalidException extends RuntimeException {

    public ResetTokenInvalidException() {
        super("reset code is invalid, expired, or already used");
    }
}
