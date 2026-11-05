package dev.beatward.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.beatward.monitor.MonitorService;
import dev.beatward.monitor.config.MonitorConfigInput;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class AdminConsoleApiIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String ADMIN_PASSWORD = "correct-horse-battery";
    private static final String USER_PASSWORD = "user-password-123";
    private static final AtomicInteger REMOTE_SEQUENCE = new AtomicInteger();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    InviteCodeRepository inviteCodeRepository;

    @Autowired
    InviteCodeService inviteCodeService;

    @Autowired
    AdminUserService adminUserService;

    @Autowired
    MonitorService monitorService;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Long adminId;

    @BeforeEach
    void normalizeAdminState() {
        for (AppUserEntity user : userRepository.findAll()) {
            if (user.getRole() == AppRole.ADMIN && !"test-admin".equals(user.getUsername())) {
                user.setStatus(AccountStatus.DISABLED);
                user.setDisabledAt(Instant.now());
                userRepository.save(user);
            }
        }
        AppUserEntity admin = userRepository.findByUsername("test-admin").orElseThrow();
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setRole(AppRole.ADMIN);
        admin.setStatus(AccountStatus.ACTIVE);
        admin.setDisabledAt(null);
        admin.setUpdatedAt(Instant.now());
        userRepository.save(admin);
        adminId = admin.getId();
    }

    @Test
    void adminListsInviteCodesWithoutReturningPlaintextOrHash() throws Exception {
        InviteCodeService.CreatedInviteCode created =
                inviteCodeService.create(adminId, "list-visible", 4, 24);
        InviteCodeEntity revoked = inviteCodeService.create(adminId, "list-revoked", 1, 24).invite();
        revoked.setRevokedAt(Instant.now());
        inviteCodeRepository.saveAndFlush(revoked);
        Csrf csrf = bootstrapCsrf();
        MockHttpSession adminSession = login("test-admin", ADMIN_PASSWORD, csrf);

        MvcResult result = mockMvc.perform(get("/api/v1/admin/invite-codes")
                        .param("page", "0")
                        .param("size", "100")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode item = findById(body.get("items"), created.invite().getId());
        assertThat(item).isNotNull();
        assertThat(item.get("label").asText()).isEqualTo("list-visible");
        assertThat(item.get("maxUses").asInt()).isEqualTo(4);
        assertThat(item.get("usedCount").asInt()).isZero();
        assertThat(item.get("expiresAt").asText()).isNotBlank();
        assertThat(item.has("code")).isFalse();
        assertThat(item.has("codeHash")).isFalse();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(created.plaintextCode())
                .doesNotContain(created.invite().getCodeHash());
    }

    @Test
    void adminRevokesInviteCodeIdempotentlyAndRevokedCodeCannotRegister() throws Exception {
        InviteCodeService.CreatedInviteCode created =
                inviteCodeService.create(adminId, "to-revoke", 1, 24);
        Csrf csrf = bootstrapCsrf();
        MockHttpSession adminSession = login("test-admin", ADMIN_PASSWORD, csrf);

        mockMvc.perform(withCsrf(post("/api/v1/admin/invite-codes/{id}/revoke",
                                created.invite().getId()), adminSession, csrf))
                .andExpect(status().isNoContent());
        mockMvc.perform(withCsrf(post("/api/v1/admin/invite-codes/{id}/revoke",
                                created.invite().getId()), adminSession, csrf))
                .andExpect(status().isNoContent());

        InviteCodeEntity revoked =
                inviteCodeRepository.findById(created.invite().getId()).orElseThrow();
        assertThat(revoked.getRevokedAt()).isNotNull();

        mockMvc.perform(withCsrf(post("/api/v1/auth/register"), null, csrf)
                        .with(request -> {
                            request.setRemoteAddr(nextRemoteAddress());
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inviteCode":"%s","username":"%s","password":"%s"}
                                """.formatted(created.plaintextCode(), uniqueUsername("revoked"),
                                USER_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITE_REVOKED"));
    }

    @Test
    void revokingUnknownInviteCodeReturnsNotFound() throws Exception {
        Csrf csrf = bootstrapCsrf();
        MockHttpSession adminSession = login("test-admin", ADMIN_PASSWORD, csrf);

        mockMvc.perform(withCsrf(post("/api/v1/admin/invite-codes/{id}/revoke", 999_999L),
                        adminSession, csrf))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));
    }

    @Test
    void adminListsUsersWithMonitorCountsAndNoMonitorDetails() throws Exception {
        AppUserEntity user = createUser("counted", AppRole.USER, AccountStatus.ACTIVE);
        monitorService.create(new MonitorConfigInput("first-api", "http://8.8.8.8/health", "GET",
                60, 3000, 10000, 200, 299, true, 3, 2), user.getId());
        monitorService.create(new MonitorConfigInput("second-api", "http://8.8.4.4/health", "GET",
                60, 3000, 10000, 200, 299, true, 3, 2), user.getId());
        Csrf csrf = bootstrapCsrf();
        MockHttpSession adminSession = login("test-admin", ADMIN_PASSWORD, csrf);

        MvcResult result = mockMvc.perform(get("/api/v1/admin/users")
                        .param("page", "0")
                        .param("size", "100")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode item = findByUsername(body.get("items"), user.getUsername());
        assertThat(item).isNotNull();
        assertThat(item.get("role").asText()).isEqualTo("USER");
        assertThat(item.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(item.get("monitorCount").asInt()).isEqualTo(2);
        assertThat(item.has("passwordHash")).isFalse();
        assertThat(item.has("monitors")).isFalse();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("first-api")
                .doesNotContain("second-api")
                .doesNotContain("8.8.8.8");
    }

    @Test
    void disablingUserInvalidatesSessionsAndEnablingRestoresLogin() throws Exception {
        AppUserEntity user = createUser("disabled", AppRole.USER, AccountStatus.ACTIVE);
        Csrf csrf = bootstrapCsrf();
        MockHttpSession userSession = login(user.getUsername(), USER_PASSWORD, csrf);
        MockHttpSession adminSession = login("test-admin", ADMIN_PASSWORD, csrf);

        mockMvc.perform(withCsrf(patch("/api/v1/admin/users/{id}/status", user.getId()),
                        adminSession, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        assertThat(userSession.isInvalid()).isTrue();
        mockMvc.perform(loginRequest(user.getUsername(), USER_PASSWORD, csrf))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));

        AppUserEntity disabled = userRepository.findById(user.getId()).orElseThrow();
        assertThat(disabled.getStatus()).isEqualTo(AccountStatus.DISABLED);
        assertThat(disabled.getDisabledAt()).isNotNull();

        mockMvc.perform(withCsrf(patch("/api/v1/admin/users/{id}/status", user.getId()),
                        adminSession, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ACTIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(loginRequest(user.getUsername(), USER_PASSWORD, csrf))
                .andExpect(status().isOk());
        AppUserEntity enabled = userRepository.findById(user.getId()).orElseThrow();
        assertThat(enabled.getDisabledAt()).isNull();
    }

    @Test
    void cannotDisableLastActiveAdmin() throws Exception {
        Csrf csrf = bootstrapCsrf();
        MockHttpSession adminSession = login("test-admin", ADMIN_PASSWORD, csrf);

        mockMvc.perform(withCsrf(patch("/api/v1/admin/users/{id}/status", adminId),
                        adminSession, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ACTIVE_ADMIN"));

        assertThat(userRepository.findById(adminId).orElseThrow().getStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void anonymousCallersCannotUseAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/admin/invite-codes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void adminCanBeDisabledWhileAnotherActiveAdminRemains() throws Exception {
        AppUserEntity secondAdmin = createUser("second-admin", AppRole.ADMIN, AccountStatus.ACTIVE);
        Csrf csrf = bootstrapCsrf();
        MockHttpSession secondAdminSession = login(secondAdmin.getUsername(), USER_PASSWORD, csrf);

        mockMvc.perform(withCsrf(patch("/api/v1/admin/users/{id}/status", adminId),
                        secondAdminSession, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        mockMvc.perform(withCsrf(patch("/api/v1/admin/users/{id}/status", secondAdmin.getId()),
                        secondAdminSession, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ACTIVE_ADMIN"));

        assertThat(userRepository.findById(secondAdmin.getId()).orElseThrow().getStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void concurrentDisablingOfTwoAdminsKeepsOneActive() throws Exception {
        AppUserEntity first = createUser("concurrent-one", AppRole.ADMIN, AccountStatus.ACTIVE);
        AppUserEntity second = createUser("concurrent-two", AppRole.ADMIN, AccountStatus.ACTIVE);
        adminUserService.changeStatus(adminId, "DISABLED");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> attemptDisable(first.getId(), start)),
                    executor.submit(() -> attemptDisable(second.getId(), start)));
            start.countDown();

            int successes = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        long activeAdmins = userRepository.findAll().stream()
                .filter(user -> user.getRole() == AppRole.ADMIN)
                .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
                .count();
        assertThat(activeAdmins).isEqualTo(1);
    }

    @Test
    void userRoleCannotUseAdminEndpoints() throws Exception {
        AppUserEntity user = createUser("plain-user", AppRole.USER, AccountStatus.ACTIVE);
        Csrf csrf = bootstrapCsrf();
        MockHttpSession userSession = login(user.getUsername(), USER_PASSWORD, csrf);

        mockMvc.perform(get("/api/v1/admin/users").session(userSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/admin/invite-codes").session(userSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(withCsrf(patch("/api/v1/admin/users/{id}/status", user.getId()),
                        userSession, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void invalidStatusIsRejected() throws Exception {
        AppUserEntity user = createUser("bad-status", AppRole.USER, AccountStatus.ACTIVE);
        Csrf csrf = bootstrapCsrf();
        MockHttpSession adminSession = login("test-admin", ADMIN_PASSWORD, csrf);

        mockMvc.perform(withCsrf(patch("/api/v1/admin/users/{id}/status", user.getId()),
                        adminSession, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUSPENDED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void adminActionsWriteStructuredAuditLogs(CapturedOutput output) throws Exception {
        AppUserEntity user = createUser("audited", AppRole.USER, AccountStatus.ACTIVE);
        Csrf csrf = bootstrapCsrf();
        MockHttpSession adminSession = login("test-admin", ADMIN_PASSWORD, csrf);

        MvcResult inviteResult = mockMvc.perform(withCsrf(post("/api/v1/admin/invite-codes"),
                                adminSession, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"audit","maxUses":1,"expiresInHours":24}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long inviteId = objectMapper.readTree(inviteResult.getResponse().getContentAsString())
                .get("id").asLong();
        String inviteCode = objectMapper.readTree(inviteResult.getResponse().getContentAsString())
                .get("code").asText();

        mockMvc.perform(withCsrf(post("/api/v1/admin/invite-codes/{id}/revoke", inviteId),
                        adminSession, csrf))
                .andExpect(status().isNoContent());

        MvcResult resetResult = mockMvc.perform(withCsrf(
                                post("/api/v1/admin/users/{id}/reset-code", user.getId()),
                                adminSession, csrf))
                .andExpect(status().isOk())
                .andReturn();
        String resetCode = objectMapper.readTree(resetResult.getResponse().getContentAsString())
                .get("code").asText();

        mockMvc.perform(withCsrf(patch("/api/v1/admin/users/{id}/status", user.getId()),
                        adminSession, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(withCsrf(patch("/api/v1/admin/users/{id}/status", user.getId()),
                        adminSession, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ACTIVE"}
                                """))
                .andExpect(status().isOk());

        assertThat(output.getAll())
                .contains("event=invite_code.created")
                .contains("event=invite_code.revoked")
                .contains("event=password_reset_code.created")
                .contains("event=user.status_changed")
                .doesNotContain(inviteCode)
                .doesNotContain(resetCode);
    }

    private boolean attemptDisable(Long userId, CountDownLatch start) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try {
            adminUserService.changeStatus(userId, "DISABLED");
            return true;
        } catch (LastActiveAdminException ex) {
            return false;
        }
    }

    private AppUserEntity createUser(String prefix, AppRole role, AccountStatus status) {
        AppUserEntity user = new AppUserEntity();
        user.setUsername(uniqueUsername(prefix));
        user.setPasswordHash(passwordEncoder.encode(USER_PASSWORD));
        user.setRole(role);
        user.setStatus(status);
        if (status == AccountStatus.DISABLED) {
            user.setDisabledAt(Instant.now());
        }
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user);
    }

    private MockHttpSession login(String username, String password, Csrf csrf) throws Exception {
        MvcResult result = mockMvc.perform(loginRequest(username, password, csrf))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpServletRequestBuilder loginRequest(String username, String password,
                                                       Csrf csrf) {
        return withCsrf(post("/api/v1/auth/login"), null, csrf)
                .with(request -> {
                    request.setRemoteAddr(nextRemoteAddress());
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s"}
                        """.formatted(username, password));
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder,
                                                   MockHttpSession session, Csrf csrf) {
        builder.cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token());
        if (session != null) {
            builder.session(session);
        }
        return builder;
    }

    private Csrf bootstrapCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/session"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("csrfToken").asText();
        return new Csrf(cookie, token);
    }

    private static JsonNode findById(JsonNode items, Long id) {
        for (JsonNode item : items) {
            if (item.get("id").asLong() == id) {
                return item;
            }
        }
        return null;
    }

    private static JsonNode findByUsername(JsonNode items, String username) {
        for (JsonNode item : items) {
            if (username.equals(item.get("username").asText())) {
                return item;
            }
        }
        return null;
    }

    private static String nextRemoteAddress() {
        return "10.40.0." + (REMOTE_SEQUENCE.incrementAndGet() % 250 + 1);
    }

    private static String uniqueUsername(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record Csrf(Cookie cookie, String token) {
    }
}
