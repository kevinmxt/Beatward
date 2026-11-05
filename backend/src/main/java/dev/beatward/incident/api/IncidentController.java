package dev.beatward.incident.api;

import dev.beatward.auth.CurrentUserProvider;
import dev.beatward.common.InvalidQueryException;
import dev.beatward.common.PageResponse;
import dev.beatward.incident.IncidentEntity;
import dev.beatward.incident.IncidentQueryService;
import dev.beatward.incident.IncidentStatus;
import dev.beatward.notification.NotificationDeliveryEntity;
import dev.beatward.notification.api.NotificationDeliveryResponse;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentQueryService incidentQueryService;
    private final CurrentUserProvider currentUserProvider;

    public IncidentController(IncidentQueryService incidentQueryService,
                              CurrentUserProvider currentUserProvider) {
        this.incidentQueryService = incidentQueryService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public PageResponse<IncidentResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(name = "monitor_id", required = false) Long monitorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long userId = currentUserProvider.requireId(authentication);
        IncidentStatus parsedStatus = parseStatus(status);
        Page<IncidentEntity> result = incidentQueryService.listForUser(
                userId, parsedStatus, monitorId, page, size);
        Map<Long, String> monitorNames = incidentQueryService.monitorNames(
                result.getContent().stream().map(IncidentEntity::getMonitorId).toList(), userId);
        List<IncidentResponse> items = result.getContent().stream()
                .map(incident -> IncidentResponse.from(incident,
                        monitorNames.get(incident.getMonitorId())))
                .toList();
        return new PageResponse<>(items, page, size, result.getTotalElements());
    }

    @GetMapping("/{id}")
    public IncidentDetailResponse get(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserProvider.requireId(authentication);
        IncidentEntity incident = incidentQueryService.getForUser(id, userId);
        List<NotificationDeliveryEntity> recent =
                incidentQueryService.recentDeliveries(incident.getId(), userId);
        Map<Long, String> channelNames = incidentQueryService.channelNames(
                recent.stream().map(NotificationDeliveryEntity::getChannelId)
                        .filter(java.util.Objects::nonNull)
                        .toList(), userId);
        List<NotificationDeliveryResponse> deliveries = recent.stream()
                .map(delivery -> NotificationDeliveryResponse.from(delivery,
                        delivery.getChannelId() == null
                                ? null
                                : channelNames.get(delivery.getChannelId())))
                .toList();
        return IncidentDetailResponse.from(incident,
                incidentQueryService.monitorName(incident.getMonitorId(), userId), deliveries);
    }

    private static IncidentStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return IncidentStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidQueryException("status must be OPEN or RESOLVED");
        }
    }
}
