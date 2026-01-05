package com.example.attempt.repository;

import com.example.attempt.domain.RefreshToken;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.tokenHash = :hash and r.revoked = false and r.expiresAt > :now")
    int revokeByHash(@Param("hash") String hash, @Param("now") LocalDateTime now);

    int deleteByUsernameAndExpiresAtBefore(String username, LocalDateTime now);
}
