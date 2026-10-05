package in.ggv.campora.profile.domain;

import org.hibernate.annotations.SQLRestriction;

import in.ggv.campora.common.domain.BaseEntity;
import in.ggv.campora.identity.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "driver_profile")
@SQLRestriction("deleted = false")
public class DriverProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "vehicle_number", nullable = false, unique = true, length = 20)
    private String vehicleNumber;

    @Column(name = "seat_capacity", nullable = false)
    private int seatCapacity;

    @Column(name = "native_area", length = 120)
    private String nativeArea;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability", nullable = false, length = 12)
    private Availability availability = Availability.OFFLINE;

    /** Only admin-verified drivers can see demand and reserve seats. */
    @Column(name = "verified", nullable = false)
    private boolean verified = false;

    protected DriverProfile() {
    }

    public DriverProfile(User user, VehicleType vehicleType, String vehicleNumber, int seatCapacity, String nativeArea) {
        this.user = user;
        this.vehicleType = vehicleType;
        this.vehicleNumber = vehicleNumber;
        this.seatCapacity = seatCapacity;
        this.nativeArea = nativeArea;
    }

    public void setAvailability(Availability availability) {
        this.availability = availability;
    }

    public void verify() {
        this.verified = true;
    }

    public void revokeVerification() {
        this.verified = false;
        this.availability = Availability.OFFLINE;
    }

    public boolean isOnline() {
        return availability == Availability.ONLINE;
    }

    public User getUser() {
        return user;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public int getSeatCapacity() {
        return seatCapacity;
    }

    public String getNativeArea() {
        return nativeArea;
    }

    public Availability getAvailability() {
        return availability;
    }

    public boolean isVerified() {
        return verified;
    }
}
