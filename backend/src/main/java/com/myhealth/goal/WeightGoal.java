package com.myhealth.goal;

import com.myhealth.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "weight_goals")
public class WeightGoal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "target_weight_kg", nullable = false)
    private BigDecimal targetWeightKg;

    @Column(name = "start_weight_kg", nullable = false)
    private BigDecimal startWeightKg;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected WeightGoal() {
    }

    public WeightGoal(AppUser user, BigDecimal targetWeightKg, BigDecimal startWeightKg,
                      LocalDate startDate, LocalDate targetDate) {
        this.user = user;
        this.targetWeightKg = targetWeightKg;
        this.startWeightKg = startWeightKg;
        this.startDate = startDate;
        this.targetDate = targetDate;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public BigDecimal getTargetWeightKg() {
        return targetWeightKg;
    }

    public void setTargetWeightKg(BigDecimal targetWeightKg) {
        this.targetWeightKg = targetWeightKg;
    }

    public BigDecimal getStartWeightKg() {
        return startWeightKg;
    }

    public void setStartWeightKg(BigDecimal startWeightKg) {
        this.startWeightKg = startWeightKg;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
