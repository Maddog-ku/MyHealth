package com.myhealth.workout;

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

/** A user's weekly training-frequency commitment (one per user). */
@Entity
@Table(name = "workout_goals")
public class WorkoutGoal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "target_sessions_per_week", nullable = false)
    private int targetSessionsPerWeek;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected WorkoutGoal() {
    }

    public WorkoutGoal(AppUser user, int targetSessionsPerWeek) {
        this.user = user;
        this.targetSessionsPerWeek = targetSessionsPerWeek;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public int getTargetSessionsPerWeek() {
        return targetSessionsPerWeek;
    }

    public void setTargetSessionsPerWeek(int targetSessionsPerWeek) {
        this.targetSessionsPerWeek = targetSessionsPerWeek;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
