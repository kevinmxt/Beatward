package dev.beatward.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {

    Optional<AppUserEntity> findByUsername(String username);

    /**
     * Serializes per-user writes that enforce quotas or other account-level invariants.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUserEntity u where u.id = :id")
    Optional<AppUserEntity> findByIdForUpdate(@Param("id") Long id);

    boolean existsByUsername(String username);

    @Modifying
    @Transactional
    @Query("update AppUserEntity u set u.lastLoginAt = :now where u.username = :username")
    int updateLastLoginAt(@Param("username") String username, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update AppUserEntity u set u.passwordHash = :hash, u.updatedAt = :now where u.id = :id")
    int updatePassword(@Param("id") Long id, @Param("hash") String passwordHash,
                       @Param("now") Instant now);

    default Optional<AppUserEntity> findByUsernameInput(String rawUsername) {
        if (!UsernameRules.isValid(rawUsername)) {
            return Optional.empty();
        }
        return findByUsername(UsernameRules.normalize(rawUsername));
    }
}
