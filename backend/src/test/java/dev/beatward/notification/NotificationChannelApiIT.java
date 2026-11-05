package dev.beatward.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.beatward.auth.AccountStatus;
import dev.beatward.auth.AppRole;
import dev.beatward.auth.AppUserEntity;
import dev.beatward.auth.AppUserRepository;
import dev.beatward.monitor.MonitorService;
import dev.beatward.monitor.config.MonitorConfigInput;
import dev.beatward.notification.api.NotificationChannelCreateRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "beatward.notification.scheduling-enabled=false",
        "beatward.channel-secret-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "beatward.smtp.host=",
        "beatward.smtp.username=",
        "beatward.smtp.password=",
        "beatward.smtp.from="
})
@AutoConfigureMockMvc
@Testcontainers
class NotificationChannelApiIT {

    private static final String TOKEN = "pushplus-token-1234";
    private static final HttpServer SERVER = startServer();
    private static final int PORT = SERVER.getAddress().getPort();
    private static final List<String> PUSHPLUS_REQUESTS = new CopyOnWriteArrayList<>();
    private static volatile int pushplusStatus = 200;
    private static volatile String pushplusBody = "{\"code\":200,\"msg\":\"ok\"}";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    NotificationChannelRepository channelRepository;

    @Autowired
    ChannelSecretCipher cipher;

    @Autowired
    NotificationChannelService channelService;

    @Autowired
    MonitorService monitorService;

    @DynamicPropertySource
    static void endpointProperties(DynamicPropertyRegistry registry) {
        registry.add("beatward.notification.endpoints.pushplus-api-base-url",
                () -> "http://127.0.0.1:" + PORT);
    }

    @BeforeEach
    void resetState() {
        channelRepository.deleteAll();
        PUSHPLUS_REQUESTS.clear();
        pushplusStatus = 200;
        pushplusBody = "{\"code\":200,\"msg\":\"ok\"}";
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @Test
    void createsChannelAndReturnsOnlyMaskedCredential() throws Exception {
        AppUserEntity owner = createUser();

        MvcResult result = mockMvc.perform(post("/api/v1/notification-channels")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PUSHPLUS","name":"我的微信","enabled":true,
                                 "config":{"token":"%s"}}
                                """.formatted(TOKEN)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PUSHPLUS"))
                .andExpect(jsonPath("$.name").value("我的微信"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.maskedConfig").value("****1234"))
                .andExpect(jsonPath("$.config").doesNotExist())
                .andReturn();

        long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        NotificationChannelEntity stored = channelRepository.findById(id).orElseThrow();
        assertThat(stored.getConfigCiphertext()).doesNotContain(TOKEN);
        assertThat(cipher.decrypt(stored.getConfigCiphertext())).contains(TOKEN);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(TOKEN);
    }

    @Test
    void listsOnlyTheCurrentUsersChannels() throws Exception {
        AppUserEntity alice = createUser();
        AppUserEntity bob = createUser();
        createChannel(alice, "alice-push", NotificationChannel.PUSHPLUS);
        createChannel(bob, "bob-mail", NotificationChannel.EMAIL);

        mockMvc.perform(get("/api/v1/notification-channels")
                        .with(user(alice.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].name").value("alice-push"));
    }

    @Test
    void enforcesThreeEnabledChannels() throws Exception {
        AppUserEntity owner = createUser();
        createChannel(owner, "one", NotificationChannel.PUSHPLUS);
        createChannel(owner, "two", NotificationChannel.PUSHPLUS);
        createChannel(owner, "three", NotificationChannel.PUSHPLUS);

        mockMvc.perform(post("/api/v1/notification-channels")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PUSHPLUS","name":"four","enabled":true,
                                 "config":{"token":"another-token"}}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHANNEL_LIMIT_EXCEEDED"));

        mockMvc.perform(post("/api/v1/notification-channels")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PUSHPLUS","name":"four","enabled":false,
                                 "config":{"token":"another-token"}}
                                """))
                .andExpect(status().isCreated());

        long fourthId = channelRepository.findByUserIdOrderByIdAsc(owner.getId()).stream()
                .filter(channel -> "four".equals(channel.getName()))
                .findFirst().orElseThrow().getId();
        mockMvc.perform(put("/api/v1/notification-channels/{id}", fourthId)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"four","enabled":true}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHANNEL_LIMIT_EXCEEDED"));
    }

    @Test
    void concurrentCreatesDoNotExceedThreeEnabledChannels() throws Exception {
        AppUserEntity owner = createUser();
        int attempts = 6;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = java.util.stream.IntStream.range(0, attempts)
                    .mapToObj(index -> executor.submit(() -> {
                        start.await(10, TimeUnit.SECONDS);
                        try {
                            channelService.create(owner.getId(),
                                    new NotificationChannelCreateRequest("PUSHPLUS",
                                            "concurrent-" + index, true,
                                            Map.of("token", "token-" + index)));
                            return true;
                        } catch (ChannelLimitExceededException ex) {
                            return false;
                        }
                    }))
                    .toList();
            start.countDown();

            int successes = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(NotificationChannelService.MAX_ENABLED_CHANNELS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(channelRepository.countByUserIdAndEnabledTrue(owner.getId()))
                .isEqualTo(NotificationChannelService.MAX_ENABLED_CHANNELS);
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/notification-channels"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/usage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void reportsCurrentUsageAndLimitsPerUser() throws Exception {
        AppUserEntity alice = createUser();
        AppUserEntity bob = createUser();
        createHeartbeatMonitor(alice);
        createHeartbeatMonitor(alice);
        createHeartbeatMonitor(bob);
        NotificationChannelEntity aliceChannel =
                createChannel(alice, "alice-push", NotificationChannel.PUSHPLUS);
        aliceChannel.setLastTestStatus(ChannelTestStatus.SUCCESS);
        channelRepository.save(aliceChannel);
        channelService.create(alice.getId(), new NotificationChannelCreateRequest(
                "PUSHPLUS", "alice-disabled", false, Map.of("token", "disabled-token")));
        createChannel(bob, "bob-push", NotificationChannel.PUSHPLUS);

        mockMvc.perform(get("/api/v1/usage")
                        .with(user(alice.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monitorCount").value(2))
                .andExpect(jsonPath("$.monitorLimit").value(MonitorService.MAX_MONITORS_PER_USER))
                .andExpect(jsonPath("$.enabledChannelCount").value(1))
                .andExpect(jsonPath("$.channelLimit")
                        .value(NotificationChannelService.MAX_ENABLED_CHANNELS))
                .andExpect(jsonPath("$.hasSuccessfulChannelTest").value(true));

        mockMvc.perform(get("/api/v1/usage")
                        .with(user(bob.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monitorCount").value(1))
                .andExpect(jsonPath("$.enabledChannelCount").value(1))
                .andExpect(jsonPath("$.hasSuccessfulChannelTest").value(false));
    }

    @Test
    void updatesAndDeletesOwnChannel() throws Exception {
        AppUserEntity owner = createUser();
        NotificationChannelEntity channel = createChannel(owner, "old", NotificationChannel.PUSHPLUS);

        mockMvc.perform(put("/api/v1/notification-channels/{id}", channel.getId())
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"renamed","enabled":false,
                                 "config":{"token":"rotated-token"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("renamed"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.maskedConfig").value("****oken"));

        NotificationChannelEntity updated =
                channelRepository.findById(channel.getId()).orElseThrow();
        assertThat(cipher.decrypt(updated.getConfigCiphertext())).contains("rotated-token");

        mockMvc.perform(delete("/api/v1/notification-channels/{id}", channel.getId())
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(channelRepository.findById(channel.getId())).isEmpty();
    }

    @Test
    void testSendUsesPushPlusAndRecordsSuccess() throws Exception {
        AppUserEntity owner = createUser();
        NotificationChannelEntity channel = createChannel(owner, "push", NotificationChannel.PUSHPLUS);

        mockMvc.perform(post("/api/v1/notification-channels/{id}/test", channel.getId())
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        assertThat(PUSHPLUS_REQUESTS).hasSize(1);
        assertThat(PUSHPLUS_REQUESTS.getFirst())
                .contains("\"token\":\"" + TOKEN + "\"")
                .contains("通知测试");
        NotificationChannelEntity updated =
                channelRepository.findById(channel.getId()).orElseThrow();
        assertThat(updated.getLastTestStatus()).isEqualTo(ChannelTestStatus.SUCCESS);
        assertThat(updated.getLastTestAt()).isNotNull();
        assertThat(updated.getLastTestError()).isNull();
    }

    @Test
    void testSendFailureIsVisibleWithoutLeakingTheToken() throws Exception {
        AppUserEntity owner = createUser();
        NotificationChannelEntity channel = createChannel(owner, "push", NotificationChannel.PUSHPLUS);
        pushplusStatus = 500;

        mockMvc.perform(post("/api/v1/notification-channels/{id}/test", channel.getId())
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        NotificationChannelEntity updated =
                channelRepository.findById(channel.getId()).orElseThrow();
        assertThat(updated.getLastTestStatus()).isEqualTo(ChannelTestStatus.FAILED);
        assertThat(updated.getLastTestError())
                .contains("HTTP 500")
                .doesNotContain(TOKEN);
    }

    @Test
    void emailTestReportsMissingGlobalSmtp() throws Exception {
        AppUserEntity owner = createUser();
        NotificationChannelEntity channel = createChannel(owner, "mail", NotificationChannel.EMAIL);

        mockMvc.perform(post("/api/v1/notification-channels/{id}/test", channel.getId())
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value("SMTP is not configured"));
    }

    @Test
    void crossUserAccessReturnsNotFound() throws Exception {
        AppUserEntity alice = createUser();
        AppUserEntity bob = createUser();
        NotificationChannelEntity channel = createChannel(alice, "private", NotificationChannel.PUSHPLUS);

        mockMvc.perform(put("/api/v1/notification-channels/{id}", channel.getId())
                        .with(user(bob.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"hijack","enabled":true}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHANNEL_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/notification-channels/{id}/test", channel.getId())
                        .with(user(bob.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHANNEL_NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/notification-channels/{id}", channel.getId())
                        .with(user(bob.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHANNEL_NOT_FOUND"));
    }

    @Test
    void rejectsInvalidConfigAndMissingCsrf() throws Exception {
        AppUserEntity owner = createUser();

        mockMvc.perform(post("/api/v1/notification-channels")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PUSHPLUS","name":"bad","config":{}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/notification-channels")
                        .with(user(owner.getUsername()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PUSHPLUS","name":"no-csrf","config":{"token":"abc"}}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void testSendIsRateLimitedPerChannel() throws Exception {
        AppUserEntity owner = createUser();
        NotificationChannelEntity channel = createChannel(owner, "limited", NotificationChannel.PUSHPLUS);
        for (int i = 0; i < NotificationRateLimiter.PER_MINUTE; i++) {
            mockMvc.perform(post("/api/v1/notification-channels/{id}/test", channel.getId())
                            .with(user(owner.getUsername()).roles("USER"))
                            .with(csrf()))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/notification-channels/{id}/test", channel.getId())
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    private NotificationChannelEntity createChannel(AppUserEntity owner, String name,
                                                    NotificationChannel type) {
        NotificationChannelEntity channel = new NotificationChannelEntity();
        channel.setUserId(owner.getId());
        channel.setType(type);
        channel.setName(name);
        channel.setEnabled(true);
        channel.setConfigCiphertext(cipher.encrypt(switch (type) {
            case PUSHPLUS -> "{\"token\":\"" + TOKEN + "\"}";
            case XIZHI -> "{\"key\":\"xizhi-key\"}";
            case EMAIL -> "{\"email\":\"owner@example.com\"}";
            default -> throw new IllegalArgumentException();
        }));
        Instant now = Instant.now();
        channel.setCreatedAt(now);
        channel.setUpdatedAt(now);
        return channelRepository.save(channel);
    }

    private void createHeartbeatMonitor(AppUserEntity owner) {
        monitorService.create(new MonitorConfigInput(
                "usage-heartbeat", null, null, 300, null, null, null, null, true,
                null, null, "HEARTBEAT", 60), owner.getId());
    }

    private AppUserEntity createUser() {
        AppUserEntity user = new AppUserEntity();
        user.setUsername("channel-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash(
                "{bcrypt}$2a$12$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234");
        user.setRole(AppRole.USER);
        user.setStatus(AccountStatus.ACTIVE);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user);
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/send", exchange -> {
                PUSHPLUS_REQUESTS.add(new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));
                respond(exchange, pushplusStatus, pushplusBody);
            });
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        } catch (IOException ignored) {
            // Test asserts the recorded outcome.
        }
    }
}
