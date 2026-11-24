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

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import in.ggv.campora.admin.service.OwnerBootstrap;
import in.ggv.campora.campus.domain.Destination;
import in.ggv.campora.campus.domain.LocationType;
import in.ggv.campora.campus.repository.DepartmentRepository;
import in.ggv.campora.campus.repository.DestinationRepository;
import in.ggv.campora.companion.service.CompanionService;
import in.ggv.campora.identity.domain.RoleName;
import in.ggv.campora.identity.domain.User;
import in.ggv.campora.identity.domain.UserStatus;
import in.ggv.campora.identity.repository.RoleRepository;
import in.ggv.campora.identity.repository.UserRepository;
import in.ggv.campora.identity.service.AccountService;
import in.ggv.campora.mobility.domain.RequestStatus;
import in.ggv.campora.mobility.domain.RequestType;
import in.ggv.campora.mobility.repository.TravelRequestRepository;
import in.ggv.campora.mobility.service.MatchingService;
import in.ggv.campora.mobility.service.TravelRequestService;
import in.ggv.campora.profile.domain.Availability;
import in.ggv.campora.profile.domain.DriverProfile;
import in.ggv.campora.profile.domain.StudentProfile;
import in.ggv.campora.profile.domain.VehicleType;
import in.ggv.campora.profile.repository.DriverProfileRepository;
import in.ggv.campora.profile.repository.StudentProfileRepository;
import in.ggv.campora.trip.domain.TripStatus;
import in.ggv.campora.trip.repository.TripRepository;
import in.ggv.campora.trip.service.TripService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminPanelTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;
    @Autowired OwnerBootstrap bootstrap;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired StudentProfileRepository students;
    @Autowired DriverProfileRepository drivers;
    @Autowired DepartmentRepository departments;
    @Autowired DestinationRepository destinations;
    @Autowired TravelRequestRepository requests;
    @Autowired TravelRequestService requestService;
    @Autowired MatchingService matching;
    @Autowired TripRepository trips;
    @Autowired TripService tripService;
    @Autowired CompanionService companions;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private UUID ownerId;
    private MockHttpSession owner;
    private UUID adminId;
    private MockHttpSession admin;

    @BeforeEach
    void admins() throws Exception {
        ownerId = accounts.signInWithGoogle("owner-sub", "owner@example.com", "Olive", "Owner");
        users.findById(ownerId).ifPresent(u -> {
            u.setPasswordHash(encoder.encode("river-stone-42"));
            users.save(u);
        });
        owner = login("owner@example.com", "river-stone-42");
        User plain = person(RoleName.ADMIN);
        adminId = plain.getId();
        admin = login(plain.getMobileNumber(), "river-stone-42");
    }

    // ── becoming an admin ──────────────────────────────────────

    @Test
    void ownerLandsInTheAdminPanelWithoutChoosingARole() throws Exception {
        mvc.perform(get("/dashboard").session(owner)).andExpect(redirectedUrl("/admin"));
        mvc.perform(get("/admin").session(owner)).andExpect(status().isOk());
    }

    @Test
    void typingTheOwnersEmailIsNotEnough() throws Exception {
        User student = person(RoleName.STUDENT);
        // a plain admin cannot hand the owner's address to someone else either
        save(admin, student.getId(), "Sly", "Fox", "owner@example.com", student.getMobileNumber())
                .andExpect(flash().attribute("error", containsString("owner's email")));
        bootstrap.run(null);
        assertThat(roleOf(student.getId())).isEqualTo(RoleName.STUDENT);
    }

    @Test
    void startUpPromotesAnExistingVerifiedOwnerAccount() throws Exception {
        User u = users.findById(ownerId).orElseThrow();
        u.assignRole(roles.findByName(RoleName.STUDENT).orElseThrow());
        users.save(u);
        bootstrap.run(null);
        assertThat(roleOf(ownerId)).isEqualTo(RoleName.ADMIN);
    }

    @Test
    void studentsAndDriversCannotOpenIt() throws Exception {
        MockHttpSession student = login(person(RoleName.STUDENT).getMobileNumber(), "river-stone-42");
        for (String path : new String[] {"/admin", "/admin/users", "/admin/rides", "/admin/users/" + ownerId}) {
            mvc.perform(get(path).session(student)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/admin/users/" + ownerId + "/delete").with(csrf()).session(student)
                .param("confirm", "DELETE")).andExpect(status().isForbidden());
    }

    @Test
    void everyPageRenders() throws Exception {
        UUID student = studentUser();
        UUID driver = driverUser();
        UUID unfinished = person(null).getId();
        for (String path : new String[] {"/admin", "/admin/users", "/admin/users/new", "/admin/rides", "/admin/stops",
                "/admin/departments", "/admin/activity", "/admin/users?page=2"}) {
            mvc.perform(get(path).session(admin)).andExpect(status().isOk());
        }
        for (String view : new String[] {"ALL", "STUDENTS", "DRIVERS", "ADMINS", "UNVERIFIED", "UNFINISHED",
                "SUSPENDED", "BLOCKED"}) {
            mvc.perform(get("/admin/users?show=" + view).session(admin)).andExpect(status().isOk());
        }
        for (UUID id : new UUID[] {student, driver, unfinished, adminId, ownerId}) {
            for (String tab : new String[] {"profile", "rides", "access", "history", "nonsense"}) {
                mvc.perform(get("/admin/users/" + id + "?tab=" + tab).session(admin)).andExpect(status().isOk());
            }
        }
        mvc.perform(get("/admin/users/" + student + "/delete").session(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/users/" + UUID.randomUUID()).session(admin)).andExpect(status().isNotFound());
    }

    // ── finding people ─────────────────────────────────────────

    @Test
    void searchFindsPeopleByNameEmailMobileAndPlate() throws Exception {
        UUID id = driverUser();
        User u = users.findById(id).orElseThrow();
        String plate = drivers.findByUserId(id).orElseThrow().getVehicleNumber();
        for (String q : new String[] {u.getFirstName().toLowerCase(), u.getEmail(), u.getMobileNumber(),
                "+91 " + u.getMobileNumber().substring(0, 5) + " " + u.getMobileNumber().substring(5),
                plate.toLowerCase(), plate.substring(0, 4) + " " + plate.substring(4)}) {
            mvc.perform(get("/admin/users").param("q", q).session(admin))
                    .andExpect(content().string(containsString(u.getMobileNumber())));
        }
        mvc.perform(get("/admin/users").param("q", u.getMobileNumber()).param("show", "STUDENTS").session(admin))
                .andExpect(content().string(containsString("No one found")));
        mvc.perform(get("/admin/users").param("q", "%").session(admin))
                .andExpect(content().string(containsString("No one found")));
    }

    // ── fixing accounts ────────────────────────────────────────

    @Test
    void detailsCanBeCorrectedAndClashesAreRefused() throws Exception {
        UUID id = studentUser();
        User other = person(RoleName.STUDENT);
        save(admin, id, "Newname", "", "new-" + id + "@example.com", "9" + unique(9))
                .andExpect(flash().attribute("success", "Details saved."));
        User u = users.findById(id).orElseThrow();
        assertThat(u.getFullName()).isEqualTo("Newname");
        assertThat(u.isEmailVerified()).isTrue();

        save(admin, id, "Newname", "", other.getEmail(), u.getMobileNumber())
                .andExpect(flash().attribute("error", containsString("already uses this email")));
        save(admin, id, "Newname", "", u.getEmail(), other.getMobileNumber())
                .andExpect(flash().attribute("error", containsString("already uses this mobile")));
        save(admin, id, "", "", u.getEmail(), u.getMobileNumber())
                .andExpect(flash().attribute("error", containsString("First name")));
        save(admin, id, "X", "", "", u.getMobileNumber())
                .andExpect(flash().attribute("error", containsString("can't be left empty")));
    }

    @Test
    void studentAndDriverProfilesCanBeEditedOrFilledIn() throws Exception {
        UUID student = person(RoleName.STUDENT).getId();   // no profile yet
        mvc.perform(post("/admin/users/" + student + "/student").with(csrf()).session(admin)
                .param("departmentId", departments.findByCode("MCA").orElseThrow().getId().toString())
                .param("semester", "4")).andExpect(flash().attributeExists("success"));
        assertThat(students.findByUserId(student).orElseThrow().getSemester()).isEqualTo(4);

        UUID driver = driverUser();
        String taken = drivers.findByUserId(driverUser()).orElseThrow().getVehicleNumber();
        mvc.perform(driverForm(driver, taken)).andExpect(flash().attribute("error", containsString("another driver")));
        mvc.perform(driverForm(driver, "cg10 zx 4321")).andExpect(flash().attributeExists("success"));
        assertThat(drivers.findByUserId(driver).orElseThrow().getVehicleNumber()).isEqualTo("CG10ZX4321");
        mvc.perform(driverForm(student, "CG10ZX4322")).andExpect(flash().attribute("error", containsString("not a driver")));
    }

    @Test
    void temporaryPasswordMustBeReplacedAtNextSignIn() throws Exception {
        User u = users.findById(studentUser()).orElseThrow();
        MvcResult set = mvc.perform(post("/admin/users/" + u.getId() + "/temporary-password").with(csrf())
                .session(admin)).andReturn();
        String temporary = (String) set.getFlashMap().get("temporaryPassword");
        assertThat(temporary).matches("[a-z2-9]{4}-[a-z2-9]{4}-[a-z2-9]{4}");
        loginAttempt(u.getMobileNumber(), "river-stone-42").andExpect(redirectedUrl("/login?error"));

        MockHttpSession session = login(u.getMobileNumber(), temporary);
        mvc.perform(get("/student").session(session)).andExpect(redirectedUrl("/account/password"));
        mvc.perform(get("/account/password").session(session)).andExpect(status().isOk());
        newPassword(session, temporary).andExpect(content().string(containsString("different from the temporary")));
        newPassword(session, "brandnew123").andExpect(redirectedUrl("/dashboard"));
        mvc.perform(get("/student").session(session)).andExpect(status().isOk());
        login(u.getMobileNumber(), "brandnew123");
        mvc.perform(get("/account/password").session(session)).andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void adminCanOpenAnAccountForSomeoneWithoutEmail() throws Exception {
        String mobile = "8" + unique(9);
        MvcResult made = mvc.perform(post("/admin/users/new").with(csrf()).session(admin).param("firstName", "Ramu")
                .param("lastName", "").param("mobile", mobile).param("email", "").param("role", "DRIVER")).andReturn();
        String temporary = (String) made.getFlashMap().get("temporaryPassword");
        MockHttpSession session = login(mobile, temporary);
        newPassword(session, "brandnew123").andExpect(redirectedUrl("/dashboard"));
        mvc.perform(get("/dashboard").session(session)).andExpect(redirectedUrl("/onboarding/profile"));

        mvc.perform(post("/admin/users/new").with(csrf()).session(admin).param("firstName", "Again")
                        .param("mobile", mobile).param("role", "DRIVER"))
                .andExpect(content().string(containsString("already uses this mobile")));
        mvc.perform(post("/admin/users/new").with(csrf()).session(admin).param("firstName", "Boss")
                        .param("mobile", "8" + unique(9)).param("role", "ADMIN"))
                .andExpect(content().string(containsString("Choose Student or Driver")));
    }

    @Test
    void stuckUnverifiedAccountsCanBeVerified() throws Exception {
        String mobile = "7" + unique(9);
        UUID id = accounts.register("Stuck", "User", mobile + "@example.com", mobile, "river-stone-42");
        mvc.perform(get("/admin/users?show=UNVERIFIED").session(admin))
                .andExpect(content().string(containsString(mobile)));
        act(admin, id, "verify").andExpect(flash().attributeExists("success"));
        assertThat(users.findById(id).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
        mvc.perform(get("/dashboard").session(login(mobile, "river-stone-42"))).andExpect(redirectedUrl("/onboarding/role"));
    }

    @Test
    void googleOnlyAccountsKeepAWayIn() throws Exception {
        UUID id = accounts.signInWithGoogle("g-" + unique(6), "g" + unique(6) + "@example.com", "Gita", "G");
        act(admin, id, "disconnect-google").andExpect(flash().attribute("error", containsString("temporary password")));
        act(admin, id, "temporary-password");
        act(admin, id, "disconnect-google").andExpect(flash().attributeExists("success"));
        assertThat(users.findById(id).orElseThrow().isGoogleLinked()).isFalse();
    }

    @Test
    void roleCanBeSwitchedAndSwitchedBack() throws Exception {
        UUID id = studentUser();
        String mobile = users.findById(id).orElseThrow().getMobileNumber();
        role(admin, id, "DRIVER").andExpect(flash().attributeExists("success"));
        mvc.perform(get("/dashboard").session(login(mobile, "river-stone-42")))
                .andExpect(redirectedUrl("/onboarding/profile"));
        mvc.perform(get("/admin/users?show=UNFINISHED&q=" + mobile).session(admin))
                .andExpect(content().string(not(containsString("No one found"))));
        role(admin, id, "STUDENT").andExpect(flash().attributeExists("success"));
        mvc.perform(get("/dashboard").session(login(mobile, "river-stone-42"))).andExpect(redirectedUrl("/student"));
    }

    // ── suspend, block, delete ─────────────────────────────────

    @Test
    void suspendingFreesRidesAndStopsSignIn() throws Exception {
        UUID id = studentUser();
        String mobile = users.findById(id).orElseThrow().getMobileNumber();
        UUID request = raise(id, stop(), stop());
        mvc.perform(post("/admin/users/" + id + "/suspend").with(csrf()).session(admin).param("reason", "Spam"))
                .andExpect(flash().attributeExists("success"));
        assertThat(requests.findById(request).orElseThrow().getStatus()).isEqualTo(RequestStatus.CANCELLED);
        loginAttempt(mobile, "river-stone-42").andExpect(redirectedUrl("/login?suspended"));
        mvc.perform(get("/admin/users/" + id + "?tab=history").session(admin))
                .andExpect(content().string(containsString("Spam")));
        act(admin, id, "reactivate");
        login(mobile, "river-stone-42");
    }

    @Test
    void blockedDriverGoesOffline() throws Exception {
        UUID id = driverUser();
        mvc.perform(post("/admin/users/" + id + "/driving").with(csrf()).session(admin).param("blocked", "true"));
        DriverProfile d = drivers.findByUserId(id).orElseThrow();
        assertThat(d.isVerified()).isFalse();
        assertThat(d.isOnline()).isFalse();
        mvc.perform(get("/admin/users?show=BLOCKED&q=" + d.getVehicleNumber()).session(admin))
                .andExpect(content().string(containsString("Driving blocked")));
        mvc.perform(post("/admin/users/" + id + "/driving").with(csrf()).session(admin).param("blocked", "false"));
        assertThat(drivers.findByUserId(id).orElseThrow().isVerified()).isTrue();
    }

    @Test
    void deletingErasesThePersonButNotTheRide() throws Exception {
        UUID passenger = studentUser();
        UUID driver = driverUser();
        User before = users.findById(passenger).orElseThrow();
        Destination from = stop();
        Destination to = stop();
        UUID request = raise(passenger, from, to);
        UUID trip = matching.reserveSeats(driver, from.getId(), to.getId(), 4).tripId();
        tripService.start(driver, trip);
        jdbc.update("update student_profile set enrollment_no = ? where user_id = ?", "GGV/" + unique(6), passenger);

        mvc.perform(post("/admin/users/" + passenger + "/delete").with(csrf()).session(admin).param("confirm", "nope"))
                .andExpect(redirectedUrl("/admin/users/" + passenger + "/delete"));
        assertThat(users.findById(passenger).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);

        mvc.perform(post("/admin/users/" + passenger + "/delete").with(csrf()).session(admin).param("confirm", "DELETE"))
                .andExpect(redirectedUrl("/admin/users"));
        User erased = users.findById(passenger).orElseThrow();
        assertThat(erased.getFullName()).isEqualTo("Deleted user");
        assertThat(erased.getEmail()).isNull();
        assertThat(erased.getMobileNumber()).isNull();
        assertThat(erased.getPasswordHash()).isNull();
        assertThat(jdbc.queryForObject("select enrollment_no from student_profile where user_id = ?", String.class,
                passenger)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from admin_action where target_name = ?", Long.class,
                before.getFullName())).isZero();

        // the driver can still finish the trip, and the request is closed
        tripService.complete(driver, trip);
        assertThat(trips.findById(trip).orElseThrow().getStatus()).isEqualTo(TripStatus.COMPLETED);
        assertThat(requests.findById(request).orElseThrow().getStatus()).isEqualTo(RequestStatus.COMPLETED);

        loginAttempt(before.getMobileNumber(), "river-stone-42").andExpect(redirectedUrl("/login?error"));
        mvc.perform(get("/admin/users/" + passenger).session(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/admin/users").param("q", "Deleted user").session(admin))
                .andExpect(content().string(containsString("No one found")));
        accounts.register("Back", "Again", before.getEmail(), before.getMobileNumber(), "river-stone-42");
    }

    @Test
    void deletingADriverFreesPlateAndPassengers() throws Exception {
        UUID passenger = studentUser();
        UUID driver = driverUser();
        String plate = drivers.findByUserId(driver).orElseThrow().getVehicleNumber();
        Destination from = stop();
        Destination to = stop();
        UUID request = raise(passenger, from, to);
        matching.reserveSeats(driver, from.getId(), to.getId(), 4);
        mvc.perform(post("/admin/users/" + driver + "/delete").with(csrf()).session(admin).param("confirm", "delete"));
        assertThat(drivers.existsByVehicleNumberIgnoreCase(plate)).isFalse();
        assertThat(requests.findById(request).orElseThrow().getStatus()).isEqualTo(RequestStatus.OPEN);
    }

    // ── rides ──────────────────────────────────────────────────

    @Test
    void adminEndsForgottenRides() throws Exception {
        UUID passenger = studentUser();
        UUID driver = driverUser();
        Destination from = stop();
        Destination to = stop();
        UUID request = raise(passenger, from, to);
        UUID trip = matching.reserveSeats(driver, from.getId(), to.getId(), 4).tripId();
        tripService.start(driver, trip);
        mvc.perform(get("/admin/rides").session(admin)).andExpect(content().string(containsString(from.getName())));
        mvc.perform(get("/admin/users/" + driver + "?tab=rides").session(admin))
                .andExpect(content().string(containsString("Clear active rides")));
        mvc.perform(post("/admin/rides/trips/" + trip + "/end").with(csrf()).session(admin))
                .andExpect(redirectedUrl("/admin/rides"));
        assertThat(requests.findById(request).orElseThrow().getStatus()).isEqualTo(RequestStatus.COMPLETED);

        UUID waiting = raise(passenger, from, to);
        mvc.perform(post("/admin/rides/requests/" + waiting + "/cancel").with(csrf()).session(admin));
        assertThat(requests.findById(waiting).orElseThrow().getStatus()).isEqualTo(RequestStatus.CANCELLED);
    }

    @Test
    void clearingRidesFreesHostsAndPassengers() throws Exception {
        UUID host = studentUser();
        UUID rider = studentUser();
        Destination from = stop();
        Destination to = stop();
        java.time.LocalDateTime soon = java.time.LocalDateTime.now(clock.withZone(java.time.ZoneId.of("Asia/Kolkata")))
                .plusMinutes(20);
        UUID ride = companions.offer(host, from.getId(), to.getId(), soon, 3, "Meet at gate");
        companions.join(rider, ride, 1);
        companions.start(host, ride);
        act(admin, rider, "clear-rides").andExpect(flash().attributeExists("success"));
        assertThat(companions.joinedRide(rider)).isEmpty();
        act(admin, host, "clear-rides").andExpect(flash().attributeExists("success"));
        assertThat(companions.hostedRide(host)).isEmpty();
        act(admin, host, "clear-rides").andExpect(flash().attribute("error", containsString("no active rides")));
    }

    // ── admins managing admins ─────────────────────────────────

    @Test
    void onlyOwnersManageAdminsAndNobodyManagesTheOwner() throws Exception {
        User otherAdmin = person(RoleName.ADMIN);
        for (String action : new String[] {"temporary-password", "suspend", "sign-out"}) {
            act(admin, otherAdmin.getId(), action).andExpect(flash().attribute("error", containsString("Only the owner")));
            act(admin, ownerId, action).andExpect(flash().attribute("error", containsString("Only the owner")));
            act(admin, adminId, action).andExpect(flash().attribute("error", containsString("your own account")));
            act(owner, ownerId, action).andExpect(flash().attribute("error", containsString("your own account")));
        }
        mvc.perform(post("/admin/users/" + ownerId + "/delete").with(csrf()).session(admin).param("confirm", "DELETE"));
        assertThat(users.findById(ownerId).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);

        UUID student = studentUser();
        role(admin, student, "ADMIN").andExpect(flash().attribute("error", containsString("Only the owner")));
        role(owner, student, "ADMIN").andExpect(flash().attributeExists("success"));
        assertThat(roleOf(student)).isEqualTo(RoleName.ADMIN);
        role(owner, student, "STUDENT").andExpect(flash().attributeExists("success"));
        assertThat(roleOf(student)).isEqualTo(RoleName.STUDENT);
    }

    @Test
    void aRemovedAdminsOpenSessionIsUseless() throws Exception {
        role(owner, adminId, "STUDENT");
        UUID victim = studentUser();
        mvc.perform(post("/admin/users/" + victim + "/temporary-password").with(csrf()).session(admin))
                .andExpect(status().isForbidden());
        assertThat(users.findById(victim).orElseThrow().isPasswordChangeRequired()).isFalse();
    }

    @Test
    void stopsAndDepartmentsCanBeRenamed() throws Exception {
        Destination stop = stop();
        String name = "Renamed " + unique(6);
        mvc.perform(post("/admin/stops/" + stop.getId()).with(csrf()).session(admin).param("name", name))
                .andExpect(redirectedUrl("/admin/stops"));
        assertThat(destinations.findById(stop.getId()).orElseThrow().getName()).isEqualTo(name);
        mvc.perform(get("/admin/activity").session(admin)).andExpect(content().string(containsString(name)));
    }

    // ── guessing passwords ─────────────────────────────────────

    @Test
    void tooManyWrongPasswordsPauseSignIn() throws Exception {
        String mobile = person(RoleName.STUDENT).getMobileNumber();
        for (int i = 0; i < 10; i++) {
            loginAttempt(i % 2 == 0 ? mobile : "+91 " + mobile, "wrong").andExpect(redirectedUrl("/login?error"));
        }
        loginAttempt(mobile, "river-stone-42").andExpect(redirectedUrl("/login?locked"));
        mvc.perform(post(java.net.URI.create("/%6Cogin")).with(csrf()).param("mobile", mobile)
                .param("password", "river-stone-42")).andExpect(redirectedUrl("/login?locked"));
        login(person(null).getMobileNumber(), "river-stone-42");   // nobody else is affected
        mvc.perform(get("/login?locked")).andExpect(content().string(containsString("Wait 15 minutes")));
    }

    // ── helpers ────────────────────────────────────────────────

    private ResultActions save(MockHttpSession as, UUID id, String first, String last, String email, String mobile)
            throws Exception {
        return mvc.perform(post("/admin/users/" + id + "/account").with(csrf()).session(as).param("firstName", first)
                .param("lastName", last).param("email", email).param("mobile", mobile));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder driverForm(UUID id, String plate) {
        return (org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder)
                post("/admin/users/" + id + "/driver").session(admin).param("vehicleType", "CAR")
                        .param("vehicleNumber", plate).param("seatCapacity", "4").with(csrf());
    }

    private ResultActions act(MockHttpSession as, UUID id, String action) throws Exception {
        return mvc.perform(post("/admin/users/" + id + "/" + action).with(csrf()).session(as));
    }

    private ResultActions role(MockHttpSession as, UUID id, String role) throws Exception {
        return mvc.perform(post("/admin/users/" + id + "/role").with(csrf()).session(as).param("role", role));
    }

    private ResultActions newPassword(MockHttpSession session, String password) throws Exception {
        return mvc.perform(post("/account/password").with(csrf()).session(session).param("password", password)
                .param("confirmPassword", password));
    }

    private ResultActions loginAttempt(String login, String password) throws Exception {
        return mvc.perform(post("/login").with(csrf()).param("mobile", login).param("password", password));
    }

    private MockHttpSession login(String login, String password) throws Exception {
        return (MockHttpSession) loginAttempt(login, password).andExpect(redirectedUrl("/dashboard")).andReturn()
                .getRequest().getSession(false);
    }

    private User person(RoleName role) {
        String mobile = "6" + unique(9);
        User u = new User("Person" + mobile, "Test", mobile + "@example.com", mobile, encoder.encode("river-stone-42"));
        u.markEmailVerified(clock.instant());
        if (role != null) {
            u.assignRole(roles.findByName(role).orElseThrow());
        }
        return users.save(u);
    }

    private UUID studentUser() {
        User u = person(RoleName.STUDENT);
        students.save(new StudentProfile(u, departments.findByCode("MCA").orElseThrow(), 3));
        return u.getId();
    }

    private UUID driverUser() {
        User u = person(RoleName.DRIVER);
        DriverProfile d = new DriverProfile(u, VehicleType.AUTO, "CG10T" + unique(4), 4, "Koni");
        d.setAvailability(Availability.ONLINE);
        drivers.save(d);
        return u.getId();
    }

    private UUID raise(UUID student, Destination from, Destination to) {
        return requestService.create(student, from.getId(), to.getId(), RequestType.ANY, 1);
    }

    private Destination stop() {
        return destinations.save(new Destination("Admin stop " + unique(6), null, LocationType.CITY, 1));
    }

    private RoleName roleOf(UUID id) {
        return users.findByIdWithRole(id).orElseThrow().getRole().getName();
    }

    private static String unique(int digits) {
        return String.format("%0" + digits + "d", SEQ.getAndIncrement());
    }
}
