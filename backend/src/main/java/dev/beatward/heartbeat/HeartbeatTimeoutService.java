package dev.beatward.heartbeat;

import dev.beatward.auth.AccountActivity;
import dev.beatward.incident.IncidentService;
import dev.beatward.monitor.CheckFailureType;
import dev.beatward.monitor.MonitorEntity;
import dev.beatward.monitor.MonitorRepository;
import dev.beatward.monitor.MonitorStatus;
import dev.beatward.monitor.MonitorType;
import dev.beatward.monitor.probe.CheckResultEntity;
import dev.beatward.monitor.probe.CheckResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class HeartbeatTimeoutService {

    private final MonitorRepository monitorRepository;
    private final IncidentService incidentService;
    private final CheckResultRepository checkResultRepository;
    private final AccountActivity accountActivity;
    private final Clock clock;

    public HeartbeatTimeoutService(MonitorRepository monitorRepository,
                                   IncidentService incidentService,
                                   CheckResultRepository checkResultRepository,
                                   AccountActivity accountActivity,
                                   Clock clock) {
        this.monitorRepository = monitorRepository;
        this.incidentService = incidentService;
        this.checkResultRepository = checkResultRepository;
        this.accountActivity = accountActivity;
        this.clock = clock;
    }

    @Transactional
    public void checkTimeout(Long monitorId) {
        MonitorEntity monitor = monitorRepository.findByIdForUpdate(monitorId).orElse(null);
        if (monitor == null || monitor.getType() != MonitorType.HEARTBEAT || !monitor.isEnabled()
                || !accountActivity.isActive(monitor.getUserId())) {
            return;
        }

        Instant now = clock.instant();
        Instant deadline = monitor.getNextCheckAt();
        if (now.isBefore(deadline)) {
            return;
        }

        monitor.setNextCheckAt(now.plusSeconds(
                monitor.getIntervalSeconds() + monitor.getHeartbeatGraceSeconds()));
        if (incidentService.openHeartbeatTimeout(monitor, deadline)) {
            monitor.setCurrentStatus(MonitorStatus.DOWN);
            monitor.setLastCheckedAt(now);
            monitor.setUpdatedAt(now);
            checkResultRepository.save(timeoutResult(monitorId, now));
        }
        monitorRepository.save(monitor);
    }

    private static CheckResultEntity timeoutResult(Long monitorId, Instant detectedAt) {
        CheckResultEntity result = new CheckResultEntity();
        result.setMonitorId(monitorId);
        result.setStartedAt(detectedAt);
        result.setFinishedAt(detectedAt);
        result.setDurationMs(0);
        result.setSuccess(false);
        result.setFailureType(CheckFailureType.HEARTBEAT_TIMEOUT);
        return result;
    }
}
