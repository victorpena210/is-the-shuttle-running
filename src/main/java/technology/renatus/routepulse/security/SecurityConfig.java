package technology.renatus.routepulse.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories
                .createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            PublicRequestThrottle publicRequestThrottle)
            throws Exception {

        LoginAttemptThrottleFilter
                loginAttemptThrottleFilter =
                new LoginAttemptThrottleFilter(
                        publicRequestThrottle
                );

        http
                .authorizeHttpRequests(authorize -> authorize

                        .requestMatchers(
                                "/",
                                "/demo-request",
                                "/login",
                                "/register",
                                "/organization/register",
                                "/verify-email",
                                "/resend-verification",
                                "/invitations/accept",
                                "/forgot-password",
                                "/reset-password",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/error"
                        ).permitAll()

                        // Only the person who originally
                        // registered the organization
                        // can manage manager invitations.
                        .requestMatchers(
                                "/organization/invitations/**"
                        ).hasRole("OWNER")

                        // Owners and invited managers
                        // can operate the organization.
                        .requestMatchers(
                                "/app",
                                "/upload",
                                "/drivers/**",
                                "/assignments/**"
                        ).hasAnyRole(
                                "OWNER",
                                "ADMIN",
                                "DISPATCHER"
                        )

                        .anyRequest().authenticated()
                )

                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .defaultSuccessUrl("/app", true)
                        .failureUrl("/login?error")
                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .permitAll()
                )

                .addFilterBefore(
                        loginAttemptThrottleFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
