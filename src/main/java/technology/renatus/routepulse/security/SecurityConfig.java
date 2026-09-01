package technology.renatus.routepulse.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories
                .createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(authorize -> authorize

                        .requestMatchers(
                                "/login",
                                "/register",
                                "/verify-email",
                                "/invitations/accept",
                                "/forgot-password",
                                "/reset-password",
                                "/css/**",
                                "/js/**",
                                "/images/**",
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
                                "/",
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
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error")
                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .permitAll()
                );

        return http.build();
    }
}