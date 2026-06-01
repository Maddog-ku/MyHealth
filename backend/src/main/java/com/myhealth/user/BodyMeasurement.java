package com.myhealth.user;

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

@Entity
@Table(name = "body_measurements")
public class BodyMeasurement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt = Instant.now();

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "body_fat_pct")
    private BigDecimal bodyFatPct;

    @Column(name = "muscle_mass_kg")
    private BigDecimal muscleMassKg;

    @Column(name = "bmr_kcal")
    private Integer bmrKcal;

    @Column(name = "waist_cm")
    private BigDecimal waistCm;

    @Column(name = "body_water_pct")
    private BigDecimal bodyWaterPct;

    private String note;

    public Instant getMeasuredAt() {
        return measuredAt;
    }

    public void setMeasuredAt(Instant measuredAt) {
        this.measuredAt = measuredAt;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public BigDecimal getBodyFatPct() {
        return bodyFatPct;
    }

    public BigDecimal getMuscleMassKg() {
        return muscleMassKg;
    }

    public BigDecimal getWaistCm() {
        return waistCm;
    }

    public BigDecimal getBodyWaterPct() {
        return bodyWaterPct;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public void setBodyFatPct(BigDecimal bodyFatPct) {
        this.bodyFatPct = bodyFatPct;
    }

    public void setMuscleMassKg(BigDecimal muscleMassKg) {
        this.muscleMassKg = muscleMassKg;
    }

    public void setBmrKcal(Integer bmrKcal) {
        this.bmrKcal = bmrKcal;
    }

    public void setWaistCm(BigDecimal waistCm) {
        this.waistCm = waistCm;
    }

    public void setBodyWaterPct(BigDecimal bodyWaterPct) {
        this.bodyWaterPct = bodyWaterPct;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
