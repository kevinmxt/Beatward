package dev.beatward.notification;

import dev.beatward.auth.AccountStatus;
import dev.beatward.auth.AppRole;
import dev.beatward.auth.AppUserEntity;
import dev.beatward.auth.AppUserRepository;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manual acceptance check for a real email send. Requires a recipient, a
 * configured global SMTP and BEATWARD_RUN_REAL_NOTIFICATIONS=true so normal
 * verify runs never send real messages.
 */
@SpringBootTest(properties = {
        "beatward.notification.scheduling-enabled=false",
        "beatward.channel-secret-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Testcontainers
@EnabledIfEnvironmentVariables({
        @EnabledIfEnvironmentVariable(named = "BEATWARD_TEST_EMAIL_TO", matches = ".+"),
        @EnabledIfEnvironmentVariable(named = "BEATWARD_SMTP_HOST", matches = ".+"),
        @EnabledIfEnvironmentVariable(named = "BEATWARD_RUN_REAL_NOTIFICATIONS", matches = "true")
})
class RealEmailNotificationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    NotificationChannelService channelService;

    @Autowired
    AppUserRepository userRepository;

    @Test
    void sendsARealEmailTestNotification() {
        AppUserEntity user = new AppUserEntity();
        user.setUsername("real-email-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash(
                "{bcrypt}$2a$12$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234");
        user.setRole(AppRole.USER);
        user.setStatus(AccountStatus.ACTIVE);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user = userRepository.save(user);

        NotificationChannelEntity channel = channelService.create(user.getId(),
                new NotificationChannelCreateRequest("EMAIL", "manual-acceptance", true,
                        Map.of("email", System.getenv("BEATWARD_TEST_EMAIL_TO"))));

        var result = channelService.test(user.getId(), channel.getId());

        assertThat(result.status()).isEqualTo(ChannelTestStatus.SUCCESS);
    }
}
