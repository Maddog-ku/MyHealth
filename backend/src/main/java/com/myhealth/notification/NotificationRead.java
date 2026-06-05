package com.myhealth.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** "Last opened the notification center" watermark, one row per user (user_id is the PK). */
@Entity
@Table(name = "notification_reads")
public class NotificationRead {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "last_read_at", nullable = false)
    private Instant lastReadAt = Instant.now();

    protected NotificationRead() {
    }

    public NotificationRead(Long userId, Instant lastReadAt) {
        this.userId = userId;
        this.lastReadAt = lastReadAt;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getLastReadAt() {
        return lastReadAt;
    }

    public void setLastReadAt(Instant lastReadAt) {
        this.lastReadAt = lastReadAt;
    }
}
