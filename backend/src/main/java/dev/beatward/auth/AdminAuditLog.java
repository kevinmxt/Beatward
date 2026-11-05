package dev.beatward.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Structured audit trail for administrator actions. Never log invite codes,
 * reset codes or other credentials here.
 */
@Component
public class AdminAuditLog {

    private static final Logger log = LoggerFactory.getLogger("dev.beatward.audit");

    public void inviteCodeCreated(Long actorId, Long inviteId, int maxUses, Instant expiresAt,
                                  String traceId) {
        log.info("event=invite_code.created actorId={} inviteId={} maxUses={} expiresAt={} "
                + "result=success traceId={}", actorId, inviteId, maxUses, expiresAt, traceId);
    }

    public void inviteCodeRevoked(Long actorId, Long inviteId, String traceId) {
        log.info("event=invite_code.revoked actorId={} inviteId={} result=success traceId={}",
                actorId, inviteId, traceId);
    }

    public void userStatusChanged(Long actorId, Long userId, AccountStatus status,
                                  String traceId) {
        log.info("event=user.status_changed actorId={} userId={} status={} result=success "
                + "traceId={}", actorId, userId, status, traceId);
    }

    public void passwordResetCodeCreated(Long actorId, Long userId, Instant expiresAt,
                                        String traceId) {
        log.info("event=password_reset_code.created actorId={} userId={} expiresAt={} "
                + "result=success traceId={}", actorId, userId, expiresAt, traceId);
    }
}
