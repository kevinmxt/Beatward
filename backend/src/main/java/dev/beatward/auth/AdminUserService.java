package dev.beatward.auth;

import dev.beatward.common.PageQueries;
import dev.beatward.monitor.MonitorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private final AppUserRepository userRepository;
    private final MonitorRepository monitorRepository;
    private final Clock clock;

    public AdminUserService(AppUserRepository userRepository, MonitorRepository monitorRepository,
                            Clock clock) {
        this.userRepository = userRepository;
        this.monitorRepository = monitorRepository;
        this.clock = clock;
    }

    public Page<AppUserEntity> listUsers(int page, int size) {
        return userRepository.findAll(PageQueries.of(page, size, Sort.by("id").ascending()));
    }

    public Map<Long, Long> monitorCounts(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return monitorRepository.countByUserIds(userIds).stream()
                .collect(Collectors.toMap(MonitorRepository.UserMonitorCount::getUserId,
                        MonitorRepository.UserMonitorCount::getTotal));
    }

    public long monitorCount(Long userId) {
        return monitorCounts(List.of(userId)).getOrDefault(userId, 0L);
    }

    @Transactional
    public AppUserEntity changeStatus(Long userId, String rawStatus) {
        AccountStatus status = parseStatus(rawStatus);
        Instant now = clock.instant();
        AppRole role = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId))
                .getRole();

        if (status == AccountStatus.DISABLED) {
            // Locking every active admin serializes concurrent disables; only admin
            // targets can remove the last one, so regular users skip this lock.
            List<AppUserEntity> activeAdmins = role == AppRole.ADMIN
                    ? userRepository.lockActiveAdmins()
                    : List.of();
            AppUserEntity user = userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new UserNotFoundException(userId));
            boolean disablingActiveAdmin = user.getRole() == AppRole.ADMIN
                    && user.getStatus() == AccountStatus.ACTIVE;
            if (disablingActiveAdmin && activeAdmins.size() <= 1) {
                throw new LastActiveAdminException();
            }
            if (user.getStatus() != AccountStatus.DISABLED) {
                user.setStatus(AccountStatus.DISABLED);
                user.setDisabledAt(now);
                user.setUpdatedAt(now);
            }
            return userRepository.save(user);
        }

        AppUserEntity user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.setStatus(AccountStatus.ACTIVE);
        user.setDisabledAt(null);
        user.setUpdatedAt(now);
        return userRepository.save(user);
    }

    private static AccountStatus parseStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            throw new AuthValidationException("status is required");
        }
        try {
            return AccountStatus.valueOf(rawStatus.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new AuthValidationException("status must be ACTIVE or DISABLED");
        }
    }
}
