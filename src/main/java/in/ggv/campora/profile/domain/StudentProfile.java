package in.ggv.campora.profile.domain;

import org.hibernate.annotations.SQLRestriction;

import in.ggv.campora.campus.domain.Department;
import in.ggv.campora.common.domain.BaseEntity;
import in.ggv.campora.identity.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_profile")
@SQLRestriction("deleted = false")
public class StudentProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "enrollment_no", nullable = false, unique = true, length = 32)
    private String enrollmentNo;

    @Column(name = "semester", nullable = false)
    private int semester;

    protected StudentProfile() {
    }

    public StudentProfile(User user, Department department, String enrollmentNo, int semester) {
        this.user = user;
        this.department = department;
        this.enrollmentNo = enrollmentNo;
        this.semester = semester;
    }

    public User getUser() {
        return user;
    }

    public Department getDepartment() {
        return department;
    }

    public String getEnrollmentNo() {
        return enrollmentNo;
    }

    public int getSemester() {
        return semester;
    }
}
