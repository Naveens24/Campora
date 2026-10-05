package in.ggv.campora.campus.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.ggv.campora.campus.domain.Department;
import in.ggv.campora.campus.domain.Destination;
import in.ggv.campora.campus.domain.LocationType;
import in.ggv.campora.campus.dto.DepartmentOption;
import in.ggv.campora.campus.dto.LocationOption;
import in.ggv.campora.campus.repository.DepartmentRepository;
import in.ggv.campora.campus.repository.DestinationRepository;
import in.ggv.campora.common.config.CacheConfig;
import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.common.exception.NotFoundException;

/** Reference data: the stop network and the list of departments. */
@Service
@Transactional(readOnly = true)
public class CampusService {

    private final DestinationRepository destinations;
    private final DepartmentRepository departments;

    public CampusService(DestinationRepository destinations, DepartmentRepository departments) {
        this.destinations = destinations;
        this.departments = departments;
    }

    @Cacheable(CacheConfig.LOCATIONS)
    public List<LocationOption> activeLocations() {
        return destinations.findByActiveTrueOrderByTypeAscSortOrderAscNameAsc()
                .stream().map(LocationOption::of).toList();
    }

    public List<LocationOption> allLocations() {
        return destinations.findAllByOrderByTypeAscSortOrderAscNameAsc()
                .stream().map(LocationOption::of).toList();
    }

    @Cacheable(CacheConfig.DEPARTMENTS)
    public List<DepartmentOption> activeDepartments() {
        return departments.findByActiveTrueOrderByNameAsc()
                .stream().map(DepartmentOption::of).toList();
    }

    public List<DepartmentOption> allDepartments() {
        return departments.findAllByOrderByNameAsc()
                .stream().map(DepartmentOption::of).toList();
    }

    /** Loads a stop that is currently in service, for use in a new request or ride. */
    public Destination requireActiveLocation(UUID id) {
        if (id == null) {
            throw new BusinessException("Please choose a stop.");
        }
        return destinations.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new BusinessException("That stop is not in service any more."));
    }

    public Department requireActiveDepartment(UUID id) {
        if (id == null) {
            throw new BusinessException("Please choose your department.");
        }
        Department department = departments.findById(id)
                .orElseThrow(() -> new NotFoundException("Department"));
        if (!department.isActive()) {
            throw new BusinessException("That department is not accepting registrations.");
        }
        return department;
    }

    public boolean locationExists(String name) {
        return destinations.existsByNameIgnoreCase(name.trim());
    }

    // ── admin writes (evict caches) ───────────────────────────

    @Transactional
    @CacheEvict(value = CacheConfig.LOCATIONS, allEntries = true)
    public Destination addLocation(String name, String area, LocationType type) {
        String clean = name.trim();
        if (destinations.existsByNameIgnoreCase(clean)) {
            throw new BusinessException("A stop named \"" + clean + "\" already exists.");
        }
        int order = type == LocationType.CAMPUS_POINT ? 50 : 150;
        return destinations.save(new Destination(clean, blankToNull(area), type, order));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.LOCATIONS, allEntries = true)
    public void toggleLocation(UUID id) {
        destinations.findById(id).orElseThrow(() -> new NotFoundException("Stop")).toggleActive();
    }

    @Transactional
    @CacheEvict(value = CacheConfig.DEPARTMENTS, allEntries = true)
    public void addDepartment(String code, String name) {
        String cleanCode = code.trim().toUpperCase(Locale.ROOT);
        if (departments.existsByCodeIgnoreCase(cleanCode)) {
            throw new BusinessException("Department code " + cleanCode + " is already used.");
        }
        departments.save(new Department(cleanCode, name.trim()));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.DEPARTMENTS, allEntries = true)
    public void toggleDepartment(UUID id) {
        departments.findById(id).orElseThrow(() -> new NotFoundException("Department")).toggleActive();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
