package dev.beatward.auth.api;

import dev.beatward.auth.AppUserRepository;
import dev.beatward.auth.InviteCodeService;
import dev.beatward.auth.PasswordResetService;
import dev.beatward.common.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminAccountController {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountController.class);

    private final InviteCodeService inviteCodeService;
    private final PasswordResetService passwordResetService;
    private final AppUserRepository userRepository;

    public AdminAccountController(InviteCodeService inviteCodeService,
                                  PasswordResetService passwordResetService,
                                  AppUserRepository userRepository) {
        this.inviteCodeService = inviteCodeService;
        this.passwordResetService = passwordResetService;
        this.userRepository = userRepository;
    }

    @PostMapping("/invite-codes")
    public ResponseEntity<InviteCodeCreateResponse> createInviteCode(
            @RequestBody(required = false) InviteCodeCreateRequest body,
            Authentication authentication, HttpServletRequest request) {
        InviteCodeCreateRequest input = body == null
                ? new InviteCodeCreateRequest(null, null, null)
                : body;
        Long adminId = currentUserId(authentication);
        InviteCodeService.CreatedInviteCode created = inviteCodeService.create(
                adminId, input.label(), input.maxUses(), input.expiresInHours());
        log.info("Invite code created, inviteId={}, createdBy={}, maxUses={}, expiresAt={}, "
                        + "traceId={}", created.invite().getId(), adminId,
                created.invite().getMaxUses(), created.invite().getExpiresAt(),
                TraceIdFilter.currentTraceId(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(InviteCodeCreateResponse.from(created));
    }

    @PostMapping("/users/{id}/reset-code")
    public ResetCodeResponse createResetCode(@PathVariable Long id, Authentication authentication,
                                             HttpServletRequest request) {
        Long adminId = currentUserId(authentication);
        PasswordResetService.CreatedResetCode created =
                passwordResetService.create(id, adminId);
        log.info("Password reset code created, userId={}, createdBy={}, expiresAt={}, traceId={}",
                id, adminId, created.expiresAt(), TraceIdFilter.currentTraceId(request));
        return new ResetCodeResponse(created.plaintextCode(), created.expiresAt());
    }

    private Long currentUserId(Authentication authentication) {
        return userRepository.findByUsernameInput(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("authenticated user no longer exists"))
                .getId();
    }
}
