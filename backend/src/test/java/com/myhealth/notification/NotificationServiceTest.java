package com.myhealth.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.myhealth.meal.MealRepository;
import com.myhealth.notification.NotificationDtos.NotificationFeed;
import com.myhealth.notification.NotificationDtos.NotificationItem;
import com.myhealth.stats.StatsDtos.CalorieBudgetResponse;
import com.myhealth.stats.StatsDtos.MacroBudget;
import com.myhealth.stats.StatsService;
import com.myhealth.streak.Achievement;
import com.myhealth.streak.AchievementRepository;
import com.myhealth.streak.StreakDtos.StreakInfo;
import com.myhealth.streak.StreakService;
import com.myhealth.user.AppUser;
import com.myhealth.user.BodyMeasurement;
import com.myhealth.user.BodyMeasurementRepository;
import com.myhealth.user.Role;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock AchievementRepository achievements;
    @Mock StreakService streakService;
    @Mock MealRepository meals;
    @Mock BodyMeasurementRepository bodyMeasurements;
    @Mock StatsService stats;
    @Mock com.myhealth.report.WeeklyReportRepository weeklyReports;
    @Mock NotificationReadRepository reads;

    TransactionTemplate transactionTemplate;
    NotificationService service;
    AppUser user;

    private static final LocalDate TODAY = LocalDate.now();

    @BeforeEach
    void setUp() {
        transactionTemplate = mock(TransactionTemplate.class);
        service = new NotificationService(achievements, streakService, meals, bodyMeasurements,
                stats, weeklyReports, reads, transactionTemplate);
        user = new AppUser();
        user.setEmail("a@b.c");
        user.setRole(Role.USER);
        setId(user, 1L);

        // Defaults = a "healthy" user with nothing to nag about.
        lenient().when(reads.findById(1L)).thenReturn(Optional.empty());
        lenient().when(achievements.findByUserId(1L)).thenReturn(List.of());
        lenient().when(meals.existsByUserIdAndDate(eq(1L), any())).thenReturn(true);
        lenient().when(streakService.overallStreak(user)).thenReturn(new StreakInfo(5, 5, TODAY));
        lenient().when(bodyMeasurements.findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(eq(1L), any()))
                .thenReturn(Optional.of(measurement(TODAY)));
        // Healthy protein attainment by default → no protein nudge.
        lenient().when(stats.budget(eq(user), any())).thenReturn(budget(80));
        // Last week's report already exists by default → no report nudge.
        lenient().when(weeklyReports.findByUserIdAndWeekStart(eq(1L), any()))
                .thenReturn(Optional.of(new com.myhealth.report.WeeklyReport(user, TODAY, "x", "model")));
    }

    /** Calorie budget whose only relevant field here is the protein attainment percent. */
    private CalorieBudgetResponse budget(int proteinPct) {
        return new CalorieBudgetResponse(TODAY, 1700, 1200, 0, 1700, 500, 70, false,
                List.of(new MacroBudget("protein", 120, 120 * proteinPct / 100, proteinPct)));
    }

    private NotificationItem byType(List<NotificationItem> items, String type) {
        return items.stream().filter(i -> i.type().equals(type)).findFirst().orElse(null);
    }

    @Test
    void getFeed_returnsEmpty_whenHealthyAndNoAchievements() {
        NotificationFeed feed = service.getFeed(user);
        assertThat(feed.items()).isEmpty();
        assertThat(feed.unreadCount()).isZero();
    }

    @Test
    void getFeed_buildsAchievementAndReminders_andCountsUnread() {
        when(achievements.findByUserId(1L)).thenReturn(List.of(new Achievement(user, "STREAK_7")));
        when(meals.existsByUserIdAndDate(eq(1L), eq(TODAY))).thenReturn(false);                 // no meal today
        when(streakService.overallStreak(user)).thenReturn(new StreakInfo(4, 9, TODAY.minusDays(1))); // not active today
        when(bodyMeasurements.findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(eq(1L), any()))
                .thenReturn(Optional.of(measurement(TODAY.minusDays(10))));                     // overdue weigh-in

        NotificationFeed feed = service.getFeed(user);

        assertThat(feed.items()).extracting(NotificationItem::type)
                .containsExactlyInAnyOrder("ACHIEVEMENT", "MEAL_REMINDER", "STREAK_RISK", "WEIGHT_REMINDER");
        assertThat(feed.unreadCount()).isEqualTo(4); // never read → all unread

        assertThat(byType(feed.items(), "ACHIEVEMENT").title()).contains("一週不間斷");
        assertThat(byType(feed.items(), "STREAK_RISK").body()).contains("4 天");
        assertThat(byType(feed.items(), "WEIGHT_REMINDER").body()).contains("10 天");
    }

    @Test
    void getFeed_addsProteinReminder_whenLoggedButProteinLow() {
        // Meals logged today (default), but protein attainment 25% < 40% threshold.
        when(stats.budget(eq(user), eq(TODAY))).thenReturn(budget(25));

        NotificationFeed feed = service.getFeed(user);

        NotificationItem protein = byType(feed.items(), "PROTEIN_REMINDER");
        assertThat(protein).isNotNull();
        assertThat(protein.body()).contains("25%");
        // The "no meals" reminder must not also fire when a meal exists.
        assertThat(byType(feed.items(), "MEAL_REMINDER")).isNull();
    }

    @Test
    void getFeed_addsReportReminder_whenLastWeekReportMissing() {
        when(weeklyReports.findByUserIdAndWeekStart(eq(1L), any())).thenReturn(Optional.empty());

        NotificationFeed feed = service.getFeed(user);

        NotificationItem report = byType(feed.items(), "REPORT_REMINDER");
        assertThat(report).isNotNull();
        assertThat(report.title()).contains("上週回顧");
    }

    @Test
    void getFeed_noProteinReminder_whenNoMealLoggedToday() {
        // No meal today → meal reminder fires, protein nudge is skipped (nothing to assess yet).
        when(meals.existsByUserIdAndDate(eq(1L), eq(TODAY))).thenReturn(false);

        NotificationFeed feed = service.getFeed(user);

        assertThat(byType(feed.items(), "PROTEIN_REMINDER")).isNull();
        assertThat(byType(feed.items(), "MEAL_REMINDER")).isNotNull();
    }

    @Test
    void getFeed_noStreakRisk_whenActiveTodayOrStreakTooShort() {
        when(meals.existsByUserIdAndDate(eq(1L), eq(TODAY))).thenReturn(false);
        when(streakService.overallStreak(user)).thenReturn(new StreakInfo(1, 3, TODAY.minusDays(1))); // current < 2

        NotificationFeed feed = service.getFeed(user);

        assertThat(byType(feed.items(), "STREAK_RISK")).isNull();
        assertThat(byType(feed.items(), "MEAL_REMINDER")).isNotNull();
    }

    @Test
    void getFeed_marksRead_whenWatermarkIsAfterItems() {
        when(achievements.findByUserId(1L)).thenReturn(List.of(new Achievement(user, "STREAK_3")));
        when(meals.existsByUserIdAndDate(eq(1L), eq(TODAY))).thenReturn(false);
        when(reads.findById(1L)).thenReturn(Optional.of(
                new NotificationRead(1L, Instant.now().plusSeconds(3600)))); // read in the "future"

        NotificationFeed feed = service.getFeed(user);

        assertThat(feed.items()).allMatch(NotificationItem::read);
        assertThat(feed.unreadCount()).isZero();
    }

    @Test
    void markRead_upsertsWatermark_andReturnsFeed() {
        doAnswer(inv -> {
            Consumer<TransactionStatus> cb = inv.getArgument(0);
            cb.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        NotificationFeed feed = service.markRead(user);

        verify(reads).save(any(NotificationRead.class));
        assertThat(feed.unreadCount()).isZero();
    }

    private BodyMeasurement measurement(LocalDate date) {
        BodyMeasurement m = new BodyMeasurement();
        m.setMeasuredAt(date.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant());
        return m;
    }

    private static void setId(AppUser user, Long id) {
        try {
            Field f = AppUser.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
