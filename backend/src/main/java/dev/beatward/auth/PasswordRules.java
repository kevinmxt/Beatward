package dev.beatward.auth;

public final class PasswordRules {

    static final int MIN_LENGTH = 10;
    static final int MAX_LENGTH = 32;

    private PasswordRules() {
    }

    public static boolean isValid(String password) {
        return password != null
                && password.length() >= MIN_LENGTH
                && password.length() <= MAX_LENGTH;
    }
}
