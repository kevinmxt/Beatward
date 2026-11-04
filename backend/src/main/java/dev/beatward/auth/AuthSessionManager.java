package dev.beatward.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class AuthSessionManager {

    private final AdminSessionRegistry sessionRegistry;
    private final SecurityContextRepository securityContextRepository;
    private final Clock clock;

    public AuthSessionManager(AdminSessionRegistry sessionRegistry,
                              SecurityContextRepository securityContextRepository,
                              Clock clock) {
        this.sessionRegistry = sessionRegistry;
        this.securityContextRepository = securityContextRepository;
        this.clock = clock;
    }

    public void establish(Authentication authentication, HttpServletRequest request,
                          HttpServletResponse response) {
        rotateSession(request);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        HttpSession session = request.getSession(true);
        session.setAttribute(AbsoluteSessionTimeoutFilter.SESSION_CREATED_AT_ATTRIBUTE,
                clock.instant());
        sessionRegistry.register(authentication.getName(), session);
        securityContextRepository.saveContext(context, request, response);
    }

    private void rotateSession(HttpServletRequest request) {
        HttpSession existing = request.getSession(false);
        if (existing == null) {
            return;
        }
        sessionRegistry.unregister(existing.getId());
        try {
            existing.invalidate();
        } catch (IllegalStateException ignored) {
            // Already expired by the container.
        }
    }
}
