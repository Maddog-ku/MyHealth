package com.myhealth.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.myhealth.ai.AiProvider;
import com.myhealth.meal.Meal;
import com.myhealth.meal.MealRepository;
import com.myhealth.report.ReportDtos.WeeklyReportResponse;
import com.myhealth.stats.StatsDtos.DailyStatsResponse;
import com.myhealth.stats.StatsDtos.RangeStatsResponse;
import com.myhealth.stats.StatsDtos.SeriesPoint;
import com.myhealth.stats.StatsService;
import com.myhealth.user.AppUser;
import com.myhealth.user.Gender;
import com.myhealth.user.Goal;
import com.myhealth.user.Profile;
import com.myhealth.user.Role;
import com.myhealth.workout.WorkoutPlan;
import com.myhealth.workout.WorkoutPlanRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceTest {

    @Mock StatsService stats;
    @Mock MealRepository meals;
    @Mock WorkoutPlanRepository workouts;
    @Mock WeeklyReportRepository reports;
    @Mock AiProvider provider;

    TransactionTemplate transactionTemplate;
    ReportService service;
    AppUser user;

    @BeforeEach
    void setUp() {
        transactionTemplate = mock(TransactionTemplate.class);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
        service = new ReportService(stats, meals, workouts, reports, provider, transactionTemplate);

        user = new AppUser();
        user.setEmail("a@b.c");
        user.setRole(Role.USER);
        setId(user, 1L);
        Profile p = new Profile();
        p.setGender(Gender.male);
        p.setGoal(Goal.fat_loss);
        user.setProfile(p);

        lenient().when(provider.textModel()).thenReturn("gemma4:e4b");
        lenient().when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(reports.findByUserIdAndWeekStart(eq(1L), any())).thenReturn(Optional.empty());
        lenient().when(stats.daily(any(), any())).thenReturn(daily(1700));
        lenient().when(meals.findByUserIdAndDateBetweenOrderByDateAsc(eq(1L), any(), any())).thenReturn(List.of());
        lenient().when(workouts.findByUserIdAndDateBetweenOrderByDateAsc(eq(1L), any(), any())).thenReturn(List.of());
    }

    private DailyStatsResponse daily(int goal) {
        return new DailyStatsResponse(LocalDate.now(), 0, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, goal, 0, 0);
    }

    private SeriesPoint point(LocalDate date, int intake, int burn, String weight) {
        return new SeriesPoint(date, intake, burn,
                weight == null ? null : new BigDecimal(weight), null, null, null, null);
    }

    private Meal meal() {
        Meal m = new Meal();
        m.setSlot("lunch");
        m.setTotalKcal(300);
        return m;
    }

    private WorkoutPlan workout(boolean done) {
        WorkoutPlan w = new WorkoutPlan();
        w.setCategory("abs");
        w.setTotalKcal(120);
        w.setDone(done);
        return w;
    }

    @Test
    void normalizeWeekStart_snapsToMonday() {
        // 2026-06-03 is a Wednesday → Monday is 2026-06-01.
        assertThat(service.normalizeWeekStart(LocalDate.of(2026, 6, 3))).isEqualTo(LocalDate.of(2026, 6, 1));
        // Already Monday stays put.
        assertThat(service.normalizeWeekStart(LocalDate.of(2026, 6, 1))).isEqualTo(LocalDate.of(2026, 6, 1));
        // null → current week's Monday.
        assertThat(service.normalizeWeekStart(null))
                .isEqualTo(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
    }

    @Test
    void get_computesSummary_andReturnsCachedNarrativeWhenPresent() {
        LocalDate weekStart = LocalDate.of(2026, 5, 25);  // a past Monday → full week, no clamping
        when(stats.range(eq(user), eq(weekStart), eq(weekStart.plusDays(6)))).thenReturn(range(weekStart));
        when(meals.findByUserIdAndDateBetweenOrderByDateAsc(eq(1L), any(), any()))
                .thenReturn(List.of(meal(), meal(), meal(), meal(), meal()));
        when(workouts.findByUserIdAndDateBetweenOrderByDateAsc(eq(1L), any(), any()))
                .thenReturn(List.of(workout(true), workout(true), workout(false)));
        WeeklyReport cached = new WeeklyReport(user, weekStart, "上週做得不錯 💪", "gemma4:e4b");
        when(reports.findByUserIdAndWeekStart(1L, weekStart)).thenReturn(Optional.of(cached));

        WeeklyReportResponse res = service.get(user, weekStart);

        assertThat(res.weekStart()).isEqualTo(weekStart);
        assertThat(res.weekEnd()).isEqualTo(weekStart.plusDays(6));
        assertThat(res.summary().totalIntakeKcal()).isEqualTo(720);
        assertThat(res.summary().avgIntakeKcal()).isEqualTo(360);  // 720 / 2 days
        assertThat(res.summary().totalBurnKcal()).isEqualTo(150);
        assertThat(res.summary().netKcal()).isEqualTo(570);
        assertThat(res.summary().workoutsDone()).isEqualTo(2);
        assertThat(res.summary().mealsLogged()).isEqualTo(5);
        assertThat(res.summary().weightStart()).isEqualByComparingTo("70.0");
        assertThat(res.summary().weightEnd()).isEqualByComparingTo("69.5");
        assertThat(res.summary().weightDelta()).isEqualByComparingTo("-0.5");
        assertThat(res.narrative()).isEqualTo("上週做得不錯 💪");
        assertThat(res.generatedAt()).isNotNull();
    }

    @Test
    void get_returnsNullNarrative_whenNotYetGenerated() {
        LocalDate weekStart = LocalDate.of(2026, 5, 25);
        when(stats.range(any(), any(), any())).thenReturn(range(weekStart));
        when(reports.findByUserIdAndWeekStart(1L, weekStart)).thenReturn(Optional.empty());

        WeeklyReportResponse res = service.get(user, weekStart);

        assertThat(res.narrative()).isNull();
        assertThat(res.generatedAt()).isNull();
    }

    @Test
    void generate_injectsRealNumbersIntoContext_persists_andReturnsNarrative() {
        LocalDate weekStart = LocalDate.of(2026, 5, 25);
        when(stats.range(any(), any(), any())).thenReturn(range(weekStart));
        when(meals.findByUserIdAndDateBetweenOrderByDateAsc(eq(1L), any(), any()))
                .thenReturn(List.of(meal(), meal()));
        when(workouts.findByUserIdAndDateBetweenOrderByDateAsc(eq(1L), any(), any()))
                .thenReturn(List.of(workout(true)));
        when(provider.weeklyReport(any())).thenReturn("攝取：很棒。運動：再加油。體重：穩定。本週建議：多喝水 💧");

        WeeklyReportResponse res = service.generate(user, weekStart);

        ArgumentCaptor<String> ctx = ArgumentCaptor.forClass(String.class);
        verify(provider).weeklyReport(ctx.capture());
        String context = ctx.getValue();
        assertThat(context).contains("只依據這些數字");      // grounding preamble
        assertThat(context).contains("減脂");                // goal label from profile
        assertThat(context).contains("720");                 // total intake
        assertThat(context).contains("150");                 // total burn
        assertThat(context).contains("70");                  // week-start weight
        assertThat(context).contains("69.5");                // week-end weight
        assertThat(context).contains("1 次");                // workouts done (1 completed)
        assertThat(context).contains("2 筆");                // meals logged

        ArgumentCaptor<WeeklyReport> saved = ArgumentCaptor.forClass(WeeklyReport.class);
        verify(reports).save(saved.capture());
        assertThat(saved.getValue().getModel()).isEqualTo("gemma4:e4b");  // real model produced it
        assertThat(res.narrative()).contains("本週建議");
        assertThat(res.summary().totalIntakeKcal()).isEqualTo(720);
    }

    @Test
    void generate_fallsBack_whenProviderReturnsBlank() {
        LocalDate weekStart = LocalDate.of(2026, 5, 25);
        when(stats.range(any(), any(), any())).thenReturn(range(weekStart));
        when(provider.weeklyReport(any())).thenReturn("   ");

        WeeklyReportResponse res = service.generate(user, weekStart);

        assertThat(res.narrative()).isNotBlank();
        assertThat(res.narrative()).contains("AI");  // conservative fallback message
        ArgumentCaptor<WeeklyReport> saved = ArgumentCaptor.forClass(WeeklyReport.class);
        verify(reports).save(saved.capture());
        assertThat(saved.getValue().getModel()).isEqualTo("fallback");  // marked as fallback, not the real model
    }

    @Test
    void generate_fallsBack_whenProviderThrows() {
        LocalDate weekStart = LocalDate.of(2026, 5, 25);
        when(stats.range(any(), any(), any())).thenReturn(range(weekStart));
        when(provider.weeklyReport(any())).thenThrow(new RuntimeException("ollama down"));

        WeeklyReportResponse res = service.generate(user, weekStart);

        assertThat(res.narrative()).isNotBlank();
        verify(reports).save(any(WeeklyReport.class));
    }

    @Test
    void generate_retriesAsUpdate_whenConcurrentInsertRacesOnUniqueKey() {
        LocalDate weekStart = LocalDate.of(2026, 5, 25);
        when(stats.range(any(), any(), any())).thenReturn(range(weekStart));
        when(provider.weeklyReport(any())).thenReturn("攝取：很棒。本週建議：多喝水 💧");
        // First attempt sees no row and tries to INSERT → a concurrent generate already
        // inserted it, tripping UNIQUE(user_id, week_start). On retry the row is visible,
        // so the second attempt updates it instead of failing.
        when(reports.findByUserIdAndWeekStart(1L, weekStart))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new WeeklyReport(user, weekStart, "raced-in", "gemma4:e4b")));
        when(reports.save(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenAnswer(inv -> inv.getArgument(0));

        WeeklyReportResponse res = service.generate(user, weekStart);

        assertThat(res.narrative()).contains("本週建議");
        assertThat(res.generatedAt()).isNotNull();
        verify(reports, times(2)).save(any(WeeklyReport.class));
    }

    /** Two-day series: intake 300+420=720, burn 0+150=150, weight 70.0 → 69.5. */
    private RangeStatsResponse range(LocalDate weekStart) {
        return new RangeStatsResponse(weekStart, weekStart.plusDays(6), List.of(
                point(weekStart, 300, 0, "70.0"),
                point(weekStart.plusDays(1), 420, 150, "69.5")));
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
