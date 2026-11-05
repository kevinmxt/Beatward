package dev.beatward.auth;

public class LastActiveAdminException extends RuntimeException {

    public LastActiveAdminException() {
        super("at least one active administrator must remain");
    }
}
