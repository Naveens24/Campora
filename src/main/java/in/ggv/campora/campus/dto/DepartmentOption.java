package in.ggv.campora.campus.dto;

import java.util.UUID;

import in.ggv.campora.campus.domain.Department;

public record DepartmentOption(UUID id, String code, String name, boolean active) {

    public static DepartmentOption of(Department d) {
        return new DepartmentOption(d.getId(), d.getCode(), d.getName(), d.isActive());
    }
}
