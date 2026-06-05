package com.myhealth.report;

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
import java.time.LocalDate;

@Entity
@Table(name = "weekly_reports")
public class WeeklyReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column
    private String model;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected WeeklyReport() {
    }

    public WeeklyReport(AppUser user, LocalDate weekStart, String content, String model) {
        this.user = user;
        this.weekStart = weekStart;
        this.content = content;
        this.model = model;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
