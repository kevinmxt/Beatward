package dev.beatward.auth.api;

import dev.beatward.auth.InviteCodeService;

import java.time.Instant;

public record InviteCodeCreateResponse(Long id, String code, String label, int maxUses,
                                       Instant expiresAt) {

    public static InviteCodeCreateResponse from(InviteCodeService.CreatedInviteCode created) {
        return new InviteCodeCreateResponse(created.invite().getId(), created.plaintextCode(),
                created.invite().getLabel(), created.invite().getMaxUses(),
                created.invite().getExpiresAt());
    }
}
