package in.ggv.campora.seed;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import in.ggv.campora.campus.domain.Department;
import in.ggv.campora.campus.domain.Destination;
import in.ggv.campora.campus.repository.DepartmentRepository;
import in.ggv.campora.campus.repository.DestinationRepository;
import in.ggv.campora.common.config.CamporaProperties;
import in.ggv.campora.companion.domain.CompanionRide;
import in.ggv.campora.companion.repository.CompanionRideRepository;
import in.ggv.campora.identity.domain.Role;
import in.ggv.campora.identity.domain.RoleName;
import in.ggv.campora.identity.domain.User;
import in.ggv.campora.identity.repository.RoleRepository;
import in.ggv.campora.identity.repository.UserRepository;
import in.ggv.campora.mobility.domain.RequestType;
import in.ggv.campora.mobility.domain.TravelRequest;
import in.ggv.campora.mobility.repository.TravelRequestRepository;
import in.ggv.campora.profile.domain.Availability;
import in.ggv.campora.profile.domain.DriverProfile;
import in.ggv.campora.profile.domain.StudentProfile;
import in.ggv.campora.profile.domain.VehicleType;
import in.ggv.campora.profile.repository.DriverProfileRepository;
import in.ggv.campora.profile.repository.StudentProfileRepository;

/**
 * Seeds demo accounts and live demand on first start (dev profile only),
 * reproducing the design-document scenario: ten seats of demand from
 * Engineering Block to Rajkishore Nagar, ready for two drivers to absorb.
 *
 * <pre>
 *  Admin     9000000001 / admin@123
 *  Student   9000000011 / student@123   (no request yet — try the flow)
 *  Drivers   9000000021, 9000000022 / driver@123  (verified, online)
 *            9000000023 / driver@123  (awaiting verification)
 * </pre>
 */
@Component
@ConditionalOnProperty(prefix = "campora", name = "seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String[][] STUDENTS = {
            // mobile, first, last, dept code
            {"9000000011", "Aarav", "Sharma", "MCA"},
            {"9000000012", "Priya", "Verma", "CSIT"},
            {"9000000013", "Rohit", "Sahu", "ECE"},
            {"9000000014", "Sneha", "Patel", "ME"},
            {"9000000015", "Aditya", "Singh", "CE"},
            {"9000000016", "Kavya", "Dewangan", "CSIT"},
            {"9000000017", "Manish", "Yadav", "MGMT"},
            {"9000000018", "Ananya", "Mishra", "PHY"},
            {"9000000019", "Ishita", "Tiwari", "MCA"},
    };

    private final UserRepository users;
    private final RoleRepository roles;
    private final DepartmentRepository departments;
    private final DestinationRepository destinations;
    private final StudentProfileRepository students;
    private final DriverProfileRepository drivers;
    private final TravelRequestRepository requests;
    private final CompanionRideRepository rides;
    private final PasswordEncoder encoder;
    private final CamporaProperties properties;
    private final Clock clock;

    public DemoDataSeeder(UserRepository users, RoleRepository roles, DepartmentRepository departments,
                          DestinationRepository destinations, StudentProfileRepository students,
                          DriverProfileRepository drivers, TravelRequestRepository requests,
                          CompanionRideRepository rides, PasswordEncoder encoder,
                          CamporaProperties properties, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.departments = departments;
        this.destinations = destinations;
        this.students = students;
        this.drivers = drivers;
        this.requests = requests;
        this.rides = rides;
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        Instant now = clock.instant();

        user("9000000001", "Campus", "Admin", "admin@123", RoleName.ADMIN, now);

        StudentProfile[] s = new StudentProfile[STUDENTS.length];
        for (int i = 0; i < STUDENTS.length; i++) {
            String[] row = STUDENTS[i];
            User u = user(row[0], row[1], row[2], "student@123", RoleName.STUDENT, now);
            Department dept = departments.findByCode(row[3]).orElseThrow();
            s[i] = students.save(new StudentProfile(u, dept, "GGV/24/" + (1001 + i), 3));
        }

        driver("9000000021", "Ramesh", "Kumar", VehicleType.AUTO, "CG10AB1234", 5, "Sarkanda", true, now);
        driver("9000000022", "Suresh", "Patel", VehicleType.E_RICKSHAW, "CG10CD5678", 5, "Koni", true, now);
        driver("9000000023", "Mahesh", "Verma", VehicleType.AUTO, "CG10EF9012", 4, "Nehru Chowk", false, now);

        // The design-doc hotspot: 10 seats of demand on one route.
        Destination engineering = stop("Engineering Block");
        Destination rajkishore = stop("Rajkishore Nagar");
        int[] seats = {2, 2, 1, 2, 2, 1};
        Instant expiry = now.plus(properties.mobility().requestTtl());
        for (int i = 0; i < seats.length; i++) {
            requests.save(new TravelRequest(s[i + 1], engineering, rajkishore, RequestType.ANY, seats[i], expiry));
        }
        // A smaller second hotspot.
        requests.save(new TravelRequest(s[7], stop("Main Gate"), stop("Bilaspur Railway Station"),
                RequestType.AUTO, 1, expiry));

        // A companion ride anyone can join.
        rides.save(new CompanionRide(s[8], stop("Main Gate"), stop("Old Bus Stand"),
                now.plus(Duration.ofMinutes(45)), 3, "Scooty + friend's car, leaving from the gate"));

        log.info("Demo data seeded — sign in with 9000000011 / student@123 or 9000000021 / driver@123");
    }

    private User user(String mobile, String first, String last, String password, RoleName role, Instant now) {
        User user = new User(first, last, mobile, encoder.encode(password));
        user.markMobileVerified(now);
        Role r = roles.findByName(role).orElseThrow();
        user.assignRole(r);
        return users.save(user);
    }

    private void driver(String mobile, String first, String last, VehicleType type, String plate, int capacity,
                        String area, boolean verified, Instant now) {
        User u = user(mobile, first, last, "driver@123", RoleName.DRIVER, now);
        DriverProfile d = new DriverProfile(u, type, plate, capacity, area);
        if (verified) {
            d.verify();
            d.setAvailability(Availability.ONLINE);
        }
        drivers.save(d);
    }

    private Destination stop(String name) {
        return destinations.findByName(name).orElseThrow(() -> new IllegalStateException("Missing stop " + name));
    }
}
