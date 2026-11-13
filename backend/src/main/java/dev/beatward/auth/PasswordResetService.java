package dev.beatward.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class PasswordResetService {

    static final Duration TOKEN_LIFETIME = Duration.ofHours(24);

    private final PasswordResetTokenRepository tokenRepository;
    private final AppUserRepository userRepository;
    private final SecureTokenGenerator tokenGenerator;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                AppUserRepository userRepository,
                                SecureTokenGenerator tokenGenerator,
                                PasswordEncoder passwordEncoder,
                                Clock clock) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.tokenGenerator = tokenGenerator;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public CreatedResetCode create(Long userId, Long createdBy) {
        userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        Instant now = clock.instant();
        String code = tokenGenerator.generate();
        PasswordResetTokenEntity token = new PasswordResetTokenEntity();
        token.setTokenHash(tokenGenerator.digest(code));
        token.setUserId(userId);
        token.setExpiresAt(now.plus(TOKEN_LIFETIME));
        token.setCreatedBy(createdBy);
        token.setCreatedAt(now);
        tokenRepository.save(token);
        return new CreatedResetCode(code, token.getExpiresAt());
    }

    @Transactional
    public ResetResult reset(String rawCode, String rawPassword) {
        if (!PasswordRules.isValid(rawPassword)) {
            throw new AuthValidationException("password must be 10-32 characters");
        }
        String code = rawCode == null ? "" : rawCode.strip();
        Instant now = clock.instant();
        PasswordResetTokenEntity token = tokenRepository
                .findByTokenHash(tokenGenerator.digest(code))
                .orElseThrow(ResetTokenInvalidException::new);
        if (token.getUsedAt() != null || !now.isBefore(token.getExpiresAt())) {
            throw new ResetTokenInvalidException();
        }

        AppUserEntity user = userRepository.findById(token.getUserId())
                .orElseThrow(ResetTokenInvalidException::new);
        if (passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new AuthValidationException(
                    "new password must differ from the current password");
        }
        if (tokenRepository.markUsed(token.getId(), now) != 1) {
            throw new ResetTokenInvalidException();
        }
        userRepository.updatePassword(user.getId(), passwordEncoder.encode(rawPassword), now);
        return new ResetResult(user.getId(), user.getUsername());
    }

    public record CreatedResetCode(String plaintextCode, Instant expiresAt) {
    }

    public record ResetResult(Long userId, String username) {
    }
}
