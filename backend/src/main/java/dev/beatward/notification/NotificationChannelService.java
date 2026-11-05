package dev.beatward.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.beatward.auth.AppUserRepository;
import dev.beatward.auth.AuthValidationException;
import dev.beatward.auth.UserNotFoundException;
import dev.beatward.common.PageQueries;
import dev.beatward.notification.api.NotificationChannelCreateRequest;
import dev.beatward.notification.api.NotificationChannelResponse;
import dev.beatward.notification.api.NotificationChannelTestResponse;
import dev.beatward.notification.api.NotificationChannelUpdateRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationChannelService {

    public static final int MAX_ENABLED_CHANNELS = 3;
    static final int NAME_MAX_LENGTH = 100;
    static final int ERROR_MAX_LENGTH = 500;
    private static final String TEST_TITLE = "Beatward 通知测试";
    private static final String TEST_CONTENT = "这是一条来自 Beatward 的测试通知。";

    private final NotificationChannelRepository channelRepository;
    private final AppUserRepository userRepository;
    private final ChannelSecretCipher cipher;
    private final ChannelConfigValidator configValidator;
    private final NotificationRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<NotificationChannel, UserChannelSender> senders;

    public NotificationChannelService(NotificationChannelRepository channelRepository,
                                      AppUserRepository userRepository,
                                      ChannelSecretCipher cipher,
                                      ChannelConfigValidator configValidator,
                                      NotificationRateLimiter rateLimiter,
                                      ObjectMapper objectMapper,
                                      Clock clock,
                                      List<UserChannelSender> senders) {
        this.channelRepository = channelRepository;
        this.userRepository = userRepository;
        this.cipher = cipher;
        this.configValidator = configValidator;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.senders = new EnumMap<>(NotificationChannel.class);
        senders.forEach(sender -> this.senders.put(sender.type(), sender));
    }

    public Page<NotificationChannelEntity> list(Long userId, int page, int size) {
        return channelRepository.findByUserId(userId,
                PageQueries.of(page, size, Sort.by("id").ascending()));
    }

    @Transactional
    public NotificationChannelEntity create(Long userId, NotificationChannelCreateRequest request) {
        NotificationChannel type = configValidator.parseType(request == null ? null : request.type());
        String name = validName(request == null ? null : request.name());
        boolean enabled = request == null || request.enabled() == null || request.enabled();
        Map<String, String> config = configValidator.normalize(type, request == null
                ? null : request.config());
        if (channelRepository.existsByUserIdAndTypeAndName(userId, type, name)) {
            throw new AuthValidationException("channel name already exists");
        }

        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (enabled && channelRepository.countByUserIdAndEnabledTrue(userId)
                >= MAX_ENABLED_CHANNELS) {
            throw new ChannelLimitExceededException();
        }

        Instant now = clock.instant();
        NotificationChannelEntity channel = new NotificationChannelEntity();
        channel.setUserId(userId);
        channel.setType(type);
        channel.setName(name);
        channel.setConfigCiphertext(cipher.encrypt(toJson(config)));
        channel.setEnabled(enabled);
        channel.setCreatedAt(now);
        channel.setUpdatedAt(now);
        try {
            return channelRepository.saveAndFlush(channel);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthValidationException("channel name already exists");
        }
    }

    @Transactional
    public NotificationChannelEntity update(Long userId, Long id,
                                           NotificationChannelUpdateRequest request) {
        NotificationChannelEntity channel = require(userId, id);
        String name = request == null || request.name() == null
                ? channel.getName()
                : validName(request.name());
        if (channelRepository.existsByUserIdAndTypeAndNameAndIdNot(
                userId, channel.getType(), name, id)) {
            throw new AuthValidationException("channel name already exists");
        }
        boolean enabled = request == null || request.enabled() == null
                ? channel.isEnabled()
                : request.enabled();

        if (enabled && !channel.isEnabled()) {
            userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new UserNotFoundException(userId));
            if (channelRepository.countByUserIdAndEnabledTrueAndIdNot(userId, id)
                    >= MAX_ENABLED_CHANNELS) {
                throw new ChannelLimitExceededException();
            }
        }

        channel.setName(name);
        channel.setEnabled(enabled);
        if (request != null && request.config() != null && !request.config().isEmpty()) {
            Map<String, String> config = configValidator.normalize(channel.getType(),
                    request.config());
            channel.setConfigCiphertext(cipher.encrypt(toJson(config)));
        }
        channel.setUpdatedAt(clock.instant());
        try {
            return channelRepository.saveAndFlush(channel);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthValidationException("channel name already exists");
        }
    }

    @Transactional
    public void delete(Long userId, Long id) {
        channelRepository.delete(require(userId, id));
    }

    public NotificationChannelTestResponse test(Long userId, Long id) {
        NotificationChannelEntity channel = require(userId, id);
        rateLimiter.tryAcquire(userId, String.valueOf(id)).ifPresent(retryAfter -> {
            throw new NotificationRateLimitedException(retryAfter);
        });

        Instant testedAt = clock.instant();
        ChannelTestStatus status;
        String message = null;
        try {
            UserChannelSender sender = senderFor(channel.getType());
            sender.send(credentials(channel), TEST_TITLE, TEST_CONTENT);
            status = ChannelTestStatus.SUCCESS;
        } catch (NotificationSendException failure) {
            status = ChannelTestStatus.FAILED;
            message = truncate(sanitize(channel, failure.getMessage()));
        }

        channel.setLastTestAt(testedAt);
        channel.setLastTestStatus(status);
        channel.setLastTestError(message);
        channelRepository.save(channel);
        return new NotificationChannelTestResponse(status, message, testedAt);
    }

    public NotificationChannelResponse toResponse(NotificationChannelEntity channel) {
        return NotificationChannelResponse.from(channel, mask(channel));
    }

    public Map<String, String> credentials(NotificationChannelEntity channel) {
        try {
            return objectMapper.readValue(cipher.decrypt(channel.getConfigCiphertext()),
                    new TypeReference<Map<String, String>>() {
                    });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored channel config is invalid", ex);
        }
    }

    public String mask(NotificationChannelEntity channel) {
        return configValidator.mask(channel.getType(), credentials(channel));
    }

    public UserChannelSender senderFor(NotificationChannel type) {
        UserChannelSender sender = senders.get(type);
        if (sender == null) {
            throw new NotificationSendException("Unsupported channel type", false);
        }
        return sender;
    }

    public String sanitize(NotificationChannelEntity channel, String message) {
        return senderFor(channel.getType()).sanitize(message);
    }

    private NotificationChannelEntity require(Long userId, Long id) {
        return channelRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ChannelNotFoundException(id));
    }

    private String toJson(Map<String, String> config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize channel config", ex);
        }
    }

    private static String validName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new AuthValidationException("name is required");
        }
        String name = rawName.strip();
        if (name.length() > NAME_MAX_LENGTH) {
            throw new AuthValidationException("name is too long");
        }
        return name;
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= ERROR_MAX_LENGTH) {
            return message;
        }
        return message.substring(0, ERROR_MAX_LENGTH);
    }
}
