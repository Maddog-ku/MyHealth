package com.myhealth.streak;

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
import java.time.Instant;

@Entity
@Table(name = "achievements")
public class Achievement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false)
    private String code;

    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt = Instant.now();

    protected Achievement() {
    }

    public Achievement(AppUser user, String code) {
        this.user = user;
        this.code = code;
        this.unlockedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getCode() {
        return code;
    }

    public Instant getUnlockedAt() {
        return unlockedAt;
    }
}
