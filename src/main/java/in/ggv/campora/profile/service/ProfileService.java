package in.ggv.campora.profile.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.common.exception.NotFoundException;
import in.ggv.campora.profile.domain.Availability;
import in.ggv.campora.profile.domain.DriverProfile;
import in.ggv.campora.profile.domain.StudentProfile;
import in.ggv.campora.profile.dto.DriverStatusView;
import in.ggv.campora.profile.repository.DriverProfileRepository;
import in.ggv.campora.profile.repository.StudentProfileRepository;

/** Resolves the signed-in user's role profile; used by every role-specific service. */
@Service
@Transactional(readOnly = true)
public class ProfileService {

    private final StudentProfileRepository students;
    private final DriverProfileRepository drivers;

    public ProfileService(StudentProfileRepository students, DriverProfileRepository drivers) {
        this.students = students;
        this.drivers = drivers;
    }

    public StudentProfile requireStudent(UUID userId) {
        return students.findByUserId(userId).orElseThrow(() -> new NotFoundException("Student profile"));
    }

    public StudentProfile requireStudentDetailed(UUID userId) {
        return students.findDetailedByUserId(userId).orElseThrow(() -> new NotFoundException("Student profile"));
    }

    public DriverProfile requireDriver(UUID userId) {
        return drivers.findByUserId(userId).orElseThrow(() -> new NotFoundException("Driver profile"));
    }

    public DriverProfile requireDriverDetailed(UUID userId) {
        return drivers.findDetailedByUserId(userId).orElseThrow(() -> new NotFoundException("Driver profile"));
    }

    public DriverStatusView driverStatus(UUID userId) {
        DriverProfile d = requireDriverDetailed(userId);
        return new DriverStatusView(d.getUser().getFullName(), d.getVehicleType().getLabel(), d.getVehicleNumber(),
                d.getSeatCapacity(), d.isVerified(), d.isOnline());
    }

    @Transactional
    public Availability setAvailability(UUID userId, Availability availability) {
        DriverProfile driver = requireDriver(userId);
        if (availability == Availability.ONLINE && !driver.isVerified()) {
            throw new BusinessException(
                    "Your account is awaiting verification by the transport office.");
        }
        driver.setAvailability(availability);
        return availability;
    }
}
