package dev.beatward.monitor.probe;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CheckResultRepository extends JpaRepository<CheckResultEntity, Long> {

    @Query("""
            select r
            from CheckResultEntity r, MonitorEntity m
            where r.monitorId = m.id
              and r.monitorId = :monitorId
              and m.userId = :userId
            """)
    Page<CheckResultEntity> findByMonitorIdAndUserId(@Param("monitorId") Long monitorId,
                                                     @Param("userId") Long userId,
                                                     Pageable pageable);

    @Query(value = """
            SELECT DISTINCT ON (check_result.monitor_id)
                   check_result.monitor_id AS "monitorId",
                   check_result.duration_ms AS "durationMs"
            FROM check_result
            JOIN monitor ON monitor.id = check_result.monitor_id
            WHERE check_result.monitor_id IN (:monitorIds)
              AND monitor.user_id = :userId
            ORDER BY check_result.monitor_id, check_result.started_at DESC, check_result.id DESC
            """, nativeQuery = true)
    List<LatestCheckDuration> findLatestDurations(@Param("monitorIds") Collection<Long> monitorIds,
                                                  @Param("userId") Long userId);

    interface LatestCheckDuration {
        Long getMonitorId();

        long getDurationMs();
    }
}
