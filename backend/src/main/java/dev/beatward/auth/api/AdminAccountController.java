package dev.beatward.auth.api;

import dev.beatward.auth.AccountStatus;
import dev.beatward.auth.AdminAuditLog;
import dev.beatward.auth.AdminSessionRegistry;
import dev.beatward.auth.AdminUserService;
import dev.beatward.auth.AppUserEntity;
import dev.beatward.auth.CurrentUserProvider;
import dev.beatward.auth.InviteCodeEntity;
import dev.beatward.auth.InviteCodeService;
import dev.beatward.auth.PasswordResetService;
import dev.beatward.common.PageResponse;
import dev.beatward.common.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminAccountController {

    private final InviteCodeService inviteCodeService;
    private final PasswordResetService passwordResetService;
    private final AdminUserService adminUserService;
    private final AdminSessionRegistry sessionRegistry;
    private final AdminAuditLog auditLog;
    private final CurrentUserProvider currentUserProvider;

    public AdminAccountController(InviteCodeService inviteCodeService,
                                  PasswordResetService passwordResetService,
                                  AdminUserService adminUserService,
                                  AdminSessionRegistry sessionRegistry,
                                  AdminAuditLog auditLog,
                                  CurrentUserProvider currentUserProvider) {
        this.inviteCodeService = inviteCodeService;
        this.passwordResetService = passwordResetService;
        this.adminUserService = adminUserService;
        this.sessionRegistry = sessionRegistry;
        this.auditLog = auditLog;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/invite-codes")
    public ResponseEntity<InviteCodeCreateResponse> createInviteCode(
            @RequestBody(required = false) InviteCodeCreateRequest body,
            Authentication authentication, HttpServletRequest request) {
        InviteCodeCreateRequest input = body == null
                ? new InviteCodeCreateRequest(null, null, null)
                : body;
        Long adminId = currentUserProvider.requireId(authentication);
        InviteCodeService.CreatedInviteCode created = inviteCodeService.create(
                adminId, input.label(), input.maxUses(), input.expiresInHours());
        auditLog.inviteCodeCreated(adminId, created.invite().getId(),
                created.invite().getMaxUses(), created.invite().getExpiresAt(),
                TraceIdFilter.currentTraceId(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(InviteCodeCreateResponse.from(created));
    }

    @GetMapping("/invite-codes")
    public PageResponse<InviteCodeResponse> listInviteCodes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<InviteCodeEntity> result = inviteCodeService.list(page, size);
        List<InviteCodeResponse> items = result.getContent().stream()
                .map(InviteCodeResponse::from)
                .toList();
        return new PageResponse<>(items, page, size, result.getTotalElements());
    }

    @PostMapping("/invite-codes/{id}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeInviteCode(@PathVariable Long id, Authentication authentication,
                                 HttpServletRequest request) {
        Long adminId = currentUserProvider.requireId(authentication);
        inviteCodeService.revoke(id);
        auditLog.inviteCodeRevoked(adminId, id, TraceIdFilter.currentTraceId(request));
    }

    @GetMapping("/users")
    public PageResponse<AdminUserResponse> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AppUserEntity> result = adminUserService.listUsers(page, size);
        List<Long> userIds = result.getContent().stream().map(AppUserEntity::getId).toList();
        var counts = adminUserService.monitorCounts(userIds);
        List<AdminUserResponse> items = result.getContent().stream()
                .map(user -> AdminUserResponse.from(user, counts.getOrDefault(user.getId(), 0L)))
                .toList();
        return new PageResponse<>(items, page, size, result.getTotalElements());
    }

    @PatchMapping("/users/{id}/status")
    public AdminUserResponse changeUserStatus(@PathVariable Long id,
                                              @RequestBody(required = false) AdminUserStatusRequest body,
                                              Authentication authentication,
                                              HttpServletRequest request) {
        Long adminId = currentUserProvider.requireId(authentication);
        AppUserEntity user = adminUserService.changeStatus(id, body == null ? null : body.status());
        if (user.getStatus() == AccountStatus.DISABLED) {
            sessionRegistry.invalidateAll(user.getUsername());
        }
        auditLog.userStatusChanged(adminId, user.getId(), user.getStatus(),
                TraceIdFilter.currentTraceId(request));
        return AdminUserResponse.from(user, adminUserService.monitorCount(user.getId()));
    }

    @PostMapping("/users/{id}/reset-code")
    public ResetCodeResponse createResetCode(@PathVariable Long id, Authentication authentication,
                                             HttpServletRequest request) {
        Long adminId = currentUserProvider.requireId(authentication);
        PasswordResetService.CreatedResetCode created =
                passwordResetService.create(id, adminId);
        auditLog.passwordResetCodeCreated(adminId, id, created.expiresAt(),
                TraceIdFilter.currentTraceId(request));
        return new ResetCodeResponse(created.plaintextCode(), created.expiresAt());
    }
}
