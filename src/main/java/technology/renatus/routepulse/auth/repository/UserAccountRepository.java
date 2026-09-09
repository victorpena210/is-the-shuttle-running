package technology.renatus.routepulse.auth.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import technology.renatus.routepulse.auth.model.UserAccount;

public interface UserAccountRepository
        extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
