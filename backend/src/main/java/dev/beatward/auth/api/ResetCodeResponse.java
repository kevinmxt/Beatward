package dev.beatward.auth.api;

import java.time.Instant;

public record ResetCodeResponse(String code, Instant expiresAt) {
}
