package in.ggv.campora.campus.dto;

import java.util.UUID;

import in.ggv.campora.campus.domain.Destination;
import in.ggv.campora.campus.domain.LocationType;

/** Immutable, cache-friendly view of a stop for select boxes. */
public record LocationOption(UUID id, String name, String area, LocationType type, boolean active) {

    public static LocationOption of(Destination d) {
        return new LocationOption(d.getId(), d.getName(), d.getArea(), d.getType(), d.isActive());
    }

    public boolean onCampus() {
        return type == LocationType.CAMPUS_POINT;
    }
}
