package com.myhealth.notification;

import com.myhealth.meal.MealRepository;
import com.myhealth.notification.NotificationDtos.NotificationFeed;
import com.myhealth.notification.NotificationDtos.NotificationItem;
import com.myhealth.streak.Achievement;
import com.myhealth.streak.AchievementCatalog;
import com.myhealth.streak.AchievementRepository;
import com.myhealth.stats.StatsService;
import com.myhealth.streak.StreakDtos.StreakInfo;
import com.myhealth.streak.StreakService;
import com.myhealth.user.AppUser;
import com.myhealth.user.BodyMeasurement;
import com.myhealth.user.BodyMeasurementRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The notification center: a single feed mixing persisted achievement unlocks with
 * live, actionable reminders (no meals today, streak about to break, overdue weigh-in).
 * Nothing about the notifications is stored except a per-user "last read" watermark
 * that drives the unread count.
 */
@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    /** Cap how many past achievement unlocks ride along in the feed. */
    private static final int MAX_ACHIEVEMENTS = 15;
    /** Only nag about a streak worth protecting (≥ this many consecutive days). */
    private static final int STREAK_RISK_MIN = 2;
    /** Remind to weigh in after this many days without a measurement. */
    private static final int WEIGHT_REMINDER_DAYS = 7;
    /** Nudge about protein once the day's attainment falls below this percent of target. */
    private static final int PROTEIN_LOW_PCT = 40;

    private final AchievementRepository achievements;
    private final StreakService streakService;
    private final MealRepository meals;
    private final BodyMeasurementRepository bodyMeasurements;
    private final StatsService stats;
    private final NotificationReadRepository reads;
    private final TransactionTemplate transactionTemplate;
    private final ZoneId zoneId = ZoneId.systemDefault();

    public NotificationService(AchievementRepository achievements, StreakService streakService,
                               MealRepository meals, BodyMeasurementRepository bodyMeasurements,
                               StatsService stats, NotificationReadRepository reads,
                               TransactionTemplate transactionTemplate) {
        this.achievements = achievements;
        this.streakService = streakService;
        this.meals = meals;
        this.bodyMeasurements = bodyMeasurements;
        this.stats = stats;
        this.reads = reads;
        this.transactionTemplate = transactionTemplate;
    }

    public NotificationFeed getFeed(AppUser user) {
        Instant lastReadAt = reads.findById(user.getId())
                .map(NotificationRead::getLastReadAt)
                .orElse(Instant.EPOCH);
        LocalDate today = LocalDate.now(zoneId);
        Instant startOfToday = today.atStartOfDay(zoneId).toInstant();

        List<NotificationItem> items = new ArrayList<>();
        addAchievements(user, items, lastReadAt);
        addReminders(user, items, today, startOfToday, lastReadAt);

        items.sort(Comparator.comparing(NotificationItem::createdAt).reversed());
        int unread = (int) items.stream().filter(i -> !i.read()).count();
        return new NotificationFeed(items, unread);
    }

    /** Mark everything currently shown as read (advance the watermark to now). */
    public NotificationFeed markRead(AppUser user) {
        Instant now = Instant.now();
        try {
            transactionTemplate.executeWithoutResult(s -> upsertRead(user.getId(), now));
        } catch (DataIntegrityViolationException race) {
            // A concurrent markRead inserted the row first; its watermark is effectively ours.
            log.debug("notification_reads upsert raced for user {}", user.getId());
        }
        return getFeed(user);
    }

    private void upsertRead(Long userId, Instant at) {
        NotificationRead row = reads.findById(userId).orElse(null);
        if (row == null) {
            row = new NotificationRead(userId, at);
        } else {
            row.setLastReadAt(at);
        }
        reads.save(row);
    }

    private void addAchievements(AppUser user, List<NotificationItem> items, Instant lastReadAt) {
        achievements.findByUserId(user.getId()).stream()
                .sorted(Comparator.comparing(Achievement::getUnlockedAt).reversed())
                .limit(MAX_ACHIEVEMENTS)
                .forEach(a -> resolve(a.getCode()).ifPresent(badge -> items.add(new NotificationItem(
                        "ach:" + a.getCode(),
                        "ACHIEVEMENT",
                        "解鎖成就：" + badge.title(),
                        badge.description(),
                        badge.emoji(),
                        "success",
                        a.getUnlockedAt(),
                        "/",
                        !a.getUnlockedAt().isAfter(lastReadAt)))));
    }

    private void addReminders(AppUser user, List<NotificationItem> items, LocalDate today,
                              Instant startOfToday, Instant lastReadAt) {
        // Reminders refresh daily; treat them as read once the user has opened the center today.
        boolean readToday = !startOfToday.isAfter(lastReadAt);

        if (!meals.existsByUserIdAndDate(user.getId(), today)) {
            items.add(new NotificationItem("meal:" + today, "MEAL_REMINDER",
                    "今天還沒記錄飲食", "別忘了把今天吃的記下來，讓 AI 幫你分析營養。",
                    "🍽️", "warning", startOfToday, "/meals", readToday));
        } else {
            addProteinReminder(user, items, today, startOfToday, readToday);
        }

        StreakInfo overall = streakService.overallStreak(user);
        boolean activeToday = today.equals(overall.lastActiveDate());
        if (!activeToday && overall.current() >= STREAK_RISK_MIN) {
            items.add(new NotificationItem("streak:" + today, "STREAK_RISK",
                    "連續紀錄即將中斷", "你已連續記錄 " + overall.current() + " 天，今天還沒任何紀錄，快去保住連勝！",
                    "🔥", "warning", startOfToday, "/", readToday));
        }

        BodyMeasurement latest = bodyMeasurements
                .findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(user.getId(), Instant.now())
                .orElse(null);
        if (latest != null) {
            long days = ChronoUnit.DAYS.between(latest.getMeasuredAt().atZone(zoneId).toLocalDate(), today);
            if (days >= WEIGHT_REMINDER_DAYS) {
                items.add(new NotificationItem("weight:" + today, "WEIGHT_REMINDER",
                        "該量體重了", "距離上次紀錄已 " + days + " 天，量一下追蹤趨勢吧。",
                        "⚖️", "info", startOfToday, "/", readToday));
            }
        }
    }

    /**
     * Once the day has meals logged, nudge if protein attainment is well below target. Reuses
     * the same calorie-budget macros the dashboard shows. Best-effort: a stats failure must not
     * break the feed.
     */
    private void addProteinReminder(AppUser user, List<NotificationItem> items, LocalDate today,
                                    Instant startOfToday, boolean readToday) {
        try {
            stats.budget(user, today).macros().stream()
                    .filter(macro -> "protein".equals(macro.name()))
                    .findFirst()
                    .filter(protein -> protein.targetG() > 0 && protein.pct() < PROTEIN_LOW_PCT)
                    .ifPresent(protein -> items.add(new NotificationItem("protein:" + today, "PROTEIN_REMINDER",
                            "今日蛋白質偏低", "蛋白質約達成 " + protein.pct() + "%，下一餐多補一份高蛋白食物。",
                            "🥩", "info", startOfToday, "/meals", readToday)));
        } catch (RuntimeException ex) {
            // best-effort: stats are nice-to-have context for the feed
        }
    }

    private java.util.Optional<AchievementCatalog> resolve(String code) {
        try {
            return java.util.Optional.of(AchievementCatalog.valueOf(code));
        } catch (IllegalArgumentException unknown) {
            return java.util.Optional.empty(); // a retired badge code — skip it
        }
    }
}
