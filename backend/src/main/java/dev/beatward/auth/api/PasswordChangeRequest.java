package dev.beatward.auth.api;

public record PasswordChangeRequest(String currentPassword, String newPassword) {
}
