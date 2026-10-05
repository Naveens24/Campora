package in.ggv.campora.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebSecurityTest {

    @Autowired
    MockMvc mvc;

    @Test
    void publicPagesRender() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"));
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/register")).andExpect(status().isOk());
    }

    @Test
    void roleConsolesRequireSignIn() throws Exception {
        mvc.perform(get("/student")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/driver")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
    }

    @Test
    void formsRequireCsrfToken() throws Exception {
        mvc.perform(post("/register").param("mobile", "9876543210")).andExpect(status().isForbidden());
    }

    @Test
    void registrationSignsInAndSendsToOtp() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("firstName", "Test")
                        .param("lastName", "Student")
                        .param("mobile", "9123456780")
                        .param("password", "password123")
                        .param("confirmPassword", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/verify"));
    }

    @Test
    void badLoginIsRejected() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("mobile", "9999999999").param("password", "nope"))
                .andExpect(redirectedUrl("/login?error"));
    }
}
