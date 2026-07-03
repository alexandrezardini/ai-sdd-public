package com.videomax.backend.auth.internal.repository;

import com.videomax.backend.auth.internal.entity.RefreshToken;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Plain deleteAllByUserId(UUID) derived query fails with
    // IncorrectResultSizeDataAccessException when a user has more than one
    // refresh token; a raw bulk DELETE avoids that single-result assumption.
    @Modifying
    @Query("DELETE FROM refresh_tokens WHERE user_id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
