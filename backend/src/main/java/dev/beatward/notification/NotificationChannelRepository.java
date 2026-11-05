package dev.beatward.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationChannelRepository
        extends JpaRepository<NotificationChannelEntity, Long> {

    List<NotificationChannelEntity> findByUserIdOrderByIdAsc(Long userId);

    Page<NotificationChannelEntity> findByUserId(Long userId, Pageable pageable);

    Optional<NotificationChannelEntity> findByIdAndUserId(Long id, Long userId);

    List<NotificationChannelEntity> findByUserIdAndEnabledTrueOrderByIdAsc(Long userId);

    long countByUserIdAndEnabledTrue(Long userId);

    long countByUserIdAndEnabledTrueAndIdNot(Long userId, Long id);

    boolean existsByUserIdAndEnabledTrueAndLastTestStatus(Long userId, ChannelTestStatus status);

    List<NotificationChannelEntity> findByIdIn(Collection<Long> ids);

    boolean existsByUserIdAndTypeAndName(Long userId, NotificationChannel type, String name);

    boolean existsByUserIdAndTypeAndNameAndIdNot(Long userId, NotificationChannel type,
                                                 String name, Long id);
}
