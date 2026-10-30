package technology.renatus.routepulse.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

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
                            publicRequestThrottle,
                    TokenBasedRememberMeServices rememberMeServices)
                    throws Exception {

        LoginAttemptThrottleFilter
                loginAttemptThrottleFilter =
                new LoginAttemptThrottleFilter(
                        publicRequestThrottle
                );

        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();

        http
                .requestCache(cache -> cache.requestCache(requestCache))
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
                                        "/ride/**",
                                        "/employee-shuttle-management-software",
                                        "/workforce-transportation-planning",
                                        "/shuttle-driver-scheduling",
                                        "/shuttle-operations-verification",
                                        "/employee-shuttle-software-vs-spreadsheets",
                                        "/robots.txt",
                                        "/sitemap.xml",
                                        "/css/**",
                                        "/js/**",
                                        "/images/**",
                                        "/video/**",
                                        "/actuator/health",
                                        "/actuator/health/**",
                                        "/error"
                                )
                                .permitAll()

                                /*
                                 * Owner-only organization management.
                                 */
                                .requestMatchers(
                                        "/organization/invitations/**"
                                )
                                .hasRole(
                                        "OWNER"
                                )

                                /*
                                 * Owners and admins may view, print,
                                 * and regenerate the organization's
                                 * shuttle NFC/QR driver checkpoint.
                                 */
                                .requestMatchers(
                                        "/driving-qr",
                                        "/driving-qr/**"
                                )
                                .hasAnyRole(
                                        "OWNER",
                                        "ADMIN"
                                )

                                /*
                                 * The driver portal is the only
                                 * application area available to a
                                 * driver-only account.
                                 *
                                 * This includes viewing today's own
                                 * assignment and recording start/end
                                 * through the shuttle NFC/QR checkpoint.
                                 * Drivers can also read today's aggregate
                                 * demand, upcoming assignments and their own activity analytics.
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
                                        "/post-login",
                                        "/my-driving-assignments",
                                        "/my-pickup-assistance"
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
                                 * /assignments and /driving-records,
                                 * and it also protects future manager
                                 * routes by default.
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

                                // Keep checkpoint/query links, but never send a driver-only
                                // account back to a saved manager page after signing in.
                                .successHandler(new RoleAwareLoginSuccessHandler(requestCache))
                                .failureUrl(
                                        "/login?error"
                                )
                                .permitAll()
                )

                .rememberMe(remember -> remember
                        .key(rememberMeServices.getKey())
                        .rememberMeServices(rememberMeServices)
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
                                .deleteCookies("JSESSIONID")
                                .permitAll()
                )

                .addFilterBefore(
                        loginAttemptThrottleFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
