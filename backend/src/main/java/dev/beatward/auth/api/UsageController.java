package dev.beatward.auth.api;

import dev.beatward.auth.CurrentUserProvider;
import dev.beatward.monitor.MonitorRepository;
import dev.beatward.monitor.MonitorService;
import dev.beatward.notification.ChannelTestStatus;
import dev.beatward.notification.NotificationChannelRepository;
import dev.beatward.notification.NotificationChannelService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usage")
public class UsageController {

    private final MonitorRepository monitorRepository;
    private final NotificationChannelRepository channelRepository;
    private final CurrentUserProvider currentUserProvider;

    public UsageController(MonitorRepository monitorRepository,
                           NotificationChannelRepository channelRepository,
                           CurrentUserProvider currentUserProvider) {
        this.monitorRepository = monitorRepository;
        this.channelRepository = channelRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public UsageResponse usage(Authentication authentication) {
        Long userId = currentUserProvider.requireId(authentication);
        return new UsageResponse(
                monitorRepository.countByUserId(userId),
                MonitorService.MAX_MONITORS_PER_USER,
                channelRepository.countByUserIdAndEnabledTrue(userId),
                NotificationChannelService.MAX_ENABLED_CHANNELS,
                channelRepository.existsByUserIdAndEnabledTrueAndLastTestStatus(
                        userId, ChannelTestStatus.SUCCESS));
    }

    public record UsageResponse(long monitorCount, int monitorLimit, long enabledChannelCount,
                                int channelLimit, boolean hasSuccessfulChannelTest) {
    }
}
