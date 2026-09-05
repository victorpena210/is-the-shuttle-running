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
    public SecurityFilterChain
            securityFilterChain(
                    HttpSecurity http,
                    PublicRequestThrottle
                            publicRequestThrottle)
                    throws Exception {

        LoginAttemptThrottleFilter
                loginAttemptThrottleFilter =
                new LoginAttemptThrottleFilter(
                        publicRequestThrottle
                );

        http
                .authorizeHttpRequests(authorize ->
                        authorize

                                /*
                                 * Public pages and static assets.
                                 */
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
                                )
                                .permitAll()

                                /*
                                 * Organization invitations are
                                 * owner-only management features.
                                 */
                                .requestMatchers(
                                        "/organization/invitations/**"
                                )
                                .hasRole(
                                        "OWNER"
                                )

                                /*
                                 * The driver portal is the only
                                 * application area available to a
                                 * driver-only account.
                                 *
                                 * This includes viewing today's own
                                 * assignment and recording start/end
                                 * through the shuttle QR code.
                                 */
                                .requestMatchers(
                                        "/driver/**"
                                )
                                .hasRole(
                                        "DRIVER"
                                )

                                /*
                                 * Every signed-in account passes
                                 * through this endpoint immediately
                                 * after a normal login so RoutePulse
                                 * can send drivers to /driver and
                                 * managers to /app.
                                 */
                                .requestMatchers(
                                        "/post-login"
                                )
                                .authenticated()

                                /*
                                 * DEFAULT MANAGER RULE
                                 *
                                 * Any application route that is not
                                 * public, driver-only, or owner-only
                                 * automatically requires a manager
                                 * role.
                                 *
                                 * This protects current manager pages
                                 * such as /app, /drivers, /upload,
                                 * /assignments, /driving-records and
                                 * /driving-qr, and it also protects
                                 * future manager routes by default.
                                 */
                                .anyRequest()
                                .hasAnyRole(
                                        "OWNER",
                                        "ADMIN",
                                        "DISPATCHER"
                                )
                )

                .formLogin(form ->
                        form
                                .loginPage(
                                        "/login"
                                )
                                .usernameParameter(
                                        "email"
                                )

                                /*
                                 * false matters here.
                                 *
                                 * If a driver scanned the QR
                                 * while signed out, Spring keeps
                                 * the original /driver/scan URL
                                 * and returns them there after login.
                                 */
                                .defaultSuccessUrl(
                                        "/post-login",
                                        false
                                )
                                .failureUrl(
                                        "/login?error"
                                )
                                .permitAll()
                )

                .logout(logout ->
                        logout
                                .logoutSuccessUrl(
                                        "/login?logout"
                                )
                                .invalidateHttpSession(
                                        true
                                )
                                .clearAuthentication(
                                        true
                                )
                                .permitAll()
                )

                .addFilterBefore(
                        loginAttemptThrottleFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}