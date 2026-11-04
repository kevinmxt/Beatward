package dev.beatward.auth.api;

public record InviteCodeCreateRequest(String label, Integer maxUses, Integer expiresInHours) {
}
