package in.ggv.campora.identity.domain;

import org.hibernate.annotations.SQLRestriction;

import in.ggv.campora.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_role")
@SQLRestriction("deleted = false")
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 20)
    private RoleName name;

    @Column(name = "description", length = 200)
    private String description;

    protected Role() {
    }

    public RoleName getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
