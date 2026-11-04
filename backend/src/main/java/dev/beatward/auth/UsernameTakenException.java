package dev.beatward.auth;

public class UsernameTakenException extends RuntimeException {

    public UsernameTakenException() {
        super("username is already taken");
    }
}
