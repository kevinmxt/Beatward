package dev.beatward.notification;

import dev.beatward.auth.AccountStatus;
import dev.beatward.auth.AppRole;
import dev.beatward.auth.AppUserEntity;
import dev.beatward.auth.AppUserRepository;
import dev.beatward.incident.IncidentEntity;
import dev.beatward.incident.IncidentRepository;
import dev.beatward.incident.IncidentStatus;
import dev.beatward.monitor.CheckFailureType;
import dev.beatward.monitor.MonitorEntity;
import dev.beatward.monitor.MonitorService;
import dev.beatward.monitor.config.MonitorConfigInput;
import dev.beatward.notification.api.NotificationChannelCreateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manual acceptance check for a real PushPlus send. Requires both
 * BEATWARD_TEST_PUSHPLUS_TOKEN and BEATWARD_RUN_REAL_NOTIFICATIONS=true so
 * normal verify runs never send real messages.
 */
@SpringBootTest(properties = {
        "beatward.notification.scheduling-enabled=false",
        "beatward.channel-secret-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Testcontainers
@EnabledIfEnvironmentVariables({
        @EnabledIfEnvironmentVariable(named = "BEATWARD_TEST_PUSHPLUS_TOKEN", matches = ".+"),
        @EnabledIfEnvironmentVariable(named = "BEATWARD_RUN_REAL_NOTIFICATIONS", matches = "true")
})
class RealPushPlusNotificationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    NotificationChannelService channelService;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    MonitorService monitorService;

    @Autowired
    IncidentRepository incidentRepository;

    @Autowired
    NotificationDeliveryRepository deliveryRepository;

    @Autowired
    NotificationOutbox outbox;

    @Autowired
    NotificationDispatcher dispatcher;

    @Test
    void sendsARealPushPlusTestNotification() {
        AppUserEntity user = createUser();
        NotificationChannelEntity channel = createChannel(user);

        var result = channelService.test(user.getId(), channel.getId());

        assertThat(result.status()).isEqualTo(ChannelTestStatus.SUCCESS);
    }

    @Test
    void deliversARealIncidentAlertThroughTheOutbox() {
        AppUserEntity user = createUser();
        createChannel(user);
        MonitorEntity monitor = monitorService.create(new MonitorConfigInput(
                "real-alert", null, null, 300, null, null, null, null, true,
                null, null, "HEARTBEAT", 60), user.getId()).monitor();

        IncidentEntity incident = new IncidentEntity();
        incident.setMonitorId(monitor.getId());
        incident.setStartedAt(Instant.now());
        incident.setStatus(IncidentStatus.OPEN);
        incident.setFailureType(CheckFailureType.CONNECTION_FAILED);
        incident.setSummary("manual acceptance alert");
        incident = incidentRepository.save(incident);

        outbox.enqueue(incident.getId(), NotificationEventType.INCIDENT_OPENED);
        dispatcher.dispatchDue();

        List<NotificationDeliveryEntity> deliveries = deliveryRepository.findAll();
        assertThat(deliveries).hasSize(1);
        NotificationDeliveryEntity delivery = deliveries.getFirst();
        assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(delivery.getLastErrorSummary()).isNull();
    }

    private NotificationChannelEntity createChannel(AppUserEntity user) {
        return channelService.create(user.getId(),
                new NotificationChannelCreateRequest("PUSHPLUS", "manual-acceptance", true,
                        Map.of("token", System.getenv("BEATWARD_TEST_PUSHPLUS_TOKEN"))));
    }

    private AppUserEntity createUser() {
        AppUserEntity user = new AppUserEntity();
        user.setUsername("real-push-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash(
                "{bcrypt}$2a$12$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234");
        user.setRole(AppRole.USER);
        user.setStatus(AccountStatus.ACTIVE);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user);
    }
}
