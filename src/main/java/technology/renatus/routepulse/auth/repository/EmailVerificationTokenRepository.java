package technology.renatus.routepulse.auth.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import technology.renatus.routepulse.auth.model.EmailVerificationToken;

public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT token
            FROM EmailVerificationToken token
            JOIN FETCH token.user
            WHERE token.tokenHash = :tokenHash
            """)
    Optional<EmailVerificationToken>
            findByTokenHashForUpdate(
                    @Param("tokenHash")
                    String tokenHash
            );

    List<EmailVerificationToken>
            findAllByUser_IdAndUsedAtIsNull(
                    Long userId
            );
}
