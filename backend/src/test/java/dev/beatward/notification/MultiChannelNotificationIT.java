package dev.beatward.notification;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.beatward.auth.AccountStatus;
import dev.beatward.auth.AppRole;
import dev.beatward.auth.AppUserEntity;
import dev.beatward.auth.AppUserRepository;
import dev.beatward.incident.IncidentEntity;
import dev.beatward.incident.IncidentRepository;
import dev.beatward.incident.IncidentStatus;
import dev.beatward.monitor.CheckFailureType;
import dev.beatward.monitor.MonitorEntity;
import dev.beatward.monitor.MonitorRepository;
import dev.beatward.monitor.MonitorService;
import dev.beatward.monitor.config.MonitorConfigInput;
import dev.beatward.monitor.probe.CheckResultRepository;
import dev.beatward.monitor.probe.ProbeService;
import dev.beatward.monitor.target.TargetPolicy;
import org.apache.hc.client5.http.DnsResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "beatward.probe.scheduling-enabled=false",
        "beatward.notification.scheduling-enabled=false",
        "beatward.channel-secret-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Testcontainers
@Import(MultiChannelNotificationIT.TestBeans.class)
class MultiChannelNotificationIT {

    private static final HttpServer SERVER = startServer();
    private static final int PORT = SERVER.getAddress().getPort();
    private static final List<String> PUSHPLUS_REQUESTS = new CopyOnWriteArrayList<>();
    private static final List<String> XIZHI_REQUESTS = new CopyOnWriteArrayList<>();
    private static volatile boolean targetFailing;
    private static volatile int pushplusStatus = 200;
    private static volatile int xizhiStatus = 200;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    MonitorService monitorService;

    @Autowired
    ProbeService probeService;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    MonitorRepository monitorRepository;

    @Autowired
    CheckResultRepository checkResultRepository;

    @Autowired
    IncidentRepository incidentRepository;

    @Autowired
    NotificationDeliveryRepository deliveryRepository;

    @Autowired
    NotificationChannelRepository channelRepository;

    @Autowired
    ChannelSecretCipher cipher;

    @Autowired
    NotificationDispatcher dispatcher;

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        @Primary
        DnsResolver loopbackDnsResolver() {
            return new DnsResolver() {
                @Override
                public InetAddress[] resolve(String host) throws UnknownHostException {
                    return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
                }

                @Override
                public String resolveCanonicalHostname(String host) {
                    return host;
                }
            };
        }

        @Bean
        @Primary
        TargetPolicy permissiveTargetPolicy() {
            return target -> {
            };
        }
    }

    @DynamicPropertySource
    static void endpoints(DynamicPropertyRegistry registry) {
        registry.add("beatward.notification.endpoints.pushplus-api-base-url",
                () -> "http://127.0.0.1:" + PORT + "/push");
        registry.add("beatward.notification.endpoints.xizhi-api-base-url",
                () -> "http://127.0.0.1:" + PORT + "/xizhi");
    }

    @BeforeEach
    void resetState() {
        deliveryRepository.deleteAll();
        incidentRepository.deleteAll();
        checkResultRepository.deleteAll();
        monitorRepository.deleteAll();
        channelRepository.deleteAll();
        PUSHPLUS_REQUESTS.clear();
        XIZHI_REQUESTS.clear();
        targetFailing = false;
        pushplusStatus = 200;
        xizhiStatus = 200;
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @Test
    void enqueuesOneDeliveryPerEnabledChannelAndDispatchesAll() {
        AppUserEntity owner = createUser();
        NotificationChannelEntity push = createChannel(owner, "push", NotificationChannel.PUSHPLUS, true);
        NotificationChannelEntity xizhi = createChannel(owner, "xizhi", NotificationChannel.XIZHI, true);
        createChannel(owner, "disabled", NotificationChannel.PUSHPLUS, false);
        MonitorEntity monitor = createMonitor(owner);

        openIncident(monitor);

        List<NotificationDeliveryEntity> deliveries = deliveryRepository.findAll();
        assertThat(deliveries).hasSize(2);
        assertThat(deliveries).extracting(NotificationDeliveryEntity::getChannelId)
                .containsExactlyInAnyOrder(push.getId(), xizhi.getId());
        assertThat(deliveries).allSatisfy(delivery ->
                assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING));

        dispatcher.dispatchDue();

        assertThat(deliveryRepository.findAll())
                .allSatisfy(delivery ->
                        assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryStatus.SENT));
        assertThat(PUSHPLUS_REQUESTS).hasSize(1);
        assertThat(XIZHI_REQUESTS).hasSize(1);
        assertThat(PUSHPLUS_REQUESTS.getFirst()).contains("故障告警");
        assertThat(java.net.URLDecoder.decode(XIZHI_REQUESTS.getFirst(), StandardCharsets.UTF_8))
                .contains("故障告警");
    }

    @Test
    void oneChannelFailureDoesNotBlockTheOther() {
        AppUserEntity owner = createUser();
        createChannel(owner, "push", NotificationChannel.PUSHPLUS, true);
        createChannel(owner, "xizhi", NotificationChannel.XIZHI, true);
        MonitorEntity monitor = createMonitor(owner);
        openIncident(monitor);
        pushplusStatus = 500;

        dispatcher.dispatchDue();

        List<NotificationDeliveryEntity> deliveries = deliveryRepository.findAll();
        NotificationDeliveryEntity push = deliveries.stream()
                .filter(delivery -> delivery.getChannel() == NotificationChannel.PUSHPLUS)
                .findFirst().orElseThrow();
        NotificationDeliveryEntity xizhi = deliveries.stream()
                .filter(delivery -> delivery.getChannel() == NotificationChannel.XIZHI)
                .findFirst().orElseThrow();
        assertThat(push.getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(push.getAttemptCount()).isEqualTo(1);
        assertThat(push.getLastErrorSummary()).contains("HTTP 500");
        assertThat(xizhi.getStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(incidentRepository.findAll().getFirst().getStatus()).isEqualTo(IncidentStatus.OPEN);
    }

    @Test
    void deletedChannelIsSkippedWithoutStoppingTheTick() throws Exception {
        AppUserEntity owner = createUser();
        NotificationChannelEntity channel = createChannel(
                owner, "push", NotificationChannel.PUSHPLUS, true);
        MonitorEntity monitor = createMonitor(owner);
        openIncident(monitor);
        channelRepository.deleteById(channel.getId());

        dispatcher.dispatchDue();

        NotificationDeliveryEntity delivery = deliveryRepository.findAll().getFirst();
        assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(delivery.getLastErrorSummary()).contains("Channel is unavailable");
        assertThat(PUSHPLUS_REQUESTS).isEmpty();
    }

    @Test
    void deletingMultipleChannelsOfTheSameTypeIsSafe() {
        AppUserEntity owner = createUser();
        NotificationChannelEntity first = createChannel(
                owner, "push-one", NotificationChannel.PUSHPLUS, true);
        NotificationChannelEntity second = createChannel(
                owner, "push-two", NotificationChannel.PUSHPLUS, true);
        MonitorEntity monitor = createMonitor(owner);

        IncidentEntity incident = new IncidentEntity();
        incident.setMonitorId(monitor.getId());
        incident.setStartedAt(Instant.now());
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(Instant.now().plusSeconds(30));
        incident.setFailureType(CheckFailureType.CONNECTION_FAILED);
        incident.setSummary("seeded");
        IncidentEntity saved = incidentRepository.save(incident);
        for (NotificationChannelEntity channel : List.of(first, second)) {
            NotificationDeliveryEntity delivery = new NotificationDeliveryEntity();
            delivery.setIncidentId(saved.getId());
            delivery.setChannelId(channel.getId());
            delivery.setChannel(channel.getType());
            delivery.setEventType(NotificationEventType.INCIDENT_OPENED);
            delivery.setStatus(NotificationDeliveryStatus.PENDING);
            delivery.setNextAttemptAt(Instant.now().minusSeconds(60));
            deliveryRepository.save(delivery);
        }

        channelRepository.deleteById(first.getId());
        channelRepository.deleteById(second.getId());
        channelRepository.flush();

        assertThat(deliveryRepository.findAll())
                .allSatisfy(delivery -> assertThat(delivery.getChannelId()).isNull());
        dispatcher.dispatchDue();
        assertThat(deliveryRepository.findAll())
                .allSatisfy(delivery -> {
                    assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
                    assertThat(delivery.getLastErrorSummary()).contains("Channel is unavailable");
                });
        assertThat(PUSHPLUS_REQUESTS).isEmpty();
    }

    @Test
    void rateLimitedDeliveriesAreMarkedFailed() {
        AppUserEntity owner = createUser();
        NotificationChannelEntity channel = createChannel(
                owner, "push", NotificationChannel.PUSHPLUS, true);
        MonitorEntity monitor = createMonitor(owner);
        for (int i = 0; i < NotificationRateLimiter.PER_MINUTE + 1; i++) {
            seedDelivery(monitor, channel, i);
        }

        dispatcher.dispatchDue();

        List<NotificationDeliveryEntity> deliveries = deliveryRepository.findAll();
        assertThat(deliveries).filteredOn(delivery ->
                        delivery.getStatus() == NotificationDeliveryStatus.SENT)
                .hasSize(NotificationRateLimiter.PER_MINUTE);
        assertThat(deliveries).filteredOn(delivery ->
                        delivery.getStatus() == NotificationDeliveryStatus.FAILED)
                .hasSize(1)
                .allSatisfy(delivery ->
                        assertThat(delivery.getLastErrorSummary()).contains("Rate limit exceeded"));
        assertThat(PUSHPLUS_REQUESTS).hasSize(NotificationRateLimiter.PER_MINUTE);
    }

    @Test
    void doesNotUseAnotherUsersChannels() {
        AppUserEntity alice = createUser();
        AppUserEntity bob = createUser();
        createChannel(alice, "alice-push", NotificationChannel.PUSHPLUS, true);
        MonitorEntity bobMonitor = createMonitor(bob);

        openIncident(bobMonitor);

        assertThat(deliveryRepository.findAll()).isEmpty();
        assertThat(PUSHPLUS_REQUESTS).isEmpty();
    }

    private AppUserEntity createUser() {
        AppUserEntity user = new AppUserEntity();
        user.setUsername("notify-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash(
                "{bcrypt}$2a$12$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234");
        user.setRole(AppRole.USER);
        user.setStatus(AccountStatus.ACTIVE);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user);
    }

    private NotificationChannelEntity createChannel(AppUserEntity owner, String name,
                                                    NotificationChannel type, boolean enabled) {
        NotificationChannelEntity channel = new NotificationChannelEntity();
        channel.setUserId(owner.getId());
        channel.setType(type);
        channel.setName(name);
        channel.setEnabled(enabled);
        channel.setConfigCiphertext(cipher.encrypt(switch (type) {
            case PUSHPLUS -> "{\"token\":\"push-token\"}";
            case XIZHI -> "{\"key\":\"xizhi-key\"}";
            case EMAIL -> "{\"email\":\"owner@example.com\"}";
            default -> throw new IllegalArgumentException();
        }));
        Instant now = Instant.now();
        channel.setCreatedAt(now);
        channel.setUpdatedAt(now);
        return channelRepository.save(channel);
    }

    private MonitorEntity createMonitor(AppUserEntity owner) {
        return monitorService.create(new MonitorConfigInput(
                "notification target",
                "http://127.0.0.1:" + PORT + "/target",
                "GET",
                60,
                3000,
                10000,
                200,
                299,
                true,
                null,
                null), owner.getId()).monitor();
    }

    private void openIncident(MonitorEntity monitor) {
        targetFailing = true;
        probeService.probe(monitor.getId());
        probeService.probe(monitor.getId());
        probeService.probe(monitor.getId());
    }

    private void seedDelivery(MonitorEntity monitor, NotificationChannelEntity channel, int index) {
        IncidentEntity incident = new IncidentEntity();
        incident.setMonitorId(monitor.getId());
        incident.setStartedAt(Instant.now().plusSeconds(index));
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(Instant.now().plusSeconds(index + 30));
        incident.setFailureType(CheckFailureType.CONNECTION_FAILED);
        incident.setSummary("seeded " + index);
        IncidentEntity saved = incidentRepository.save(incident);

        NotificationDeliveryEntity delivery = new NotificationDeliveryEntity();
        delivery.setIncidentId(saved.getId());
        delivery.setChannelId(channel.getId());
        delivery.setChannel(channel.getType());
        delivery.setEventType(NotificationEventType.INCIDENT_OPENED);
        delivery.setStatus(NotificationDeliveryStatus.PENDING);
        delivery.setNextAttemptAt(Instant.now().minusSeconds(60));
        deliveryRepository.save(delivery);
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/target", exchange ->
                    respond(exchange, targetFailing ? 503 : 200, targetFailing ? "down" : "ok"));
            server.createContext("/push/send", exchange -> {
                PUSHPLUS_REQUESTS.add(new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));
                respond(exchange, pushplusStatus, "{\"code\":200,\"msg\":\"ok\"}");
            });
            server.createContext("/xizhi", exchange -> {
                XIZHI_REQUESTS.add(new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));
                respond(exchange, xizhiStatus, "ok");
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
            // The client may close the connection early.
        }
    }
}
