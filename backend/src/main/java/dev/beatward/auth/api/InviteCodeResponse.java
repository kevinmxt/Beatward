package dev.beatward.auth.api;

import dev.beatward.auth.InviteCodeEntity;

import java.time.Instant;

public record InviteCodeResponse(Long id, String label, int maxUses, int usedCount,
                                 Instant expiresAt, Instant revokedAt, Instant createdAt) {

    public static InviteCodeResponse from(InviteCodeEntity invite) {
        return new InviteCodeResponse(invite.getId(), invite.getLabel(), invite.getMaxUses(),
                invite.getUsedCount(), invite.getExpiresAt(), invite.getRevokedAt(),
                invite.getCreatedAt());
    }
}
