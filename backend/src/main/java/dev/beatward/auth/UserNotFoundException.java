package dev.beatward.auth;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Long id) {
        super("user " + id + " was not found");
    }
}
