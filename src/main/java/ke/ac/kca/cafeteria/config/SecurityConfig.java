package ke.ac.kca.cafeteria.config;

import ke.ac.kca.cafeteria.identity.MustChangePasswordFilter;
import ke.ac.kca.cafeteria.security.LoginThrottle;
import ke.ac.kca.cafeteria.security.LoginThrottleFilter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

/**
 * Security rules (SDS section 8). URL rules here are the first layer; services add
 * ownership checks as features arrive. CSRF stays enabled (default).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final boolean requireHttps;

    /** With app.require-https=true (the prod and behind-proxy profiles) every plain-HTTP request is sent to HTTPS. */
    public SecurityConfig(@Value("${app.require-https:false}") boolean requireHttps) {
        this.requireHttps = requireHttps;
    }

    /** Counts failed sign-ins per address. By default 10 failures in 15 minutes blocks that address for a while. */
    @Bean
    LoginThrottle loginThrottle(@Value("${app.rate-limit.login.max-failures:10}") int maxFailures,
                                @Value("${app.rate-limit.login.window-minutes:15}") long windowMinutes) {
        return new LoginThrottle(maxFailures, Duration.ofMinutes(windowMinutes));
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, LoginThrottle loginThrottle) throws Exception {
        // Remember the page a visitor asked for, so scanning a table QR code while signed out
        // continues to that table after sign-in.
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setMatchingRequestParameterName(null);

        http
            .requestCache(cache -> cache.requestCache(requestCache))
            .exceptionHandling(handling -> handling.accessDeniedHandler(accessDeniedHandler()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/actuator/health").permitAll()
                .requestMatchers("/webjars/**", "/css/**", "/js/**", "/fonts/**", "/error").permitAll()
                .requestMatchers("/student/**", "/t/**").hasRole("STUDENT")
                .requestMatchers("/staff/**").hasAnyRole("STAFF", "MANAGER")
                .requestMatchers("/manager/**").hasRole("MANAGER")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(new RoleAwareSuccessHandler(requestCache))
                .permitAll())
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; "
                    + "frame-ancestors 'none'; form-action 'self'"))
                // Browsers that have seen the site over HTTPS keep using HTTPS (sent only on secure connections).
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                        "camera=(), microphone=(), geolocation=(), payment=()")))
            .addFilterBefore(new LoginThrottleFilter(loginThrottle), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new MustChangePasswordFilter(), AnonymousAuthenticationFilter.class);
        if (requireHttps) {
            http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
        }
        return http.build();
    }

    /**
     * A sign-in form that went stale (for example the app was restarted while the page stayed open) fails the CSRF
     * check. Instead of an "access denied" page, send the person back to sign in with a short explanation.
     * Every other refusal keeps the normal 403 page, and every other form still needs a valid token.
     */
    private static AccessDeniedHandler accessDeniedHandler() {
        AccessDeniedHandlerImpl standard = new AccessDeniedHandlerImpl();
        return (request, response, exception) -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (exception instanceof CsrfException && "/login".equals(path)) {
                response.sendRedirect(request.getContextPath() + "/login?expired");
            } else {
                standard.handle(request, response, exception);
            }
        };
    }

    /** BCrypt per NFR-02 (SDS allows Argon2id where supported). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
