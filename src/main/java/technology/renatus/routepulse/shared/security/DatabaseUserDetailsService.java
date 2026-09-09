package technology.renatus.routepulse.shared.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.auth.repository.UserAccountRepository;
import technology.renatus.routepulse.organization.repository.OrganizationMembershipRepository;

@Service
@Transactional(readOnly = true)
public class DatabaseUserDetailsService
        implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;
    private final OrganizationMembershipRepository
            organizationMembershipRepository;

    public DatabaseUserDetailsService(
            UserAccountRepository userAccountRepository,
            OrganizationMembershipRepository
                    organizationMembershipRepository) {

        this.userAccountRepository = userAccountRepository;
        this.organizationMembershipRepository =
                organizationMembershipRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {

        String normalizedEmail = email.trim();

        UserAccount account = userAccountRepository
                .findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Invalid email or password."
                        )
                );

        List<String> authorities = new ArrayList<>();

        authorities.add("ROLE_USER");

        organizationMembershipRepository
                .findAllByUser_Id(account.getId())
                .stream()
                .map(membership ->
                        "ROLE_" + membership.getRole().name())
                .distinct()
                .forEach(authorities::add);

        boolean loginAllowed =
                account.isEnabled()
                && account.isEmailVerified();

        return User
                .withUsername(account.getEmail())
                .password(account.getPasswordHash())
                .authorities(
                        authorities.toArray(String[]::new)
                )
                .disabled(!loginAllowed)
                .build();
    }
}
