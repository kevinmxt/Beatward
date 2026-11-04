package dev.beatward.auth;

public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException() {
        super("account is disabled");
    }
}
