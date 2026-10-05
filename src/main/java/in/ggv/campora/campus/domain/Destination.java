package in.ggv.campora.campus.domain;

import org.hibernate.annotations.SQLRestriction;

import in.ggv.campora.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A named stop in the network. Used both as pickup point and destination,
 * so a route is always a (stop, stop) pair — which is exactly the key the
 * demand engine clusters on.
 */
@Entity
@Table(name = "destination")
@SQLRestriction("deleted = false")
public class Destination extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 120)
    private String name;

    @Column(name = "area", length = 120)
    private String area;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private LocationType type;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    protected Destination() {
    }

    public Destination(String name, String area, LocationType type, int sortOrder) {
        this.name = name;
        this.area = area;
        this.type = type;
        this.sortOrder = sortOrder;
    }

    public void toggleActive() {
        this.active = !this.active;
    }

    public String getName() {
        return name;
    }

    public String getArea() {
        return area;
    }

    public LocationType getType() {
        return type;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
