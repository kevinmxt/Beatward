package dev.beatward.notification;

import dev.beatward.auth.AppRole;
import dev.beatward.auth.AppUserEntity;
import dev.beatward.auth.AppUserRepository;
import dev.beatward.incident.IncidentEntity;
import dev.beatward.incident.IncidentRepository;
import dev.beatward.monitor.MonitorEntity;
import dev.beatward.monitor.MonitorRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

@Service
public class NotificationOutbox {

    private final NotificationDeliveryRepository deliveryRepository;
    private final IncidentRepository incidentRepository;
    private final MonitorRepository monitorRepository;
    private final AppUserRepository userRepository;
    private final NotificationChannelRepository channelRepository;
    private final TelegramProperties telegramProperties;
    private final Clock clock;

    public NotificationOutbox(NotificationDeliveryRepository deliveryRepository,
                              IncidentRepository incidentRepository,
                              MonitorRepository monitorRepository,
                              AppUserRepository userRepository,
                              NotificationChannelRepository channelRepository,
                              TelegramProperties telegramProperties,
                              Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.incidentRepository = incidentRepository;
        this.monitorRepository = monitorRepository;
        this.userRepository = userRepository;
        this.channelRepository = channelRepository;
        this.telegramProperties = telegramProperties;
        this.clock = clock;
    }

    public void enqueue(Long incidentId, NotificationEventType eventType) {
        IncidentEntity incident = incidentRepository.findById(incidentId).orElse(null);
        if (incident == null) {
            return;
        }
        MonitorEntity monitor = monitorRepository.findById(incident.getMonitorId()).orElse(null);
        if (monitor == null) {
            return;
        }
        AppUserEntity owner = userRepository.findById(monitor.getUserId()).orElse(null);
        if (owner == null) {
            return;
        }

        List<NotificationChannelEntity> channels =
                channelRepository.findByUserIdAndEnabledTrueOrderByIdAsc(owner.getId());
        if (!channels.isEmpty()) {
            channels.forEach(channel -> enqueueChannel(incidentId, eventType, channel));
            return;
        }

        if (owner.getRole() == AppRole.ADMIN && telegramProperties.isConfigured()) {
            enqueueLegacyTelegram(incidentId, eventType);
        }
    }

    private void enqueueChannel(Long incidentId, NotificationEventType eventType,
                                NotificationChannelEntity channel) {
        NotificationDeliveryEntity delivery = newDelivery(incidentId, eventType);
        delivery.setChannelId(channel.getId());
        delivery.setChannel(channel.getType());
        deliveryRepository.save(delivery);
    }

    private void enqueueLegacyTelegram(Long incidentId, NotificationEventType eventType) {
        NotificationDeliveryEntity delivery = newDelivery(incidentId, eventType);
        delivery.setChannel(NotificationChannel.TELEGRAM);
        deliveryRepository.save(delivery);
    }

    private NotificationDeliveryEntity newDelivery(Long incidentId,
                                                   NotificationEventType eventType) {
        NotificationDeliveryEntity delivery = new NotificationDeliveryEntity();
        delivery.setIncidentId(incidentId);
        delivery.setEventType(eventType);
        delivery.setStatus(NotificationDeliveryStatus.PENDING);
        delivery.setAttemptCount(0);
        delivery.setNextAttemptAt(clock.instant());
        return delivery;
    }
}
