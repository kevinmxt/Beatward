package dev.beatward.auth;

import dev.beatward.monitor.MonitorCreationResult;
import dev.beatward.monitor.MonitorEntity;
import dev.beatward.monitor.MonitorRepository;
import dev.beatward.monitor.MonitorService;
import dev.beatward.monitor.config.MonitorConfigInput;
import dev.beatward.monitor.probe.CheckResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DisabledAccountSchedulingIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    MonitorService monitorService;

    @Autowired
    MonitorRepository monitorRepository;

    @Autowired
    CheckResultRepository checkResultRepository;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void cleanMonitors() {
        checkResultRepository.deleteAll();
        monitorRepository.deleteAll();
    }

    @Test
    void httpMonitorsOfDisabledAccountsAreNotDue() {
        AppUserEntity user = createUser(AccountStatus.ACTIVE);
        MonitorEntity monitor = monitorService.create(httpInput("owned-http"), user.getId()).monitor();

        Instant now = Instant.now().plusSeconds(5);
        assertThat(dueHttp(now)).contains(monitor.getId());

        setStatus(user, AccountStatus.DISABLED);
        assertThat(dueHttp(now)).doesNotContain(monitor.getId());

        setStatus(user, AccountStatus.ACTIVE);
        assertThat(dueHttp(now)).contains(monitor.getId());
    }

    @Test
    void heartbeatMonitorsOfDisabledAccountsAreNotDue() {
        AppUserEntity user = createUser(AccountStatus.ACTIVE);
        MonitorEntity monitor = monitorService.create(heartbeatInput("owned-heartbeat"), user.getId())
                .monitor();
        monitor.setNextCheckAt(Instant.now().minusSeconds(60));
        monitorRepository.save(monitor);

        Instant now = Instant.now();
        assertThat(dueHeartbeat(now)).contains(monitor.getId());

        setStatus(user, AccountStatus.DISABLED);
        assertThat(dueHeartbeat(now)).doesNotContain(monitor.getId());

        setStatus(user, AccountStatus.ACTIVE);
        assertThat(dueHeartbeat(now)).contains(monitor.getId());
    }

    @Test
    void heartbeatReportsFromDisabledAccountsAreRejected() throws Exception {
        AppUserEntity user = createUser(AccountStatus.ACTIVE);
        MonitorCreationResult created = monitorService.create(
                heartbeatInput("disabled-report"), user.getId());
        String token = created.heartbeatToken();

        setStatus(user, AccountStatus.DISABLED);
        mockMvc.perform(post("/api/v1/heartbeats/{token}", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HEARTBEAT_NOT_FOUND"));
        assertThat(checkResultRepository.count()).isZero();

        setStatus(user, AccountStatus.ACTIVE);
        mockMvc.perform(post("/api/v1/heartbeats/{token}", token))
                .andExpect(status().isNoContent());
        assertThat(checkResultRepository.count()).isEqualTo(1);
    }

    private List<Long> dueHttp(Instant now) {
        return monitorRepository.findDueMonitorIds(
                now, AccountStatus.ACTIVE, PageRequest.of(0, 100));
    }

    private List<Long> dueHeartbeat(Instant now) {
        return monitorRepository.findDueHeartbeatIds(
                now, AccountStatus.ACTIVE, PageRequest.of(0, 100));
    }

    private AppUserEntity createUser(AccountStatus status) {
        AppUserEntity user = new AppUserEntity();
        user.setUsername("scheduler-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash(passwordEncoder.encode("user-password-123"));
        user.setRole(AppRole.USER);
        user.setStatus(status);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user);
    }

    private void setStatus(AppUserEntity user, AccountStatus status) {
        user.setStatus(status);
        user.setDisabledAt(status == AccountStatus.DISABLED ? Instant.now() : null);
        userRepository.save(user);
    }

    private static MonitorConfigInput httpInput(String name) {
        return new MonitorConfigInput(name, "http://8.8.8.8/health", "GET", 60,
                3000, 10000, 200, 299, true, null, null);
    }

    private static MonitorConfigInput heartbeatInput(String name) {
        return new MonitorConfigInput(name, null, null, 60, null, null, null, null,
                true, null, null, "HEARTBEAT", 30);
    }
}
