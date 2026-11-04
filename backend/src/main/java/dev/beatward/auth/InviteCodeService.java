package dev.beatward.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class InviteCodeService {

    static final int DEFAULT_MAX_USES = 1;
    static final int MAX_USES_LIMIT = 100;
    static final int DEFAULT_EXPIRES_IN_HOURS = 7 * 24;
    static final int MAX_EXPIRES_IN_HOURS = 30 * 24;

    private final InviteCodeRepository repository;
    private final SecureTokenGenerator tokenGenerator;
    private final Clock clock;

    public InviteCodeService(InviteCodeRepository repository,
                             SecureTokenGenerator tokenGenerator,
                             Clock clock) {
        this.repository = repository;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
    }

    @Transactional
    public CreatedInviteCode create(Long createdBy, String rawLabel, Integer maxUses,
                                    Integer expiresInHours) {
        int uses = maxUses == null ? DEFAULT_MAX_USES : maxUses;
        if (uses < 1 || uses > MAX_USES_LIMIT) {
            throw new AuthValidationException("maxUses must be between 1 and " + MAX_USES_LIMIT);
        }
        int hours = expiresInHours == null ? DEFAULT_EXPIRES_IN_HOURS : expiresInHours;
        if (hours < 1 || hours > MAX_EXPIRES_IN_HOURS) {
            throw new AuthValidationException(
                    "expiresInHours must be between 1 and " + MAX_EXPIRES_IN_HOURS);
        }
        String label = rawLabel == null || rawLabel.isBlank() ? null : rawLabel.strip();
        if (label != null && label.length() > 200) {
            throw new AuthValidationException("label must be at most 200 characters");
        }

        Instant now = clock.instant();
        String code = tokenGenerator.generate();
        InviteCodeEntity invite = new InviteCodeEntity();
        invite.setCodeHash(tokenGenerator.digest(code));
        invite.setLabel(label);
        invite.setMaxUses(uses);
        invite.setUsedCount(0);
        invite.setExpiresAt(now.plus(Duration.ofHours(hours)));
        invite.setCreatedBy(createdBy);
        invite.setCreatedAt(now);
        return new CreatedInviteCode(repository.save(invite), code);
    }

    public InviteCodeEntity requireUsable(String rawCode, Instant now) {
        String code = rawCode == null ? "" : rawCode.strip();
        InviteCodeEntity invite = repository.findByCodeHash(tokenGenerator.digest(code))
                .orElseThrow(InviteCodeException::invalid);
        if (invite.getRevokedAt() != null) {
            throw InviteCodeException.revoked();
        }
        if (!now.isBefore(invite.getExpiresAt())) {
            throw InviteCodeException.expired();
        }
        if (invite.getUsedCount() >= invite.getMaxUses()) {
            throw InviteCodeException.exhausted();
        }
        return invite;
    }

    public boolean consume(InviteCodeEntity invite, Instant now) {
        return repository.consumeOneUse(invite.getId(), now) == 1;
    }

    public record CreatedInviteCode(InviteCodeEntity invite, String plaintextCode) {
    }
}
