package dev.beatward.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.beatward.incident.IncidentEntity;
import dev.beatward.incident.IncidentRepository;
import dev.beatward.incident.IncidentStatus;
import dev.beatward.monitor.CheckFailureType;
import dev.beatward.monitor.MonitorRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OwnershipIsolationIT {

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
    MonitorRepository monitorRepository;

    @Autowired
    IncidentRepository incidentRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanMonitors() {
        monitorRepository.deleteAll();
        AppUserEntity admin = userRepository.findByUsername("test-admin").orElseThrow();
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        userRepository.save(admin);
    }

    @Test
    void usersOnlySeeTheirOwnMonitors() throws Exception {
        Csrf csrf = bootstrapCsrf();
        String alice = createUser("alice");
        String bob = createUser("bob");
        MockHttpSession aliceSession = login(alice, csrf, "10.20.0.1");
        MockHttpSession bobSession = login(bob, csrf, "10.20.0.2");

        long aliceMonitor = createHttpMonitor(aliceSession, csrf, "alice-api");
        long bobMonitor = createHttpMonitor(bobSession, csrf, "bob-api");

        mockMvc.perform(get("/api/v1/monitors").session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(aliceMonitor))
                .andExpect(jsonPath("$.items[0].name").value("alice-api"));

        mockMvc.perform(get("/api/v1/monitors").session(bobSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(bobMonitor))
                .andExpect(jsonPath("$.items[0].name").value("bob-api"));
    }

    @Test
    void crossUserMonitorAccessReturns404() throws Exception {
        Csrf csrf = bootstrapCsrf();
        String alice = createUser("alice");
        String bob = createUser("bob");
        MockHttpSession aliceSession = login(alice, csrf, "10.20.0.3");
        MockHttpSession bobSession = login(bob, csrf, "10.20.0.4");
        createHttpMonitor(aliceSession, csrf, "alice-api");
        long bobMonitor = createHttpMonitor(bobSession, csrf, "bob-api");

        mockMvc.perform(get("/api/v1/monitors/{id}", bobMonitor).session(aliceSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MONITOR_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/monitors/{id}/checks", bobMonitor).session(aliceSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MONITOR_NOT_FOUND"));

        mockMvc.perform(withCsrf(put("/api/v1/monitors/{id}", bobMonitor), aliceSession, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"hijacked","targetUrl":"http://8.8.8.8/other"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MONITOR_NOT_FOUND"));

        mockMvc.perform(withCsrf(delete("/api/v1/monitors/{id}", bobMonitor), aliceSession, csrf))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MONITOR_NOT_FOUND"));

        mockMvc.perform(withCsrf(post("/api/v1/monitors/{id}/heartbeat-token", bobMonitor),
                        aliceSession, csrf))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MONITOR_NOT_FOUND"));

        mockMvc.perform(withCsrf(delete("/api/v1/monitors/{id}/heartbeat-token", bobMonitor),
                        aliceSession, csrf))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MONITOR_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/monitors/{id}", bobMonitor).session(bobSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("bob-api"));
    }

    @Test
    void usersOnlySeeTheirOwnIncidents() throws Exception {
        Csrf csrf = bootstrapCsrf();
        String alice = createUser("alice");
        String bob = createUser("bob");
        MockHttpSession aliceSession = login(alice, csrf, "10.20.0.5");
        MockHttpSession bobSession = login(bob, csrf, "10.20.0.6");
        long aliceMonitor = createHttpMonitor(aliceSession, csrf, "alice-api");
        long bobMonitor = createHttpMonitor(bobSession, csrf, "bob-api");
        IncidentEntity aliceIncident = createIncident(aliceMonitor, "alice outage");
        IncidentEntity bobIncident = createIncident(bobMonitor, "bob outage");

        mockMvc.perform(get("/api/v1/incidents").session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(aliceIncident.getId()))
                .andExpect(jsonPath("$.items[0].monitorId").value(aliceMonitor));

        mockMvc.perform(get("/api/v1/incidents").param("monitor_id", String.valueOf(bobMonitor))
                        .session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/incidents/{id}", bobIncident.getId()).session(aliceSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INCIDENT_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/incidents/{id}", aliceIncident.getId()).session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monitorId").value(aliceMonitor));
    }

    @Test
    void adminDoesNotSeeOtherUsersBusinessData() throws Exception {
        Csrf csrf = bootstrapCsrf();
        String alice = createUser("alice");
        MockHttpSession aliceSession = login(alice, csrf, "10.20.0.7");
        MockHttpSession adminSession = login("test-admin", csrf, "10.20.0.8");
        long aliceMonitor = createHttpMonitor(aliceSession, csrf, "alice-api");
        IncidentEntity aliceIncident = createIncident(aliceMonitor, "alice outage");

        mockMvc.perform(get("/api/v1/monitors").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/monitors/{id}", aliceMonitor).session(adminSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MONITOR_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/incidents").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/incidents/{id}", aliceIncident.getId()).session(adminSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INCIDENT_NOT_FOUND"));
    }

    @Test
    void usersCanReadTheirOwnMonitorAndHistory() throws Exception {
        Csrf csrf = bootstrapCsrf();
        String alice = createUser("alice");
        MockHttpSession aliceSession = login(alice, csrf, "10.20.0.9");
        long aliceMonitor = createHttpMonitor(aliceSession, csrf, "alice-api");

        mockMvc.perform(get("/api/v1/monitors/{id}", aliceMonitor).session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(aliceMonitor));

        mockMvc.perform(get("/api/v1/monitors/{id}/checks", aliceMonitor).session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private String createUser(String prefix) {
        AppUserEntity user = new AppUserEntity();
        user.setUsername(prefix + "-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash(passwordEncoder.encode(USER_PASSWORD));
        user.setRole(AppRole.USER);
        user.setStatus(AccountStatus.ACTIVE);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user).getUsername();
    }

    private long createHttpMonitor(MockHttpSession session, Csrf csrf, String name) throws Exception {
        MvcResult result = mockMvc.perform(withCsrf(post("/api/v1/monitors"), session, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","targetUrl":"http://8.8.8.8/health"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private IncidentEntity createIncident(long monitorId, String summary) {
        IncidentEntity incident = new IncidentEntity();
        incident.setMonitorId(monitorId);
        incident.setStartedAt(Instant.now());
        incident.setStatus(IncidentStatus.OPEN);
        incident.setFailureType(CheckFailureType.CONNECTION_FAILED);
        incident.setSummary(summary);
        return incidentRepository.save(incident);
    }

    private MockHttpSession login(String username, Csrf csrf, String remoteAddress)
            throws Exception {
        MvcResult result = mockMvc.perform(withCsrf(post("/api/v1/auth/login"), null, csrf)
                        .with(request -> {
                            request.setRemoteAddr(remoteAddress);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, passwordFor(username))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String passwordFor(String username) {
        return "test-admin".equals(username) ? ADMIN_PASSWORD : USER_PASSWORD;
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

    private record Csrf(Cookie cookie, String token) {
    }
}
