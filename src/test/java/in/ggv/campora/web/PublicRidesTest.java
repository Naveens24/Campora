package in.ggv.campora.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import in.ggv.campora.campus.domain.Destination;
import in.ggv.campora.campus.repository.DepartmentRepository;
import in.ggv.campora.campus.repository.DestinationRepository;
import in.ggv.campora.companion.service.CompanionService;
import in.ggv.campora.identity.repository.UserRepository;
import in.ggv.campora.mobility.domain.RequestType;
import in.ggv.campora.mobility.service.TravelRequestService;

/** Anyone can look for rides; an account is asked for only at the moment it is needed. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicRidesTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    CompanionService companions;

    @Autowired
    TravelRequestService requests;

    @Autowired
    UserRepository users;

    @Autowired
    DestinationRepository destinations;

    @Autowired
    DepartmentRepository departments;

    @Test
    void visitorsCanPickARouteOnTheHomepage() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Choose pickup")))
                .andExpect(content().string(containsString("Main Gate")))
                .andExpect(content().string(not(containsString("href=\"/register\">Find"))));
    }

    @Test
    void resultsShowRidesButNotPeople() throws Exception {
        student("9123500001", "Hostfirst");
        UUID from = stop("Main Gate");
        UUID to = stop("Apollo Hospital");
        UUID ride = offer("9123500001", from, to, "Meet at the secret tree");

        mvc.perform(get("/rides").param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/rides/" + ride)))
                .andExpect(content().string(not(containsString("Hostfirst"))))
                .andExpect(content().string(not(containsString("9123500001"))))
                .andExpect(content().string(not(containsString("secret tree"))));
        // Another route lists it as a suggestion.
        mvc.perform(get("/rides").param("from", stop("Boys Hostel").toString()).param("to", to.toString()))
                .andExpect(content().string(containsString("No companion ride posted yet")))
                .andExpect(content().string(containsString("/rides/" + ride)));
    }

    @Test
    void resultsCountWaitingStudents() throws Exception {
        UUID from = stop("Central Library");
        UUID to = stop("Koni Market");
        student("9123500002", "Wait");
        requests.create(id("9123500002"), from, to, RequestType.ANY, 1);
        mvc.perform(get("/rides").param("from", from.toString()).param("to", to.toString()))
                .andExpect(content().string(containsString("1 student is waiting on this route now")));
    }

    @Test
    void badRoutesAreExplained() throws Exception {
        String gate = stop("Main Gate").toString();
        mvc.perform(get("/rides").param("from", gate).param("to", gate)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Pickup and drop must be different.")));
        mvc.perform(get("/rides").param("from", gate).param("to", UUID.randomUUID().toString()))
                .andExpect(content().string(containsString("not in service")));
        mvc.perform(get("/rides").param("from", "nonsense").param("to", "")).andExpect(status().isOk());
        mvc.perform(get("/rides/request").param("from", gate)).andExpect(redirectedUrl("/rides"));
        mvc.perform(get("/rides/offer").param("from", gate).param("to", gate)).andExpect(redirectedUrl("/rides"));
        mvc.perform(get("/rides/" + UUID.randomUUID())).andExpect(redirectedUrl("/rides"))
                .andExpect(flash().attribute("error", "That ride has left or filled up."));
        mvc.perform(get("/rides/nonsense")).andExpect(status().isNotFound());
    }

    @Test
    void joiningAsksToSignInThenReturnsToTheRide() throws Exception {
        student("9123500003", "Host");
        UUID ride = offer("9123500003", stop("Science Block"), stop("Sarkanda"), null);
        student("9123500004", "Rider");

        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/rides/" + ride).session(session)).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/login").session(session))
                .andExpect(content().string(containsString("Sign in to continue")))
                .andExpect(content().string(containsString("Join this ride")))
                .andExpect(content().string(containsString("Science Block")));
        mvc.perform(post("/login").with(csrf()).session(session).param("mobile", "9123500004@example.com")
                .param("password", "password123")).andExpect(redirectedUrl("/dashboard"));
        mvc.perform(get("/dashboard").session(session)).andExpect(redirectedUrl("/companion?ride=" + ride));
        mvc.perform(get("/companion").param("ride", ride.toString()).session(session))
                .andExpect(content().string(containsString("Your pick")));
        // Looking never books: the student still has to press Join.
        assertThat(companions.joinedRide(id("9123500004"))).isEmpty();
        // Used once.
        mvc.perform(get("/dashboard").session(session)).andExpect(redirectedUrl("/student"));
        mvc.perform(get("/companion").param("ride", UUID.randomUUID().toString()).session(session))
                .andExpect(content().string(containsString("has left or filled up")));
    }

    @Test
    void newStudentsKeepTheirRouteThroughSignUp() throws Exception {
        String from = stop("Girls Hostel").toString();
        String to = stop("Old Bus Stand").toString();
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/rides/request").param("from", from).param("to", to).session(session))
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/register").session(session)).andExpect(content().string(containsString("Request this ride")));
        signUp(session, "9123500005", "New", "STUDENT");
        mvc.perform(get("/onboarding/profile").session(session))
                .andExpect(content().string(containsString("Girls Hostel")));
        finishStudent(session).andExpect(redirectedUrl("/student?from=" + from + "&to=" + to));
        mvc.perform(get("/student").param("from", from).param("to", to).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"" + from + "\" selected=\"selected\"")))
                .andExpect(content().string(containsString("value=\"" + to + "\" selected=\"selected\"")));
        assertThat(requests.activeRequest(id("9123500005"))).isEmpty();
    }

    @Test
    void signedInStudentsGoStraightThrough() throws Exception {
        MockHttpSession session = student("9123500006", "Quick");
        String from = stop("Main Gate").toString();
        String to = stop("Nehru Chowk").toString();
        mvc.perform(get("/rides/request").param("from", from).param("to", to).session(session))
                .andExpect(redirectedUrl("/student?from=" + from + "&to=" + to));
        mvc.perform(get("/rides/offer").param("from", from).param("to", to).session(session))
                .andExpect(redirectedUrl("/companion?from=" + from + "&to=" + to + "#panel-offer"));
        mvc.perform(get("/rides/offer").session(session)).andExpect(redirectedUrl("/companion#panel-offer"));
        mvc.perform(get("/companion").param("from", from).param("to", to).session(session))
                .andExpect(content().string(containsString("value=\"" + to + "\" selected=\"selected\"")));
    }

    @Test
    void driversAreToldRidesAreForStudents() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/rides/offer").session(session)).andExpect(redirectedUrl("/login"));
        signUp(session, "9123500007", "Drive", "DRIVER");
        mvc.perform(post("/onboarding/profile/driver").with(csrf()).session(session).param("vehicleType", "AUTO")
                        .param("vehicleNumber", "CG10ZY8888").param("seatCapacity", "4"))
                .andExpect(redirectedUrl("/driver"));
        mvc.perform(get("/dashboard").session(session)).andExpect(redirectedUrl("/driver"));
        mvc.perform(get("/rides/offer").session(session)).andExpect(redirectedUrl("/rides"))
                .andExpect(flash().attribute("error", "Only student accounts can do this."));
    }

    // ── helpers ────────────────────────────────────────────────

    private UUID stop(String name) {
        return destinations.findByName(name).map(Destination::getId).orElseThrow();
    }

    private UUID id(String mobile) {
        return users.findByMobileNumber(mobile).orElseThrow().getId();
    }

    private UUID offer(String mobile, UUID from, UUID to, String note) {
        return companions.offer(id(mobile), from, to, LocalDateTime.now(ZoneId.of("Asia/Kolkata")).plusMinutes(40), 2,
                note);
    }

    private MockHttpSession student(String mobile, String firstName) throws Exception {
        MockHttpSession session = new MockHttpSession();
        signUp(session, mobile, firstName, "STUDENT");
        finishStudent(session).andExpect(redirectedUrl("/student"));
        return session;
    }

    private ResultActions finishStudent(MockHttpSession session) throws Exception {
        return mvc.perform(post("/onboarding/profile/student").with(csrf()).session(session)
                .param("departmentId", departments.findByCode("MCA").orElseThrow().getId().toString())
                .param("semester", "3"));
    }

    private void signUp(MockHttpSession session, String mobile, String firstName, String role) throws Exception {
        MvcResult registered = mvc.perform(post("/register").with(csrf()).session(session)
                        .param("firstName", firstName).param("lastName", "Person")
                        .param("email", mobile + "@example.com").param("mobile", mobile)
                        .param("password", "password123").param("confirmPassword", "password123"))
                .andExpect(redirectedUrl("/verify")).andReturn();
        String code = (String) registered.getFlashMap().get("devOtp");
        mvc.perform(post("/verify").with(csrf()).session(session).param("code", code))
                .andExpect(redirectedUrl("/onboarding/role"));
        mvc.perform(post("/onboarding/role").with(csrf()).session(session).param("role", role))
                .andExpect(redirectedUrl("/onboarding/profile"));
    }
}
