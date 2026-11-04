package dev.beatward.auth.api;

public record ResetPasswordRequest(String resetCode, String newPassword) {
}
