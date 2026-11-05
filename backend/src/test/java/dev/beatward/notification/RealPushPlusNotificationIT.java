package dev.beatward.notification;

import dev.beatward.auth.AccountStatus;
import dev.beatward.auth.AppRole;
import dev.beatward.auth.AppUserEntity;
import dev.beatward.auth.AppUserRepository;
import dev.beatward.notification.api.NotificationChannelCreateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
 * Manual acceptance check for a real PushPlus send. Skipped unless
 * BEATWARD_TEST_PUSHPLUS_TOKEN is set in the environment.
 */
@SpringBootTest(properties = {
        "beatward.notification.scheduling-enabled=false",
        "beatward.channel-secret-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Testcontainers
@EnabledIfEnvironmentVariable(named = "BEATWARD_TEST_PUSHPLUS_TOKEN", matches = ".+")
class RealPushPlusNotificationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    NotificationChannelService channelService;

    @Autowired
    AppUserRepository userRepository;

    @Test
    void sendsARealPushPlusTestNotification() {
        String token = System.getenv("BEATWARD_TEST_PUSHPLUS_TOKEN");
        AppUserEntity user = new AppUserEntity();
        user.setUsername("real-push-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash(
                "{bcrypt}$2a$12$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234");
        user.setRole(AppRole.USER);
        user.setStatus(AccountStatus.ACTIVE);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user = userRepository.save(user);

        NotificationChannelEntity channel = channelService.create(user.getId(),
                new NotificationChannelCreateRequest("PUSHPLUS", "manual-acceptance", true,
                        Map.of("token", token)));

        var result = channelService.test(user.getId(), channel.getId());

        assertThat(result.status()).isEqualTo(ChannelTestStatus.SUCCESS);
    }
}
