package dev.beatward.monitor;

import dev.beatward.auth.AccountStatus;
import dev.beatward.auth.AppRole;
import dev.beatward.auth.AppUserEntity;
import dev.beatward.auth.AppUserRepository;
import dev.beatward.monitor.config.MonitorConfigInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class MonitorQuotaIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final int QUOTA = MonitorService.MAX_MONITORS_PER_USER;

    @Autowired
    MonitorService monitorService;

    @Autowired
    MonitorRepository monitorRepository;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        monitorRepository.deleteAll();
    }

    @Test
    void countsHttpAndHeartbeatMonitorsTogether() {
        Long userId = createUser();

        for (int i = 0; i < 10; i++) {
            monitorService.create(httpInput("http-" + i), userId);
        }
        for (int i = 0; i < 10; i++) {
            monitorService.create(heartbeatInput("heartbeat-" + i), userId);
        }

        assertThatThrownBy(() -> monitorService.create(httpInput("overflow"), userId))
                .isInstanceOfSatisfying(QuotaExceededException.class, ex -> {
                    assertThat(ex.getCurrent()).isEqualTo(QUOTA);
                    assertThat(ex.getLimit()).isEqualTo(QUOTA);
                    assertThat(ex.getMessage()).contains("20 of 20");
                });
        assertThat(monitorRepository.countByUserId(userId)).isEqualTo(QUOTA);
    }

    @Test
    void quotaAppliesPerUser() {
        Long first = createUser();
        Long second = createUser();
        for (int i = 0; i < QUOTA; i++) {
            monitorService.create(httpInput("first-" + i), first);
        }

        assertThatThrownBy(() -> monitorService.create(httpInput("first-overflow"), first))
                .isInstanceOf(QuotaExceededException.class);

        MonitorEntity other = monitorService.create(httpInput("second-1"), second).monitor();
        assertThat(other.getUserId()).isEqualTo(second);
        assertThat(monitorRepository.countByUserId(second)).isEqualTo(1);
    }

    @Test
    void concurrentCreatesDoNotOversellTheQuota() throws Exception {
        Long userId = createUser();
        int attempts = 30;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                int index = i;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        monitorService.create(httpInput("race-" + index), userId);
                        return true;
                    } catch (QuotaExceededException ex) {
                        return false;
                    }
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int created = 0;
            int rejected = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    created++;
                } else {
                    rejected++;
                }
            }

            assertThat(created).isEqualTo(QUOTA);
            assertThat(rejected).isEqualTo(attempts - QUOTA);
            assertThat(monitorRepository.countByUserId(userId)).isEqualTo(QUOTA);
        } finally {
            executor.shutdownNow();
        }
    }

    private Long createUser() {
        AppUserEntity user = new AppUserEntity();
        user.setUsername("quota-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash(passwordEncoder.encode("user-password-123"));
        user.setRole(AppRole.USER);
        user.setStatus(AccountStatus.ACTIVE);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user).getId();
    }

    private static MonitorConfigInput httpInput(String name) {
        return new MonitorConfigInput(name, "http://8.8.8.8/health", "GET", 60,
                3000, 10000, 200, 299, true, null, null);
    }

    private static MonitorConfigInput heartbeatInput(String name) {
        return new MonitorConfigInput(name, null, null, 300, null, null, null, null,
                true, null, null, "HEARTBEAT", 60);
    }
}
