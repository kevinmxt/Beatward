package dev.beatward.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDeliveryEntity, Long> {

    List<NotificationDeliveryEntity> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
            NotificationDeliveryStatus status, Instant now, Pageable pageable);

    @Query("""
            select d
            from NotificationDeliveryEntity d, IncidentEntity i, MonitorEntity m
            where d.incidentId = i.id
              and i.monitorId = m.id
              and d.incidentId = :incidentId
              and m.userId = :userId
            order by d.id desc
            """)
    List<NotificationDeliveryEntity> findRecentForUser(@Param("incidentId") Long incidentId,
                                                       @Param("userId") Long userId,
                                                       Pageable pageable);

    long countByStatus(NotificationDeliveryStatus status);
}
