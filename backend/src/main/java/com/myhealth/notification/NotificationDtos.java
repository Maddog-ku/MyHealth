package com.myhealth.notification;

import java.time.Instant;
import java.util.List;

public final class NotificationDtos {
    private NotificationDtos() {
    }

    /**
     * One feed entry — either a persisted achievement unlock or a live reminder.
     *
     * @param key        stable identifier (e.g. {@code ach:STREAK_7} / {@code meal:2026-06-06})
     * @param type       ACHIEVEMENT | MEAL_REMINDER | STREAK_RISK | WEIGHT_REMINDER
     * @param severity   success | info | warning (drives the UI accent)
     * @param actionHref in-app route to act on it, or null
     * @param read       whether the user has seen it (createdAt &le; their last-read watermark)
     */
    public record NotificationItem(
            String key,
            String type,
            String title,
            String body,
            String emoji,
            String severity,
            Instant createdAt,
            String actionHref,
            boolean read
    ) {
    }

    public record NotificationFeed(List<NotificationItem> items, int unreadCount) {
    }
}
