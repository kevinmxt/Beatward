package dev.beatward.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface InviteCodeRepository extends JpaRepository<InviteCodeEntity, Long> {

    Optional<InviteCodeEntity> findByCodeHash(String codeHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update InviteCodeEntity i
            set i.usedCount = i.usedCount + 1
            where i.id = :id
              and i.usedCount < i.maxUses
              and i.revokedAt is null
              and i.expiresAt > :now
            """)
    int consumeOneUse(@Param("id") Long id, @Param("now") Instant now);
}
