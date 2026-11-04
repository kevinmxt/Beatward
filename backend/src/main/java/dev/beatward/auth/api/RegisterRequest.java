package dev.beatward.auth.api;

public record RegisterRequest(String inviteCode, String username, String password) {
}
