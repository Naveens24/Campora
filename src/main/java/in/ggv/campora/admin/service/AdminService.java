package in.ggv.campora.admin.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.ggv.campora.admin.dto.AdminStats;
import in.ggv.campora.campus.domain.DestinationRequestStatus;
import in.ggv.campora.campus.domain.LocationType;
import in.ggv.campora.campus.repository.DestinationRequestRepository;
import in.ggv.campora.campus.service.CampusService;
import in.ggv.campora.campus.service.DestinationRequestService;
import in.ggv.campora.common.config.CamporaProperties;
import in.ggv.campora.companion.domain.RideStatus;
import in.ggv.campora.companion.repository.CompanionRideRepository;
import in.ggv.campora.identity.domain.RoleName;
import in.ggv.campora.identity.domain.UserStatus;
import in.ggv.campora.identity.repository.UserRepository;
import in.ggv.campora.mobility.domain.RequestStatus;
import in.ggv.campora.mobility.repository.TravelRequestRepository;
import in.ggv.campora.profile.domain.Availability;
import in.ggv.campora.profile.repository.DriverProfileRepository;
import in.ggv.campora.trip.domain.TripStatus;
import in.ggv.campora.trip.repository.TripRepository;

/** Overview numbers, and logged changes to stops and departments. */
@Service
public class AdminService {

    private final UserRepository users;
    private final DriverProfileRepository drivers;
    private final TravelRequestRepository requests;
    private final TripRepository trips;
    private final CompanionRideRepository rides;
    private final DestinationRequestRepository proposals;
    private final DestinationRequestService proposalService;
    private final CampusService campus;
    private final AdminGuard guard;
    private final AdminLog log;
    private final ZoneId zone;
    private final Clock clock;

    public AdminService(UserRepository users, DriverProfileRepository drivers, TravelRequestRepository requests,
                        TripRepository trips, CompanionRideRepository rides, DestinationRequestRepository proposals,
                        DestinationRequestService proposalService, CampusService campus, AdminGuard guard,
                        AdminLog log, CamporaProperties properties, Clock clock) {
        this.users = users;
        this.drivers = drivers;
        this.requests = requests;
        this.trips = trips;
        this.rides = rides;
        this.proposals = proposals;
        this.proposalService = proposalService;
        this.campus = campus;
        this.guard = guard;
        this.log = log;
        this.zone = properties.zoneId();
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminStats stats() {
        Instant startOfDay = LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant();
        return new AdminStats(
                users.countByRole(RoleName.STUDENT),
                users.countByRole(RoleName.DRIVER),
                drivers.countByAvailabilityAndVerifiedTrue(Availability.ONLINE),
                requests.countByStatus(RequestStatus.OPEN),
                trips.countByStatusIn(TripStatus.ACTIVE),
                trips.countByStatusAndEndTimeAfter(TripStatus.COMPLETED, startOfDay),
                rides.countByStatusIn(RideStatus.ACTIVE),
                pendingSuggestions(),
                users.countByStatus(UserStatus.PENDING_VERIFICATION),
                users.countByStatus(UserStatus.SUSPENDED));
    }

    @Transactional(readOnly = true)
    public long pendingSuggestions() {
        return proposals.countByStatus(DestinationRequestStatus.PENDING);
    }

    @Transactional
    public void approveSuggestion(UUID adminId, UUID id, LocationType type) {
        proposalService.approve(id, type);
        note(adminId, "Approved a stop suggestion", proposalName(id));
    }

    @Transactional
    public void rejectSuggestion(UUID adminId, UUID id, String note) {
        proposalService.reject(id, note);
        note(adminId, "Rejected a stop suggestion", proposalName(id));
    }

    @Transactional
    public void addStop(UUID adminId, String name, String area, LocationType type) {
        campus.addLocation(name, area, type);
        note(adminId, "Added a stop", name.trim());
    }

    @Transactional
    public void renameStop(UUID adminId, UUID id, String name, String area) {
        campus.renameLocation(id, name, area);
        note(adminId, "Edited a stop", name.trim());
    }

    @Transactional
    public void toggleStop(UUID adminId, UUID id) {
        boolean active = campus.toggleLocation(id);
        note(adminId, active ? "Resumed a stop" : "Paused a stop", campus.locationName(id));
    }

    @Transactional
    public void addDepartment(UUID adminId, String code, String name) {
        campus.addDepartment(code, name);
        note(adminId, "Added a department", name.trim());
    }

    @Transactional
    public void renameDepartment(UUID adminId, UUID id, String name) {
        campus.renameDepartment(id, name);
        note(adminId, "Edited a department", name.trim());
    }

    @Transactional
    public void toggleDepartment(UUID adminId, UUID id) {
        boolean active = campus.toggleDepartment(id);
        note(adminId, active ? "Resumed a department" : "Paused a department", campus.departmentName(id));
    }

    private void note(UUID adminId, String summary, String what) {
        log.record(guard.admin(adminId), null, summary, what);
    }

    private String proposalName(UUID id) {
        return proposals.findById(id).map(p -> p.getProposedName()).orElse(null);
    }
}
