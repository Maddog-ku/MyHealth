package com.myhealth.notification;

import com.myhealth.auth.CurrentUser;
import com.myhealth.notification.NotificationDtos.NotificationFeed;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final CurrentUser currentUser;
    private final NotificationService notificationService;

    public NotificationController(CurrentUser currentUser, NotificationService notificationService) {
        this.currentUser = currentUser;
        this.notificationService = notificationService;
    }

    /** The full feed (achievement unlocks + live reminders) plus the unread count. */
    @GetMapping
    NotificationFeed list() {
        return notificationService.getFeed(currentUser.require());
    }

    /** Mark everything currently shown as read; returns the refreshed feed (unreadCount 0). */
    @PostMapping("/read")
    NotificationFeed markRead() {
        return notificationService.markRead(currentUser.require());
    }
}
