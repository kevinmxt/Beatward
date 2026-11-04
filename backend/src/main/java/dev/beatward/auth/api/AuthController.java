package dev.beatward.auth.api;

import dev.beatward.auth.AccountStatus;
import dev.beatward.auth.AccountDisabledException;
import dev.beatward.auth.AdminSessionRegistry;
import dev.beatward.auth.AppUserEntity;
import dev.beatward.auth.AppUserRepository;
import dev.beatward.auth.AuthSessionManager;
import dev.beatward.auth.InvalidCredentialsException;
import dev.beatward.auth.InvalidCurrentPasswordException;
import dev.beatward.auth.InvalidNewPasswordException;
import dev.beatward.auth.LoginRateLimitedException;
import dev.beatward.auth.LoginRateLimiter;
import dev.beatward.auth.PasswordRules;
import dev.beatward.common.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String ROLE_PREFIX = "ROLE_";

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter rateLimiter;
    private final AdminSessionRegistry sessionRegistry;
    private final AuthSessionManager sessionManager;
    private final CsrfTokenRepository csrfTokenRepository;
    private final Clock clock;

    public AuthController(AuthenticationManager authenticationManager,
                          AppUserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          LoginRateLimiter rateLimiter,
                          AdminSessionRegistry sessionRegistry,
                          AuthSessionManager sessionManager,
                          CsrfTokenRepository csrfTokenRepository,
                          Clock clock) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.sessionRegistry = sessionRegistry;
        this.sessionManager = sessionManager;
        this.csrfTokenRepository = csrfTokenRepository;
        this.clock = clock;
    }

    @GetMapping("/session")
    public SessionResponse session(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            csrfToken = csrfTokenRepository.loadDeferredToken(request, response).get();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isAuthenticated(authentication)) {
            return new SessionResponse(false, null, null, csrfToken.getToken());
        }
        return new SessionResponse(true, authentication.getName(),
                roleOf(authentication), csrfToken.getToken());
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody(required = false) LoginRequest body,
                               HttpServletRequest request, HttpServletResponse response) {
        String username = body == null ? null : body.username();
        String password = body == null ? null : body.password();
        String key = LoginRateLimiter.keyFor(username, request.getRemoteAddr());
        LoginRateLimiter.Attempt attempt;
        try {
            attempt = rateLimiter.beginAttempt(key);
        } catch (LoginRateLimitedException ex) {
            if (ex.isCapacityExhausted()) {
                log.warn("Admin login rate limiter capacity exhausted, remoteAddress={}, "
                                + "trackedKeys={}, traceId={}",
                        request.getRemoteAddr(), rateLimiter.trackedKeys(),
                        TraceIdFilter.currentTraceId(request));
            } else {
                log.warn("Admin login rate limited, remoteAddress={}, traceId={}",
                        request.getRemoteAddr(), TraceIdFilter.currentTraceId(request));
            }
            throw ex;
        }

        boolean authenticated = false;
        try {
            if (username == null || password == null) {
                logLoginFailure(request);
                throw new InvalidCredentialsException();
            }

            Authentication authentication;
            try {
                authentication = authenticationManager.authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated(username, password));
            } catch (AuthenticationException ex) {
                logLoginFailure(request);
                throw new InvalidCredentialsException();
            }

            AppUserEntity user = userRepository.findByUsernameInput(authentication.getName())
                    .orElseThrow(InvalidCredentialsException::new);
            if (user.getStatus() == AccountStatus.DISABLED) {
                log.warn("Login rejected for disabled account, remoteAddress={}, traceId={}",
                        request.getRemoteAddr(), TraceIdFilter.currentTraceId(request));
                throw new AccountDisabledException();
            }

            rateLimiter.recordSuccess(attempt);
            authenticated = true;
            recordLogin(user.getUsername());
            sessionManager.establish(authentication, request, response);
            log.info("Login succeeded, remoteAddress={}, traceId={}",
                    request.getRemoteAddr(), TraceIdFilter.currentTraceId(request));
            return new LoginResponse(authentication.getName(), roleOf(authentication));
        } finally {
            if (!authenticated) {
                rateLimiter.recordFailure(attempt);
            }
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            sessionRegistry.unregister(session.getId());
            try {
                session.invalidate();
            } catch (IllegalStateException ignored) {
                // Already expired by the container.
            }
        }
        SecurityContextHolder.clearContext();
        log.info("Admin logout, remoteAddress={}, traceId={}",
                request.getRemoteAddr(), TraceIdFilter.currentTraceId(request));
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestBody(required = false) PasswordChangeRequest body,
                               HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isAuthenticated(authentication)) {
            throw new InvalidCredentialsException();
        }

        AppUserEntity user = userRepository.findByUsernameInput(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("authenticated user no longer exists"));
        String currentPassword = body == null ? null : body.currentPassword();
        String newPassword = body == null ? null : body.newPassword();

        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            log.warn("Admin password change rejected, remoteAddress={}, traceId={}",
                    request.getRemoteAddr(), TraceIdFilter.currentTraceId(request));
            throw new InvalidCurrentPasswordException();
        }
        if (!PasswordRules.isValid(newPassword)) {
            throw new InvalidNewPasswordException("new password must be 12-128 characters");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new InvalidNewPasswordException("new password must differ from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(clock.instant());
        userRepository.save(user);
        sessionRegistry.invalidateAll(user.getUsername());
        SecurityContextHolder.clearContext();
        log.info("Admin password changed, remoteAddress={}, traceId={}",
                request.getRemoteAddr(), TraceIdFilter.currentTraceId(request));
    }

    private void recordLogin(String username) {
        userRepository.updateLastLoginAt(username, clock.instant());
    }

    private static void logLoginFailure(HttpServletRequest request) {
        log.warn("Login failed, remoteAddress={}, traceId={}",
                request.getRemoteAddr(), TraceIdFilter.currentTraceId(request));
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private static String roleOf(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .findFirst()
                .orElse(null);
    }
}
