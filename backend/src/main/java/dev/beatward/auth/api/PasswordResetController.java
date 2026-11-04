package dev.beatward.auth.api;

import dev.beatward.auth.AdminSessionRegistry;
import dev.beatward.auth.AuthAttemptTracker;
import dev.beatward.auth.AuthValidationException;
import dev.beatward.auth.PasswordResetService;
import dev.beatward.auth.SecureTokenGenerator;
import dev.beatward.common.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class PasswordResetController {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetController.class);

    private final PasswordResetService resetService;
    private final AdminSessionRegistry sessionRegistry;
    private final AuthAttemptTracker attemptTracker;
    private final SecureTokenGenerator tokenGenerator;

    public PasswordResetController(PasswordResetService resetService,
                                   AdminSessionRegistry sessionRegistry,
                                   AuthAttemptTracker attemptTracker,
                                   SecureTokenGenerator tokenGenerator) {
        this.resetService = resetService;
        this.sessionRegistry = sessionRegistry;
        this.attemptTracker = attemptTracker;
        this.tokenGenerator = tokenGenerator;
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@RequestBody(required = false) ResetPasswordRequest body,
                              HttpServletRequest request) {
        ResetPasswordRequest input = body == null ? new ResetPasswordRequest(null, null) : body;
        AuthAttemptTracker.Attempts attempts = attemptTracker.begin(
                AuthAttemptTracker.addressKey("reset", request.getRemoteAddr()),
                AuthAttemptTracker.secretKey("reset",
                        tokenGenerator.digest(input.resetCode() == null ? "" : input.resetCode())));
        try {
            if (input.resetCode() == null || input.resetCode().isBlank()
                    || input.newPassword() == null) {
                throw new AuthValidationException("resetCode and newPassword are required");
            }
            PasswordResetService.ResetResult result =
                    resetService.reset(input.resetCode(), input.newPassword());
            sessionRegistry.invalidateAll(result.username());
            attemptTracker.success(attempts);
            log.info("Password reset completed, userId={}, remoteAddress={}, traceId={}",
                    result.userId(), request.getRemoteAddr(), TraceIdFilter.currentTraceId(request));
        } catch (RuntimeException failure) {
            attemptTracker.failure(attempts);
            throw failure;
        }
    }
}
