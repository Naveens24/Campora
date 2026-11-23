package in.ggv.campora.identity.security;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.ExceptionMappingAuthenticationFailureHandler;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Security model
 * <ul>
 *   <li>Form login (email or mobile + BCrypt password) with emailed OTP, or Google sign-in.</li>
 *   <li>Role-based URL rules: /student & /companion → STUDENT, /driver → DRIVER, /admin → ADMIN.</li>
 *   <li>CSRF on for every form; strict CSP, no framing, no referrer leaks off-site.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com",
            "img-src 'self' data:",
            "connect-src 'self'",
            "frame-ancestors 'none'",
            "form-action 'self'",
            "base-uri 'self'");

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository,
                                                   ObjectProvider<ClientRegistrationRepository> googleClient,
                                                   GoogleLoginHandler googleLogin)
            throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/register", "/error", "/error/**",
                        "/css/**", "/js/**", "/img/**", "/webjars/**", "/favicon.svg",
                        "/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/student/**", "/companion/**", "/api/v1/student/**").hasRole("STUDENT")
                .requestMatchers("/driver/**", "/api/v1/driver/**").hasRole("DRIVER")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("mobile")
                .passwordParameter("password")
                .defaultSuccessUrl("/dashboard", true)
                .failureHandler(failureHandler())
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .deleteCookies("JSESSIONID"))
            .securityContext(ctx -> ctx.securityContextRepository(contextRepository))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .frameOptions(frame -> frame.deny()));
        if (googleClient.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth
                .loginPage("/login")
                .successHandler(googleLogin)
                .failureUrl("/login?google"));
        }
        return http.build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(11);
    }

    private ExceptionMappingAuthenticationFailureHandler failureHandler() {
        var handler = new ExceptionMappingAuthenticationFailureHandler();
        handler.setDefaultFailureUrl("/login?error");
        handler.setExceptionMappings(Map.of(DisabledException.class.getName(), "/login?suspended"));
        return handler;
    }
}
