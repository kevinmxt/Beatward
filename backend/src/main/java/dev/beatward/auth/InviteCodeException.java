package dev.beatward.auth;

public class InviteCodeException extends RuntimeException {

    private final String code;

    private InviteCodeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static InviteCodeException invalid() {
        return new InviteCodeException("INVITE_INVALID", "invite code is invalid");
    }

    public static InviteCodeException expired() {
        return new InviteCodeException("INVITE_EXPIRED", "invite code has expired");
    }

    public static InviteCodeException revoked() {
        return new InviteCodeException("INVITE_REVOKED", "invite code has been revoked");
    }

    public static InviteCodeException exhausted() {
        return new InviteCodeException("INVITE_EXHAUSTED", "invite code has no remaining uses");
    }

    public String code() {
        return code;
    }
}
