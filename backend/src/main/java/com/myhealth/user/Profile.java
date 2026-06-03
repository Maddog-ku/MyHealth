package com.myhealth.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "profiles")
public class Profile {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(name = "height_cm", nullable = false)
    private BigDecimal heightCm;

    @Column(name = "weight_kg", nullable = false)
    private BigDecimal weightKg;

    private Integer age;

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

    @Enumerated(EnumType.STRING)
    private Goal goal;

    @Column(columnDefinition = "text[]")
    private String[] equipment;

    @Enumerated(EnumType.STRING)
    private Experience experience;

    /** Preferred assistant avatar: "male" / "female". Null means follow {@link #gender}. */
    @Column(name = "assistant_avatar")
    private String assistantAvatar;

    @Column(nullable = false)
    private String theme = "system";

    @Column(nullable = false)
    private String language = "zh-TW";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getUserId() {
        return userId;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public BigDecimal getBodyFatPct() {
        return bodyFatPct;
    }

    public void setBodyFatPct(BigDecimal bodyFatPct) {
        this.bodyFatPct = bodyFatPct;
    }

    public BigDecimal getMuscleMassKg() {
        return muscleMassKg;
    }

    public void setMuscleMassKg(BigDecimal muscleMassKg) {
        this.muscleMassKg = muscleMassKg;
    }

    public Integer getBmrKcal() {
        return bmrKcal;
    }

    public void setBmrKcal(Integer bmrKcal) {
        this.bmrKcal = bmrKcal;
    }

    public BigDecimal getWaistCm() {
        return waistCm;
    }

    public void setWaistCm(BigDecimal waistCm) {
        this.waistCm = waistCm;
    }

    public BigDecimal getBodyWaterPct() {
        return bodyWaterPct;
    }

    public void setBodyWaterPct(BigDecimal bodyWaterPct) {
        this.bodyWaterPct = bodyWaterPct;
    }

    public Goal getGoal() {
        return goal;
    }

    public void setGoal(Goal goal) {
        this.goal = goal;
    }

    public String[] getEquipment() {
        return equipment;
    }

    public void setEquipment(String[] equipment) {
        this.equipment = equipment;
    }

    public Experience getExperience() {
        return experience;
    }

    public void setExperience(Experience experience) {
        this.experience = experience;
    }

    public String getAssistantAvatar() {
        return assistantAvatar;
    }

    public void setAssistantAvatar(String assistantAvatar) {
        this.assistantAvatar = assistantAvatar;
    }

    /** The avatar actually shown: explicit preference, else derived from gender. */
    public String resolvedAssistantAvatar() {
        if (assistantAvatar != null && !assistantAvatar.isBlank()) {
            return assistantAvatar;
        }
        return gender == Gender.male ? "male" : "female";
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        updatedAt = Instant.now();
    }
}
