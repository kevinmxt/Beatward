package dev.beatward.cleanup;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "beatward.cleanup", name = "scheduling-enabled",
        havingValue = "true", matchIfMissing = true)
class ScheduledCleanupTicker {

    private final CleanupService cleanupService;

    ScheduledCleanupTicker(CleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(fixedDelayString = "${beatward.cleanup.tick-interval:1h}",
            scheduler = "cleanupTickScheduler")
    void tick() {
        cleanupService.tick();
    }
}
