package technology.renatus.routepulse.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import technology.renatus.routepulse.model.PasswordResetToken;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    @Query("""
            SELECT token
            FROM PasswordResetToken token
            JOIN FETCH token.user
            WHERE token.tokenHash = :tokenHash
            """)
    Optional<PasswordResetToken> findByTokenHash(
            @Param("tokenHash") String tokenHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT token
            FROM PasswordResetToken token
            JOIN FETCH token.user
            WHERE token.tokenHash = :tokenHash
            """)
    Optional<PasswordResetToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    List<PasswordResetToken>
            findAllByUser_IdAndUsedAtIsNull(
                    Long userId
            );
}
