package dev.beatward.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class RegistrationService {

    private final AppUserRepository userRepository;
    private final InviteCodeService inviteCodeService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public RegistrationService(AppUserRepository userRepository,
                               InviteCodeService inviteCodeService,
                               PasswordEncoder passwordEncoder,
                               Clock clock) {
        this.userRepository = userRepository;
        this.inviteCodeService = inviteCodeService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public AppUserEntity register(String rawInviteCode, String rawUsername, String rawPassword) {
        String username = normalizedUsername(rawUsername);
        if (!PasswordRules.isValid(rawPassword)) {
            throw new AuthValidationException("password must be 12-128 characters");
        }

        Instant now = clock.instant();
        InviteCodeEntity invite = inviteCodeService.requireUsable(rawInviteCode, now);
        if (userRepository.existsByUsername(username)) {
            throw new UsernameTakenException();
        }
        if (!inviteCodeService.consume(invite, now)) {
            // Re-read so a concurrent loser reports why the invite stopped being usable.
            inviteCodeService.requireUsable(rawInviteCode, now);
            throw InviteCodeException.exhausted();
        }

        AppUserEntity user = new AppUserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(AppRole.USER);
        user.setStatus(AccountStatus.ACTIVE);
        user.setLastLoginAt(now);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new UsernameTakenException();
        }
    }

    private static String normalizedUsername(String rawUsername) {
        try {
            return UsernameRules.normalize(rawUsername);
        } catch (IllegalArgumentException ex) {
            throw new AuthValidationException(ex.getMessage());
        }
    }
}
