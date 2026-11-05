package dev.beatward.auth.api;

import dev.beatward.auth.AccountStatus;
import dev.beatward.auth.AppRole;
import dev.beatward.auth.AppUserEntity;

import java.time.Instant;

public record AdminUserResponse(Long id, String username, AppRole role, AccountStatus status,
                                Instant disabledAt, Instant lastLoginAt, Instant createdAt,
                                long monitorCount) {

    public static AdminUserResponse from(AppUserEntity user, long monitorCount) {
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getRole(),
                user.getStatus(), user.getDisabledAt(), user.getLastLoginAt(), user.getCreatedAt(),
                monitorCount);
    }
}
