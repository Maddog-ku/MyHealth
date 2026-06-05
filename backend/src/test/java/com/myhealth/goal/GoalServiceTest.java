package com.myhealth.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.myhealth.goal.GoalDtos.SetWeightGoalRequest;
import com.myhealth.goal.GoalDtos.WeightGoalProgress;
import com.myhealth.user.AppUser;
import com.myhealth.user.BodyMeasurement;
import com.myhealth.user.BodyMeasurementRepository;
import com.myhealth.user.Profile;
import com.myhealth.user.Role;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoalServiceTest {

    @Mock WeightGoalRepository goals;
    @Mock BodyMeasurementRepository bodyMeasurements;

    TransactionTemplate transactionTemplate;
    GoalService service;
    AppUser user;

    private static final LocalDate TODAY = LocalDate.now();

    @BeforeEach
    void setUp() {
        transactionTemplate = mock(TransactionTemplate.class);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
        service = new GoalService(goals, bodyMeasurements, transactionTemplate);

        user = new AppUser();
        user.setEmail("a@b.c");
        user.setRole(Role.USER);
        setId(user, 1L);
        Profile p = new Profile();
        p.setWeightKg(new BigDecimal("80.0"));
        user.setProfile(p);

        lenient().when(goals.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(goals.findByUserId(1L)).thenReturn(Optional.empty());
        lenient().when(bodyMeasurements.findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void get_returnsNullProgress_whenNoGoal() {
        assertThat(service.get(user).progress()).isNull();
    }

    @Test
    void get_computesProgress_projectionAndOnTrack_whenLosingWeight() {
        // Goal: 80 → 70 over 14 days so far, now 76 (lost 4 → -2 kg/week). Deadline +30d.
        when(goals.findByUserId(1L)).thenReturn(Optional.of(
                goal("70.0", "80.0", TODAY.minusDays(14), TODAY.plusDays(30))));
        when(bodyMeasurements.findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(any(), any()))
                .thenReturn(Optional.of(weight("76.0")));

        WeightGoalProgress p = service.get(user).progress();

        assertThat(p.currentWeightKg()).isEqualByComparingTo("76.0");
        assertThat(p.changeSoFarKg()).isEqualByComparingTo("-4.0");
        assertThat(p.remainingKg()).isEqualByComparingTo("-6.0");
        assertThat(p.progressPct()).isEqualTo(40);          // 4 of 10 kg
        assertThat(p.ratePerWeekKg()).isEqualTo(-2.0);
        assertThat(p.projectedDate()).isEqualTo(TODAY.plusDays(21)); // 6kg / 2kg-per-week = 3 weeks
        assertThat(p.onTrack()).isTrue();                   // +21d is before the +30d deadline
        assertThat(p.achieved()).isFalse();
    }

    @Test
    void get_offTrack_whenProjectionMissesDeadline() {
        // Same trajectory but a tight +10d deadline → projected +21d misses it.
        when(goals.findByUserId(1L)).thenReturn(Optional.of(
                goal("70.0", "80.0", TODAY.minusDays(14), TODAY.plusDays(10))));
        when(bodyMeasurements.findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(any(), any()))
                .thenReturn(Optional.of(weight("76.0")));

        assertThat(service.get(user).progress().onTrack()).isFalse();
    }

    @Test
    void get_achieved_whenReachedTarget() {
        when(goals.findByUserId(1L)).thenReturn(Optional.of(
                goal("70.0", "80.0", TODAY.minusDays(40), null)));
        when(bodyMeasurements.findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(any(), any()))
                .thenReturn(Optional.of(weight("69.0")));

        WeightGoalProgress p = service.get(user).progress();

        assertThat(p.achieved()).isTrue();
        assertThat(p.progressPct()).isEqualTo(100);   // overshot, clamped
        assertThat(p.onTrack()).isTrue();             // achieved ⇒ on track
        assertThat(p.projectedDate()).isNull();
    }

    @Test
    void get_noProjection_whenMovingAwayFromTarget() {
        // Wants to lose (80→70) but gained to 82 → no sensible projection / not on track.
        when(goals.findByUserId(1L)).thenReturn(Optional.of(
                goal("70.0", "80.0", TODAY.minusDays(14), TODAY.plusDays(30))));
        when(bodyMeasurements.findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(any(), any()))
                .thenReturn(Optional.of(weight("82.0")));

        WeightGoalProgress p = service.get(user).progress();

        assertThat(p.progressPct()).isZero();
        assertThat(p.projectedDate()).isNull();
        assertThat(p.onTrack()).isNull();
    }

    @Test
    void set_anchorsStartToCurrentWeight_andUpserts() {
        when(bodyMeasurements.findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(any(), any()))
                .thenReturn(Optional.of(weight("78.5")));

        WeightGoalProgress p = service.set(user, new SetWeightGoalRequest(new BigDecimal("72.0"), TODAY.plusDays(60)))
                .progress();

        ArgumentCaptor<WeightGoal> saved = ArgumentCaptor.forClass(WeightGoal.class);
        verify(goals).save(saved.capture());
        assertThat(saved.getValue().getStartWeightKg()).isEqualByComparingTo("78.5"); // anchored to current
        assertThat(saved.getValue().getStartDate()).isEqualTo(TODAY);
        assertThat(saved.getValue().getTargetWeightKg()).isEqualByComparingTo("72.0");

        assertThat(p.progressPct()).isZero();        // just started, no change yet
        assertThat(p.ratePerWeekKg()).isNull();
        assertThat(p.projectedDate()).isNull();
    }

    @Test
    void set_fallsBackToProfileWeight_whenNoMeasurements() {
        // bodyMeasurements empty (default) → uses profile weight 80.0 as the anchor.
        service.set(user, new SetWeightGoalRequest(new BigDecimal("70.0"), null));

        ArgumentCaptor<WeightGoal> saved = ArgumentCaptor.forClass(WeightGoal.class);
        verify(goals).save(saved.capture());
        assertThat(saved.getValue().getStartWeightKg()).isEqualByComparingTo("80.0");
    }

    @Test
    void delete_removesGoal() {
        doAnswer(inv -> {
            Consumer<TransactionStatus> cb = inv.getArgument(0);
            cb.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        service.delete(user);

        verify(goals).deleteByUserId(1L);
    }

    private WeightGoal goal(String target, String start, LocalDate startDate, LocalDate targetDate) {
        return new WeightGoal(user, new BigDecimal(target), new BigDecimal(start), startDate, targetDate);
    }

    private BodyMeasurement weight(String kg) {
        BodyMeasurement m = new BodyMeasurement();
        m.setWeightKg(new BigDecimal(kg));
        m.setMeasuredAt(Instant.now());
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
