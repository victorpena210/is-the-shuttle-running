package technology.renatus.routepulse.shared.security;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.util.Assert;

/** Opt-in sign-in on a personal device, independent of the short HTTP session. */
@Configuration
public class RememberMeConfig {

    @Bean
    public TokenBasedRememberMeServices rememberMeServices(
            UserDetailsService userDetailsService,
            @Value("${app.security.remember-me.key}") String key,
            @Value("${app.security.remember-me.days:90}") int days,
            @Value("${app.security.remember-me.secure-cookie:false}") boolean secureCookie) {

        Assert.isTrue(key != null && key.trim().length() >= 32,
                "REMEMBER_ME_KEY must contain at least 32 characters; generate it with openssl rand -hex 32.");
        Assert.isTrue(days >= 1 && days <= 365,
                "REMEMBER_ME_DAYS must be between 1 and 365.");

        // Spring signs the cookie against the current password hash. A password
        // reset or key rotation invalidates previously issued remember-me cookies.
        TokenBasedRememberMeServices services =
                new TokenBasedRememberMeServices(key, userDetailsService);
        services.setCookieName("shuttle-remember-me");
        services.setParameter("remember-me");
        services.setAlwaysRemember(false);
        services.setTokenValiditySeconds(Math.toIntExact(Duration.ofDays(days).getSeconds()));
        services.setUseSecureCookie(secureCookie);
        services.setCookieCustomizer(cookie -> cookie.setAttribute("SameSite", "Lax"));
        return services;
    }
}
