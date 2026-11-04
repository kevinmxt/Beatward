package dev.beatward.auth.api;

import dev.beatward.auth.AppUserEntity;
import dev.beatward.auth.AuthAttemptTracker;
import dev.beatward.auth.AuthSessionManager;
import dev.beatward.auth.AuthValidationException;
import dev.beatward.auth.RegistrationService;
import dev.beatward.auth.SecureTokenGenerator;
import dev.beatward.common.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class RegistrationController {

    private static final Logger log = LoggerFactory.getLogger(RegistrationController.class);

    private final RegistrationService registrationService;
    private final AuthenticationManager authenticationManager;
    private final AuthSessionManager sessionManager;
    private final AuthAttemptTracker attemptTracker;
    private final SecureTokenGenerator tokenGenerator;

    public RegistrationController(RegistrationService registrationService,
                                  AuthenticationManager authenticationManager,
                                  AuthSessionManager sessionManager,
                                  AuthAttemptTracker attemptTracker,
                                  SecureTokenGenerator tokenGenerator) {
        this.registrationService = registrationService;
        this.authenticationManager = authenticationManager;
        this.sessionManager = sessionManager;
        this.attemptTracker = attemptTracker;
        this.tokenGenerator = tokenGenerator;
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@RequestBody(required = false) RegisterRequest body,
                                                  HttpServletRequest request,
                                                  HttpServletResponse response) {
        RegisterRequest input = body == null ? new RegisterRequest(null, null, null) : body;
        AuthAttemptTracker.Attempts attempts = attemptTracker.begin(
                AuthAttemptTracker.addressKey("register", request.getRemoteAddr()),
                AuthAttemptTracker.secretKey("register",
                        tokenGenerator.digest(input.inviteCode() == null ? "" : input.inviteCode())));
        try {
            if (input.inviteCode() == null || input.inviteCode().isBlank()
                    || input.username() == null || input.password() == null) {
                throw new AuthValidationException("inviteCode, username and password are required");
            }
            AppUserEntity user = registrationService.register(
                    input.inviteCode(), input.username(), input.password());
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            user.getUsername(), input.password()));
            sessionManager.establish(authentication, request, response);
            attemptTracker.success(attempts);
            log.info("User registered, userId={}, remoteAddress={}, traceId={}",
                    user.getId(), request.getRemoteAddr(), TraceIdFilter.currentTraceId(request));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new LoginResponse(user.getUsername(), user.getRole().name()));
        } catch (RuntimeException failure) {
            attemptTracker.failure(attempts);
            throw failure;
        }
    }
}
