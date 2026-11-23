package in.ggv.campora.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import in.ggv.campora.identity.service.AccountService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountService accounts;

    @Test
    void codeSetsANewPassword() throws Exception {
        accounts.signInWithGoogle("reset-1", "reset1@example.com", "Re", "Set"); // verified, no password yet
        MvcResult sent = request("reset1@example.com");
        String code = (String) sent.getFlashMap().get("devOtp");
        MockHttpSession session = (MockHttpSession) sent.getRequest().getSession(false);

        mvc.perform(get("/reset").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("reset1@example.com")));
        submit(session, "000000".equals(code) ? "111111" : "000000", "newpassword1", "newpassword1")
                .andExpect(status().isOk()).andExpect(content().string(containsString("Incorrect code")));
        submit(session, code, "newpassword1", "different1")
                .andExpect(content().string(containsString("Passwords do not match")));
        submit(session, code, "newpassword1", "newpassword1").andExpect(redirectedUrl("/login"));
        mvc.perform(post("/login").with(csrf()).param("mobile", "reset1@example.com").param("password", "newpassword1"))
                .andExpect(redirectedUrl("/dashboard"));
        submit(session, code, "another-pass", "another-pass").andExpect(redirectedUrl("/forgot"));
    }

    @Test
    void unknownAndUnverifiedEmailsLookTheSameAndGetNoCode() throws Exception {
        accounts.register("Pen", "Ding", "pending@example.com", "9123400101", "password123");
        for (String email : new String[] {"nobody@example.com", "pending@example.com"}) {
            MvcResult sent = request(email);
            assertThat(sent.getFlashMap().get("devOtp")).isNull();
            assertThat((String) sent.getFlashMap().get("success")).startsWith("If that email has an account");
        }
    }

    @Test
    void resetPageNeedsARequestFirst() throws Exception {
        mvc.perform(get("/reset")).andExpect(redirectedUrl("/forgot"));
        mvc.perform(get("/forgot")).andExpect(status().isOk());
        mvc.perform(get("/login")).andExpect(content().string(containsString("/forgot")));
    }

    private MvcResult request(String email) throws Exception {
        return mvc.perform(post("/forgot").with(csrf()).session(new MockHttpSession()).param("email", email))
                .andExpect(redirectedUrl("/reset")).andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions submit(MockHttpSession session, String code,
                                                                      String password, String confirm)
            throws Exception {
        return mvc.perform(post("/reset").with(csrf()).session(session)
                .param("code", code).param("password", password).param("confirmPassword", confirm));
    }
}
