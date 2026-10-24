package ke.ac.kca.cafeteria.config;

import ke.ac.kca.cafeteria.identity.MustChangePasswordFilter;
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
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

/**
 * Security rules (SDS section 8). URL rules here are the first layer; services add
 * ownership checks as features arrive. CSRF stays enabled (default).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
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
                    + "frame-ancestors 'none'; form-action 'self'")))
            .addFilterAfter(new MustChangePasswordFilter(), AnonymousAuthenticationFilter.class);
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
