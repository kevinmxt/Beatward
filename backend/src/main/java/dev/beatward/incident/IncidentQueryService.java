package dev.beatward.incident;

import dev.beatward.common.PageQueries;
import dev.beatward.monitor.MonitorEntity;
import dev.beatward.monitor.MonitorRepository;
import dev.beatward.notification.NotificationChannelEntity;
import dev.beatward.notification.NotificationChannelRepository;
import dev.beatward.notification.NotificationDeliveryEntity;
import dev.beatward.notification.NotificationDeliveryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class IncidentQueryService {

    private final IncidentRepository incidentRepository;
    private final MonitorRepository monitorRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final NotificationChannelRepository notificationChannelRepository;

    public IncidentQueryService(IncidentRepository incidentRepository,
                                MonitorRepository monitorRepository,
                                NotificationDeliveryRepository notificationDeliveryRepository,
                                NotificationChannelRepository notificationChannelRepository) {
        this.incidentRepository = incidentRepository;
        this.monitorRepository = monitorRepository;
        this.notificationDeliveryRepository = notificationDeliveryRepository;
        this.notificationChannelRepository = notificationChannelRepository;
    }

    @Transactional(readOnly = true)
    public Page<IncidentEntity> listForUser(Long userId, IncidentStatus status, Long monitorId,
                                            int page, int size) {
        PageRequest pageRequest = PageQueries.of(page, size,
                Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id")));
        return incidentRepository.searchForUser(userId, status, monitorId, pageRequest);
    }

    @Transactional(readOnly = true)
    public IncidentEntity getForUser(Long id, Long userId) {
        return incidentRepository.findByIdForUser(id, userId)
                .orElseThrow(() -> new IncidentNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Map<Long, String> monitorNames(Collection<Long> monitorIds, Long userId) {
        if (monitorIds.isEmpty()) {
            return Map.of();
        }
        return monitorRepository.findAllByIdInAndUserId(monitorIds, userId).stream()
                .collect(Collectors.toMap(MonitorEntity::getId, MonitorEntity::getName));
    }

    @Transactional(readOnly = true)
    public String monitorName(Long monitorId, Long userId) {
        return monitorRepository.findByIdAndUserId(monitorId, userId)
                .map(MonitorEntity::getName)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<NotificationDeliveryEntity> recentDeliveries(Long incidentId, Long userId) {
        return notificationDeliveryRepository.findRecentForUser(
                incidentId, userId, PageRequest.of(0, 5));
    }

    @Transactional(readOnly = true)
    public Map<Long, String> channelNames(Collection<Long> channelIds, Long userId) {
        if (channelIds.isEmpty()) {
            return Map.of();
        }
        return notificationChannelRepository.findByIdIn(channelIds).stream()
                .filter(channel -> channel.getUserId().equals(userId))
                .collect(Collectors.toMap(NotificationChannelEntity::getId,
                        NotificationChannelEntity::getName));
    }
}
