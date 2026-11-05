package dev.beatward.auth;

public class InviteCodeNotFoundException extends RuntimeException {

    public InviteCodeNotFoundException(Long id) {
        super("invite code " + id + " was not found");
    }
}
