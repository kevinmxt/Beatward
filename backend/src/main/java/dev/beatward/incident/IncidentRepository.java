package dev.beatward.incident;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IncidentRepository extends JpaRepository<IncidentEntity, Long> {

    boolean existsByMonitorIdAndResolvedAtIsNull(Long monitorId);

    Optional<IncidentEntity> findFirstByMonitorIdAndResolvedAtIsNull(Long monitorId);

    @Query("""
            select i
            from IncidentEntity i, MonitorEntity m
            where i.monitorId = m.id
              and m.userId = :userId
              and (:status is null or i.status = :status)
              and (:monitorId is null or i.monitorId = :monitorId)
            """)
    Page<IncidentEntity> searchForUser(@Param("userId") Long userId,
                                       @Param("status") IncidentStatus status,
                                       @Param("monitorId") Long monitorId,
                                       Pageable pageable);

    @Query("""
            select i
            from IncidentEntity i, MonitorEntity m
            where i.id = :id
              and i.monitorId = m.id
              and m.userId = :userId
            """)
    Optional<IncidentEntity> findByIdForUser(@Param("id") Long id,
                                             @Param("userId") Long userId);
}
