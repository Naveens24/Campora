package in.ggv.campora.identity.security;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.ExceptionMappingAuthenticationFailureHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import in.ggv.campora.common.web.WebExceptionAdvice;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Security model
 * <ul>
 *   <li>Form login (email or mobile + BCrypt password) with emailed OTP, or Google sign-in.</li>
 *   <li>Home and /rides (search) are public; booking anything needs an account.</li>
 *   <li>Role-based URL rules: /student & /companion → STUDENT, /driver → DRIVER, /admin → ADMIN.</li>
 *   <li>CSRF on for every form; strict CSP, no framing, no referrer leaks off-site.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "object-src 'none'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com",
            "img-src 'self' data:",
            "connect-src 'self'",
            "frame-ancestors 'none'",
            "form-action 'self'",
            "base-uri 'self'");

    private static final String PERMISSIONS = "camera=(), geolocation=(), microphone=(), payment=(), usb=()";

    /** Pages that exist but need an account. Anything else a visitor types is simply not found. */
    private static final List<String> ACCOUNT_PAGES = List.of("/dashboard", "/student", "/companion", "/driver",
            "/admin", "/verify", "/onboarding", "/account");

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository,
                                                   ObjectProvider<ClientRegistrationRepository> googleClient,
                                                   GoogleLoginHandler googleLogin, LoginThrottle throttle)
            throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/rides", "/rides/**", "/login", "/register", "/forgot", "/reset", "/error", "/error/**",
                        "/css/**", "/js/**", "/img/**", "/webjars/**", "/favicon.svg", "/robots.txt",
                        "/actuator/health").permitAll()
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
                .failureHandler(failureHandler(throttle))
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .deleteCookies("JSESSIONID"))
            // Sign-in always goes to /dashboard, so remembering the refused page would only
            // open a session for every stray or bot request.
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint(entryPoint())
                .accessDeniedHandler(SecurityConfig::denied))
            .addFilterBefore(throttleFilter(throttle), UsernamePasswordAuthenticationFilter.class)
            .securityContext(ctx -> ctx.securityContextRepository(contextRepository))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS))
                .crossOriginOpenerPolicy(opener -> opener.policy(CrossOriginOpenerPolicy.SAME_ORIGIN))
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

    private static AuthenticationEntryPoint entryPoint() {
        AuthenticationEntryPoint login = new LoginUrlAuthenticationEntryPoint("/login");
        return (request, response, exception) -> {
            String path = path(request);
            if (path.startsWith("/api/")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);   // the page reloads itself
            } else if (ACCOUNT_PAGES.stream().anyMatch(page -> path.equals(page) || path.startsWith(page + "/"))) {
                login.commence(request, response, exception);
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        };
    }

    /** A form left open until its session ended is not a permission problem: say so and let them retry. */
    private static void denied(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException, ServletException {
        if (!(e instanceof CsrfException)) {
            new AccessDeniedHandlerImpl().handle(request, response, e);
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String path = path(request);
        String back;
        if (auth != null && auth.getPrincipal() instanceof CamporaUserDetails) {
            back = WebExceptionAdvice.safeReferer(request);
        } else if (path.equals("/logout")) {
            back = "/login?logout";
        } else {
            back = Set.of("/register", "/forgot").contains(path) ? path : "/login";
        }
        response.sendRedirect(request.getContextPath() + back + (back.contains("?") ? "&" : "?") + "expired");
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private static AuthenticationFailureHandler failureHandler(LoginThrottle throttle) {
        var handler = new ExceptionMappingAuthenticationFailureHandler();
        handler.setDefaultFailureUrl("/login?error");
        handler.setExceptionMappings(Map.of(DisabledException.class.getName(), "/login?suspended"));
        return (request, response, exception) -> {
            throttle.failed(request);
            handler.onAuthenticationFailure(request, response, exception);
        };
    }

    private static Filter throttleFilter(LoginThrottle throttle) {
        // The same kind of matcher the login itself uses, so no spelling of the URL gets around it.
        RequestMatcher login = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/login");
        return (request, response, chain) -> {
            HttpServletRequest http = (HttpServletRequest) request;
            if (login.matches(http) && throttle.blocked(http)) {
                ((HttpServletResponse) response).sendRedirect(http.getContextPath() + "/login?locked");
                return;
            }
            chain.doFilter(request, response);
        };
    }
}
