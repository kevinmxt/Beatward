package dev.beatward.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class RegistrationApiIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String ADMIN_PASSWORD = "correct-horse-battery";
    private static final String USER_PASSWORD = "user-password-123";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    InviteCodeRepository inviteCodeRepository;

    @Autowired
    PasswordResetTokenRepository resetTokenRepository;

    @Autowired
    InviteCodeService inviteCodeService;

    @Autowired
    RegistrationService registrationService;

    @Autowired
    PasswordResetService passwordResetService;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Long adminId;

    @BeforeEach
    void resetAdminPassword() {
        AppUserEntity admin = userRepository.findByUsername("test-admin").orElseThrow();
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setUpdatedAt(Instant.now());
        userRepository.save(admin);
        adminId = admin.getId();
    }

    @Test
    void adminCreatesInviteCodeAndStoresOnlyTheHash() throws Exception {
        Csrf csrf = bootstrapCsrf();
        MockHttpSession session = adminSession(csrf);

        MvcResult result = mockMvc.perform(withRemote(post("/api/v1/admin/invite-codes"), "10.10.0.1")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"batch-1","maxUses":3,"expiresInHours":24}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.label").value("batch-1"))
                .andExpect(jsonPath("$.maxUses").value(3))
                .andReturn();

        String code = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("code").asText();
        assertThat(code).hasSize(8);
        assertThat(inviteCodeRepository.findByCodeHash(sha256(code))).isPresent();
        assertThat(inviteCodeRepository.findAll())
                .noneSatisfy(invite -> assertThat(invite.getCodeHash()).isEqualTo(code));
    }

    @Test
    void registerWithValidInviteAutoLogsInAsUser() throws Exception {
        String invite = newInvite(1);
        String username = uniqueUsername("register");
        Csrf csrf = bootstrapCsrf();

        MvcResult result = mockMvc.perform(registerRequest(invite, username, USER_PASSWORD, csrf,
                        "10.10.0.2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("USER"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mockMvc.perform(get("/api/v1/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("USER"));

        AppUserEntity user = userRepository.findByUsername(username).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getLastLoginAt()).isNotNull();
        InviteCodeEntity inviteRow = inviteCodeRepository.findByCodeHash(sha256(invite)).orElseThrow();
        assertThat(inviteRow.getUsedCount()).isEqualTo(1);
    }

    @Test
    void registerAcceptsLowercaseInviteCodeWithSeparators() throws Exception {
        String invite = newInvite(1);
        String typed = invite.substring(0, 4).toLowerCase(Locale.ROOT) + "-"
                + invite.substring(4).toLowerCase(Locale.ROOT);
        String username = uniqueUsername("normalized-invite");

        mockMvc.perform(registerRequest(typed, username, USER_PASSWORD,
                        bootstrapCsrf(), "10.10.0.16"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    void registerRejectsUnknownInviteCode() throws Exception {
        mockMvc.perform(registerRequest("not-a-real-code", uniqueUsername("bad"), USER_PASSWORD,
                        bootstrapCsrf(), "10.10.0.3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITE_INVALID"));
    }

    @Test
    void invalidInviteIsRejectedBeforeUsernameLookup() throws Exception {
        mockMvc.perform(registerRequest("still-not-a-real-code", "test-admin", USER_PASSWORD,
                        bootstrapCsrf(), "10.10.0.15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITE_INVALID"));
    }

    @Test
    void registerRejectsExpiredInviteCode() throws Exception {
        InviteCodeService.CreatedInviteCode created = inviteCodeService.create(adminId, "expired", 1, 1);
        InviteCodeEntity invite = created.invite();
        invite.setExpiresAt(Instant.now().minusSeconds(5));
        inviteCodeRepository.saveAndFlush(invite);

        mockMvc.perform(registerRequest(created.plaintextCode(), uniqueUsername("expired"),
                        USER_PASSWORD, bootstrapCsrf(), "10.10.0.4"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITE_EXPIRED"));
    }

    @Test
    void registerRejectsRevokedInviteCode() throws Exception {
        InviteCodeService.CreatedInviteCode created = inviteCodeService.create(adminId, "revoked", 1, 1);
        InviteCodeEntity invite = created.invite();
        invite.setRevokedAt(Instant.now());
        inviteCodeRepository.saveAndFlush(invite);

        mockMvc.perform(registerRequest(created.plaintextCode(), uniqueUsername("revoked"),
                        USER_PASSWORD, bootstrapCsrf(), "10.10.0.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITE_REVOKED"));
    }

    @Test
    void registerRejectsExhaustedInviteCode() throws Exception {
        String invite = newInvite(1);
        Csrf csrf = bootstrapCsrf();
        mockMvc.perform(registerRequest(invite, uniqueUsername("first"), USER_PASSWORD, csrf,
                        "10.10.0.6"))
                .andExpect(status().isCreated());

        mockMvc.perform(registerRequest(invite, uniqueUsername("second"), USER_PASSWORD, csrf,
                        "10.10.0.6"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITE_EXHAUSTED"));
    }

    @Test
    void duplicateUsernameDoesNotConsumeInviteUse() throws Exception {
        String invite = newInvite(1);
        Csrf csrf = bootstrapCsrf();

        mockMvc.perform(registerRequest(invite, "test-admin", USER_PASSWORD, csrf, "10.10.0.7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));

        String username = uniqueUsername("after-conflict");
        mockMvc.perform(registerRequest(invite, username, USER_PASSWORD, csrf, "10.10.0.7"))
                .andExpect(status().isCreated());

        InviteCodeEntity inviteRow = inviteCodeRepository.findByCodeHash(sha256(invite)).orElseThrow();
        assertThat(inviteRow.getUsedCount()).isEqualTo(1);
    }

    @Test
    void concurrentRegistrationDoesNotOversellTheInvite() throws Exception {
        InviteCodeService.CreatedInviteCode created = inviteCodeService.create(adminId, "race", 3, 1);
        int attempts = 8;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                String username = uniqueUsername("race-" + i);
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        registrationService.register(created.plaintextCode(), username,
                                USER_PASSWORD);
                        return true;
                    } catch (InviteCodeException ex) {
                        assertThat(ex.code()).isEqualTo("INVITE_EXHAUSTED");
                        return false;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            long successes = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(3);
        } finally {
            executor.shutdownNow();
        }

        InviteCodeEntity invite = inviteCodeRepository
                .findByCodeHash(sha256(created.plaintextCode())).orElseThrow();
        assertThat(invite.getUsedCount()).isEqualTo(3);
    }

    @Test
    void resetCodeIsSingleUseAndInvalidatesSessions() throws Exception {
        String invite = newInvite(1);
        String username = uniqueUsername("reset");
        Csrf csrf = bootstrapCsrf();
        MvcResult registration = mockMvc.perform(registerRequest(invite, username, USER_PASSWORD,
                        csrf, "10.10.0.8"))
                .andExpect(status().isCreated())
                .andReturn();
        MockHttpSession session = (MockHttpSession) registration.getRequest().getSession(false);
        AppUserEntity user = userRepository.findByUsername(username).orElseThrow();
        String resetCode = passwordResetService.create(user.getId(), adminId).plaintextCode();
        String newPassword = "reset-password-456";

        mockMvc.perform(withRemote(post("/api/v1/auth/reset-password"), "10.10.0.8")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resetCode":"%s","newPassword":"%s"}
                                """.formatted(resetCode, newPassword)))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(login(username, USER_PASSWORD, csrf, "10.10.0.8"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(login(username, newPassword, csrf, "10.10.0.8"))
                .andExpect(status().isOk());

        mockMvc.perform(withRemote(post("/api/v1/auth/reset-password"), "10.10.0.8")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resetCode":"%s","newPassword":"another-password-789"}
                                """.formatted(resetCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    void resetRejectsTheCurrentPasswordWithoutConsumingTheCode() throws Exception {
        String username = uniqueUsername("same-reset");
        AppUserEntity user = createUser(username);
        PasswordResetService.CreatedResetCode created =
                passwordResetService.create(user.getId(), adminId);
        Csrf csrf = bootstrapCsrf();

        mockMvc.perform(withRemote(post("/api/v1/auth/reset-password"), "10.10.0.14")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resetCode":"%s","newPassword":"%s"}
                                """.formatted(created.plaintextCode(), USER_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(withRemote(post("/api/v1/auth/reset-password"), "10.10.0.14")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resetCode":"%s","newPassword":"another-password-789"}
                                """.formatted(created.plaintextCode())))
                .andExpect(status().isNoContent());
    }

    @Test
    void expiredResetCodeIsRejected() throws Exception {
        String username = uniqueUsername("expired-reset");
        AppUserEntity user = createUser(username);
        PasswordResetService.CreatedResetCode created =
                passwordResetService.create(user.getId(), adminId);
        PasswordResetTokenEntity token = resetTokenRepository
                .findByTokenHash(sha256(created.plaintextCode())).orElseThrow();
        token.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
        resetTokenRepository.saveAndFlush(token);
        Csrf csrf = bootstrapCsrf();

        mockMvc.perform(withRemote(post("/api/v1/auth/reset-password"), "10.10.0.9")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resetCode":"%s","newPassword":"another-password-789"}
                                """.formatted(created.plaintextCode())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    void adminCanGenerateAResetCodeAndUserCannotCreateInvites() throws Exception {
        String username = uniqueUsername("admin-reset");
        AppUserEntity user = createUser(username);
        Csrf csrf = bootstrapCsrf();
        MockHttpSession adminSession = adminSession(csrf);

        MvcResult result = mockMvc.perform(withRemote(
                                post("/api/v1/admin/users/{id}/reset-code", user.getId()), "10.10.0.10")
                        .session(adminSession)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andReturn();
        String resetCode = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("code").asText();
        assertThat(resetCode).isNotBlank();

        MockHttpSession userSession = loginSession(username, USER_PASSWORD, csrf, "10.10.0.10");
        mockMvc.perform(withRemote(post("/api/v1/admin/invite-codes"), "10.10.0.10")
                        .session(userSession)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerRequiresCsrf() throws Exception {
        String invite = newInvite(1);

        mockMvc.perform(withRemote(post("/api/v1/auth/register"), "10.10.0.11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(invite, uniqueUsername("csrf"), USER_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void repeatedInvalidInvitesAreRateLimited() throws Exception {
        Csrf csrf = bootstrapCsrf();
        String address = "10.10.0.12";
        for (int attempt = 0; attempt < LoginRateLimiter.MAX_ATTEMPTS; attempt++) {
            mockMvc.perform(registerRequest("invalid-" + attempt, uniqueUsername("limited"),
                            USER_PASSWORD, csrf, address))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(registerRequest("invalid-final", uniqueUsername("limited"),
                        USER_PASSWORD, csrf, address))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void registrationAndResetLogsDoNotContainSecrets(CapturedOutput output) throws Exception {
        String invite = newInvite(1);
        String username = uniqueUsername("log");
        Csrf csrf = bootstrapCsrf();
        MvcResult registration = mockMvc.perform(registerRequest(invite, username, USER_PASSWORD,
                        csrf, "10.10.0.13"))
                .andExpect(status().isCreated())
                .andReturn();
        AppUserEntity user = userRepository.findByUsername(username).orElseThrow();
        String resetCode = passwordResetService.create(user.getId(), adminId).plaintextCode();

        mockMvc.perform(withRemote(post("/api/v1/auth/reset-password"), "10.10.0.13")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resetCode":"%s","newPassword":"reset-password-456"}
                                """.formatted(resetCode)))
                .andExpect(status().isNoContent());

        assertThat(output.getAll())
                .doesNotContain(invite)
                .doesNotContain(resetCode)
                .doesNotContain(USER_PASSWORD)
                .doesNotContain("reset-password-456");
    }

    private String newInvite(int maxUses) {
        return inviteCodeService.create(adminId, "test", maxUses, 1).plaintextCode();
    }

    private AppUserEntity createUser(String username) {
        AppUserEntity user = new AppUserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(USER_PASSWORD));
        user.setRole(AppRole.USER);
        user.setStatus(AccountStatus.ACTIVE);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user);
    }

    private MockHttpServletRequestBuilder registerRequest(String invite, String username,
                                                          String password, Csrf csrf,
                                                          String remoteAddress) {
        return withRemote(post("/api/v1/auth/register"), remoteAddress)
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(invite, username, password));
    }

    private static String registerJson(String invite, String username, String password) {
        return """
                {"inviteCode":"%s","username":"%s","password":"%s"}
                """.formatted(invite, username, password);
    }

    private MockHttpSession adminSession(Csrf csrf) throws Exception {
        return loginSession("test-admin", ADMIN_PASSWORD, csrf, "10.10.0.1");
    }

    private MockHttpSession loginSession(String username, String password, Csrf csrf,
                                         String remoteAddress) throws Exception {
        MvcResult result = mockMvc.perform(login(username, password, csrf, remoteAddress))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpServletRequestBuilder login(String username, String password, Csrf csrf,
                                                String remoteAddress) {
        return withRemote(post("/api/v1/auth/login"), remoteAddress)
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s"}
                        """.formatted(username, password));
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

    private static MockHttpServletRequestBuilder withRemote(MockHttpServletRequestBuilder builder,
                                                            String remoteAddress) {
        return builder.with(request -> {
            request.setRemoteAddr(remoteAddress);
            return request;
        });
    }

    private static String uniqueUsername(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String sha256(String value) {
        return new SecureTokenGenerator().digest(value);
    }

    private record Csrf(Cookie cookie, String token) {
    }
}
