package com.myhealth.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myhealth.auth.CurrentUser;
import com.myhealth.auth.JwtAuthenticationFilter;
import com.myhealth.common.GlobalExceptionHandler;
import com.myhealth.notification.NotificationDtos.NotificationFeed;
import com.myhealth.notification.NotificationDtos.NotificationItem;
import com.myhealth.user.AppUser;
import com.myhealth.user.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = NotificationController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean NotificationService notificationService;
    @MockBean CurrentUser currentUser;

    private AppUser stubUser() {
        AppUser u = new AppUser();
        u.setEmail("u@example.com");
        u.setRole(Role.USER);
        return u;
    }

    private NotificationFeed sample(int unread) {
        return new NotificationFeed(List.of(new NotificationItem(
                "ach:STREAK_7", "ACHIEVEMENT", "解鎖成就：一週不間斷", "連續 7 天記錄健康數據",
                "🔥", "success", Instant.parse("2026-06-06T02:00:00Z"), "/", unread == 0)), unread);
    }

    @Test
    void list_returns200_withFeedAndUnreadCount() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(notificationService.getFeed(any())).thenReturn(sample(1));

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].type").value("ACHIEVEMENT"))
                .andExpect(jsonPath("$.items[0].read").value(false));
    }

    @Test
    void markRead_returns200_andClearsUnread() throws Exception {
        AppUser user = stubUser();
        when(currentUser.require()).thenReturn(user);
        when(notificationService.markRead(any())).thenReturn(sample(0));

        mockMvc.perform(post("/api/v1/notifications/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.items[0].read").value(true));

        verify(notificationService).markRead(user);
    }
}
