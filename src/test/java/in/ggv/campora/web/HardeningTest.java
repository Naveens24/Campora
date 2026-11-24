package in.ggv.campora.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.identity.repository.UserRepository;
import in.ggv.campora.identity.security.CodeThrottle;
import in.ggv.campora.identity.security.LoginThrottle;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HardeningTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    CodeThrottle codes;

    @Autowired
    LoginThrottle logins;

    @Autowired
    UserRepository users;

    @Test
    void lookingAroundOpensNoSession() throws Exception {
        for (String page : List.of("/", "/rides", "/student", "/wp-login.php", "/robots.txt", "/api/v1/student/request")) {
            assertThat(mvc.perform(get(page)).andReturn().getRequest().getSession(false)).as(page).isNull();
        }
    }

    @Test
    void visitorsSeeNotFoundInsteadOfSignIn() throws Exception {
        mvc.perform(get("/no-such-page")).andExpect(status().isNotFound());
        mvc.perform(get("/.env")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/info")).andExpect(status().isNotFound());
        mvc.perform(get("/studentx")).andExpect(status().isNotFound());
        mvc.perform(get("/admin/users")).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/api/v1/driver/hotspots")).andExpect(status().isUnauthorized());
        mvc.perform(get("/robots.txt")).andExpect(status().isOk());
    }

    @Test
    void expiredFormsExplainThemselvesAndDoNothing() throws Exception {
        mvc.perform(post("/register").param("firstName", "A").param("lastName", "B").param("email", "csrf@example.com")
                .param("mobile", "9123600001").param("password", "river-stone-42")
                .param("confirmPassword", "river-stone-42")).andExpect(redirectedUrl("/register?expired"));
        assertThat(users.findByEmail("csrf@example.com")).isEmpty();
        mvc.perform(post("/login").param("mobile", "x").param("password", "y"))
                .andExpect(redirectedUrl("/login?expired"));
        mvc.perform(post("/logout")).andExpect(redirectedUrl("/login?logout&expired"));
        mvc.perform(get("/login").param("expired", "")).andExpect(content().string(containsString("open for too long")));
    }

    @Test
    void browserProtectionsAreOn() throws Exception {
        mvc.perform(get("/")).andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Permissions-Policy", containsString("geolocation=()")))
                .andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void weakOrUnusablePasswordsAreRefused() throws Exception {
        for (String weak : List.of("Password123", "12345678", "aaaaaaaaaa", "short", "क".repeat(30))) {
            mvc.perform(post("/register").with(csrf()).param("firstName", "A").param("lastName", "B")
                            .param("email", "weak@example.com").param("mobile", "9123600002")
                            .param("password", weak).param("confirmPassword", weak))
                    .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "password"));
        }
        assertThat(users.findByEmail("weak@example.com")).isEmpty();
    }

    @Test
    void oneNetworkCannotAskForEndlessCodes() {
        for (int i = 0; i < 60; i++) {
            codes.check(from("203.0.113.9"));
        }
        assertThatThrownBy(() -> codes.check(from("203.0.113.9"))).isInstanceOf(BusinessException.class);
        codes.check(from("203.0.113.10"));
        // A private address means the real one is unknown, so nobody is punished for it.
        for (int i = 0; i < 300; i++) {
            codes.check(from("127.0.0.1"));
        }
    }

    @Test
    void oneNetworkCannotTryAPasswordOnEveryAccount() {
        for (int i = 0; i < 100; i++) {
            MockHttpServletRequest attempt = from("2001:db8:1:2::" + Integer.toHexString(i + 1));
            attempt.setParameter("mobile", "user" + i + "@example.com");
            assertThat(logins.blocked(attempt)).isFalse();
            logins.failed(attempt);
        }
        MockHttpServletRequest next = from("2001:db8:1:2::ffff");
        next.setParameter("mobile", "someone@example.com");
        assertThat(logins.blocked(next)).isTrue();
        MockHttpServletRequest elsewhere = from("2001:db8:1:3::1");
        elsewhere.setParameter("mobile", "someone@example.com");
        assertThat(logins.blocked(elsewhere)).isFalse();
    }

    @Test
    void publicPagesCarryNoCsrfMeta() throws Exception {
        mvc.perform(get("/")).andExpect(content().string(not(containsString("name=\"_csrf\""))));
    }

    private static MockHttpServletRequest from(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        return request;
    }
}
