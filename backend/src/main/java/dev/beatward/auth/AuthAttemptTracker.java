package dev.beatward.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies the login rate-limit rules to registration and password-reset attempts.
 * Uses its own key space so attacker-chosen invite codes cannot exhaust the slots
 * that protect login. Every attempt key is released on both success and failure.
 */
@Component
public class AuthAttemptTracker {

    private final LoginRateLimiter rateLimiter;

    public AuthAttemptTracker(Clock clock,
                              @Value("${beatward.auth.attempt-rate-limit.max-keys:5000}")
                              int maxTrackedKeys) {
        this.rateLimiter = new LoginRateLimiter(clock, maxTrackedKeys);
    }

    public Attempts begin(String... keys) {
        List<LoginRateLimiter.Attempt> attempts = new ArrayList<>();
        try {
            for (String key : keys) {
                attempts.add(rateLimiter.beginAttempt(key));
            }
        } catch (RuntimeException failure) {
            attempts.forEach(rateLimiter::recordFailure);
            throw failure;
        }
        return new Attempts(attempts);
    }

    public void success(Attempts attempts) {
        attempts.attempts.forEach(rateLimiter::recordSuccess);
    }

    public void failure(Attempts attempts) {
        attempts.attempts.forEach(rateLimiter::recordFailure);
    }

    public static String addressKey(String purpose, String remoteAddress) {
        return purpose + "|addr|" + (remoteAddress == null ? "" : remoteAddress);
    }

    public static String secretKey(String purpose, String secret) {
        return purpose + "|secret|" + (secret == null ? "" : secret);
    }

    public static final class Attempts {

        private final List<LoginRateLimiter.Attempt> attempts;

        private Attempts(List<LoginRateLimiter.Attempt> attempts) {
            this.attempts = attempts;
        }
    }
}
