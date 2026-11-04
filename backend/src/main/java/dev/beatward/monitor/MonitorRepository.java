package dev.beatward.monitor;

import dev.beatward.auth.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MonitorRepository extends JpaRepository<MonitorEntity, Long> {

    @Query("""
            select m.id
            from MonitorEntity m, AppUserEntity u
            where m.userId = u.id
              and u.status = :accountStatus
              and m.enabled = true
              and m.type = dev.beatward.monitor.MonitorType.HTTP
              and m.nextCheckAt <= :now
            order by m.nextCheckAt asc, m.id asc
            """)
    List<Long> findDueMonitorIds(@Param("now") Instant now,
                                 @Param("accountStatus") AccountStatus accountStatus,
                                 Pageable pageable);

    @Query("""
            select m.id
            from MonitorEntity m, AppUserEntity u
            where m.userId = u.id
              and u.status = :accountStatus
              and m.enabled = true
              and m.type = dev.beatward.monitor.MonitorType.HEARTBEAT
              and m.nextCheckAt <= :now
            order by m.nextCheckAt asc, m.id asc
            """)
    List<Long> findDueHeartbeatIds(@Param("now") Instant now,
                                   @Param("accountStatus") AccountStatus accountStatus,
                                   Pageable pageable);

    Page<MonitorEntity> findByUserId(Long userId, Pageable pageable);

    Optional<MonitorEntity> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MonitorEntity m where m.id = :id")
    Optional<MonitorEntity> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MonitorEntity m where m.id = :id and m.userId = :userId")
    Optional<MonitorEntity> findByIdAndUserIdForUpdate(@Param("id") Long id,
                                                       @Param("userId") Long userId);
}
