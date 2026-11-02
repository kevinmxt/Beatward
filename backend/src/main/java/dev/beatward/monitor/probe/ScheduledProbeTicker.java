package dev.beatward.monitor.probe;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "beatward.probe", name = "scheduling-enabled",
        havingValue = "true", matchIfMissing = true)
class ScheduledProbeTicker {

    private final ProbeScheduler probeScheduler;

    ScheduledProbeTicker(ProbeScheduler probeScheduler) {
        this.probeScheduler = probeScheduler;
    }

    @Scheduled(fixedDelayString = "${beatward.probe.tick-interval:1s}", scheduler = "probeTickScheduler")
    void tick() {
        probeScheduler.tick();
    }
}
