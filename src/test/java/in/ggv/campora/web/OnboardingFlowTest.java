package in.ggv.campora.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import in.ggv.campora.campus.repository.DepartmentRepository;
import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.identity.repository.UserRepository;
import in.ggv.campora.identity.security.GoogleLoginHandler;
import in.ggv.campora.identity.service.AccountService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnboardingFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountService accounts;

    @Autowired
    UserRepository users;

    @Autowired
    GoogleLoginHandler googleLogin;

    @Autowired
    DepartmentRepository departments;

    @Test
    void studentSignsUpWithoutEnrollment() throws Exception {
        MockHttpSession session = signUp("9123400001", "STUDENT");
        mvc.perform(get("/onboarding/profile").session(session)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("nrollment"))));
        mvc.perform(post("/onboarding/profile/student").with(csrf()).session(session)
                        .param("departmentId", departments.findByCode("MCA").orElseThrow().getId().toString())
                        .param("semester", "3"))
                .andExpect(redirectedUrl("/student"));
        mvc.perform(get("/student").session(session)).andExpect(status().isOk());
    }

    @Test
    void driverCanGoOnlineImmediately() throws Exception {
        MockHttpSession session = signUp("9123400002", "DRIVER");
        mvc.perform(post("/onboarding/profile/driver").with(csrf()).session(session)
                        .param("vehicleType", "AUTO")
                        .param("vehicleNumber", "CG10ZZ9999")
                        .param("seatCapacity", "4"))
                .andExpect(redirectedUrl("/driver"));
        mvc.perform(post("/driver/availability").with(csrf()).session(session).param("online", "true"))
                .andExpect(redirectedUrl("/driver"));
        mvc.perform(get("/driver").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Go offline")))
                .andExpect(content().string(not(containsString("Account blocked"))));
    }

    @Test
    void emailAndGoogleSignInReachTheSameAccount() throws Exception {
        signUp("9123400003", "STUDENT");
        mvc.perform(post("/login").with(csrf()).param("mobile", "9123400003@EXAMPLE.com")
                .param("password", "river-stone-42")).andExpect(redirectedUrl("/dashboard"));
        UUID viaGoogle = accounts.signInWithGoogle("sub-1", "9123400003@example.com", "Test", "User");
        assertThat(viaGoogle).isEqualTo(users.findByMobileNumber("9123400003").orElseThrow().getId());
    }

    @Test
    void googleNeverAdoptsAnUnverifiedRegistration() {
        UUID squatter = accounts.register("Bad", "Actor", "victim@example.com", "9123400004", "river-stone-42");
        UUID owner = accounts.signInWithGoogle("sub-2", "victim@example.com", "Real", null);
        assertThat(owner).isNotEqualTo(squatter);
        assertThat(users.findById(squatter)).isEmpty();
        assertThat(users.findById(owner).orElseThrow().getPasswordHash()).isNull();
    }

    @Test
    void verifiedEmailCannotBeRegisteredAgain() {
        accounts.signInWithGoogle("sub-3", "taken@example.com", "A", "B");
        assertThatThrownBy(() -> accounts.register("X", "Y", "taken@example.com", "9123400005", "river-stone-42"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void googleUserAddsPhoneThenPicksRole() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        OidcIdToken token = OidcIdToken.withTokenValue("t").subject("sub-4").claim("email", "g4@example.com")
                .claim("email_verified", true).claim("given_name", "Gita").build();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        googleLogin.onAuthenticationSuccess(request, response,
                new TestingAuthenticationToken(new DefaultOidcUser(List.of(), token), null));
        SecurityContextHolder.clearContext();
        assertThat(response.getRedirectedUrl()).isEqualTo("/dashboard");
        mvc.perform(get("/dashboard").session(session)).andExpect(redirectedUrl("/onboarding/contact"));
        mvc.perform(get("/onboarding/contact").session(session)).andExpect(status().isOk());
        mvc.perform(post("/onboarding/contact").with(csrf()).session(session).param("mobile", "9123400006"))
                .andExpect(redirectedUrl("/onboarding/role"));
        mvc.perform(post("/onboarding/role").with(csrf()).session(session).param("role", "STUDENT"))
                .andExpect(redirectedUrl("/onboarding/profile"));
        mvc.perform(post("/login").with(csrf()).param("mobile", "g4@example.com").param("password", "x"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void mistypedEmailCanBeCorrected() throws Exception {
        UUID id = accounts.register("Typo", "User", "typo@example.con", "9123400007", "river-stone-42");
        MvcResult login = mvc.perform(post("/login").with(csrf()).param("mobile", "9123400007")
                .param("password", "river-stone-42")).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/verify").session(session)).andExpect(status().isOk());
        mvc.perform(post("/verify/email").with(csrf()).session(session).param("email", "typo@example.com"))
                .andExpect(redirectedUrl("/verify"));
        assertThat(users.findById(id).orElseThrow().getEmail()).isEqualTo("typo@example.com");
    }

    @Test
    void googleButtonStartsGoogleFlow() throws Exception {
        mvc.perform(get("/login")).andExpect(content().string(containsString("Continue with Google")));
        String target = mvc.perform(get("/oauth2/authorization/google")).andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(target).startsWith("https://accounts.google.com/").contains("/login/oauth2/code/google");
    }

    private MockHttpSession signUp(String mobile, String role) throws Exception {
        MvcResult registered = mvc.perform(post("/register").with(csrf())
                        .param("firstName", "Test").param("lastName", "User").param("mobile", mobile)
                        .param("email", mobile + "@example.com")
                        .param("password", "river-stone-42").param("confirmPassword", "river-stone-42"))
                .andExpect(redirectedUrl("/verify")).andReturn();
        MockHttpSession session = (MockHttpSession) registered.getRequest().getSession(false);
        mvc.perform(post("/verify").with(csrf()).session(session)
                        .param("code", (String) registered.getFlashMap().get("devOtp")))
                .andExpect(redirectedUrl("/onboarding/role"));
        mvc.perform(post("/onboarding/role").with(csrf()).session(session).param("role", role))
                .andExpect(redirectedUrl("/onboarding/profile"));
        return session;
    }
}
