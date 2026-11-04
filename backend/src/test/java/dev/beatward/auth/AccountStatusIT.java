package dev.beatward.auth;

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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class AccountStatusIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetAdminPassword() {
        AppUserEntity admin = userRepository.findByUsername("test-admin").orElseThrow();
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setUpdatedAt(Instant.now());
        userRepository.save(admin);
    }

    @Test
    void disabledAccountLoginReturnsAccountDisabledCode() throws Exception {
        String username = "disabled-" + UUID.randomUUID();
        createUser(username, AccountStatus.DISABLED);
        Csrf csrf = bootstrapCsrf();

        mockMvc.perform(login(username, PASSWORD, csrf))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void wrongPasswordOnDisabledAccountDoesNotRevealTheAccountExists() throws Exception {
        String username = "disabled-wrong-" + UUID.randomUUID();
        createUser(username, AccountStatus.DISABLED);
        Csrf csrf = bootstrapCsrf();

        mockMvc.perform(login(username, "wrong-password-999", csrf))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void disablingAccountInvalidatesExistingSession() throws Exception {
        String username = "disable-me-" + UUID.randomUUID();
        AppUserEntity user = createUser(username, AccountStatus.ACTIVE);
        Csrf csrf = bootstrapCsrf();
        MockHttpSession session = loginSession(username, PASSWORD, csrf);

        user.setStatus(AccountStatus.DISABLED);
        user.setDisabledAt(Instant.now());
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/auth/session").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void userRoleCanChangeOwnPassword() throws Exception {
        String username = "user-role-" + UUID.randomUUID();
        createUser(username, AccountStatus.ACTIVE, AppRole.USER);
        Csrf csrf = bootstrapCsrf();
        MockHttpSession session = loginSession(username, PASSWORD, csrf);
        String newPassword = "brand-new-password-123";

        mockMvc.perform(put("/api/v1/auth/password")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}
                                """.formatted(PASSWORD, newPassword)))
                .andExpect(status().isNoContent());

        mockMvc.perform(login(username, newPassword, csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void disabledLoginLogsDoNotContainCredentials(CapturedOutput output) throws Exception {
        String username = "disabled-log-" + UUID.randomUUID();
        createUser(username, AccountStatus.DISABLED);
        Csrf csrf = bootstrapCsrf();

        mockMvc.perform(login(username, PASSWORD, csrf))
                .andExpect(status().isForbidden());

        assertThat(output.getAll())
                .doesNotContain(username)
                .doesNotContain(PASSWORD);
    }

    private AppUserEntity createUser(String username, AccountStatus status) {
        return createUser(username, status, AppRole.ADMIN);
    }

    private AppUserEntity createUser(String username, AccountStatus status, AppRole role) {
        AppUserEntity user = new AppUserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
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

    private MockHttpSession loginSession(String username, String password, Csrf csrf) throws Exception {
        MvcResult result = mockMvc.perform(login(username, password, csrf))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String username, String password, Csrf csrf) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s"}
                        """.formatted(username, password))
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token());
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

    private record Csrf(Cookie cookie, String token) {
    }
}
