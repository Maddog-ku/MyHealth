package com.myhealth.streak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.myhealth.streak.StreakDtos.AchievementView;
import com.myhealth.user.AppUser;
import com.myhealth.user.Role;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AchievementServiceTest {

    @Mock AchievementRepository repo;
    TransactionTemplate transactionTemplate;
    AchievementService service;
    AppUser user;

    @BeforeEach
    void setUp() {
        transactionTemplate = mock(TransactionTemplate.class);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
        service = new AchievementService(repo, transactionTemplate);
        user = new AppUser();
        user.setEmail("a@b.c");
        user.setRole(Role.USER);
        setId(user, 1L);
        lenient().when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(repo.findByUserId(1L)).thenReturn(List.of());
    }

    private AchievementView view(List<AchievementView> views, String code) {
        return views.stream().filter(v -> v.code().equals(code)).findFirst().orElseThrow();
    }

    @Test
    void reconcile_awardsEveryNewlyEarnedBadge_andBuildsFullWall() {
        // longest=7, meals=100, workouts=10, weight=1 → earns all except STREAK_30 and WORKOUTS_50.
        StreakMetrics metrics = new StreakMetrics(7, 100, 10, 1);

        AchievementService.ReconcileResult res = service.reconcile(user, metrics);

        assertThat(res.newlyUnlocked()).containsExactlyInAnyOrder(
                "STREAK_3", "STREAK_7", "MEALS_50", "MEALS_100", "WORKOUTS_10", "FIRST_WEIGHT");
        verify(repo, times(6)).save(any());

        assertThat(res.views()).hasSize(AchievementCatalog.values().length);
        assertThat(view(res.views(), "STREAK_7").unlocked()).isTrue();
        assertThat(view(res.views(), "STREAK_30").unlocked()).isFalse();
        assertThat(view(res.views(), "WORKOUTS_50").unlocked()).isFalse();
    }

    @Test
    void reconcile_isIdempotent_doesNotReAwardExisting() {
        when(repo.findByUserId(1L)).thenReturn(List.of(new Achievement(user, "STREAK_3")));
        StreakMetrics metrics = new StreakMetrics(3, 0, 0, 0); // only STREAK_3 earned, already owned

        AchievementService.ReconcileResult res = service.reconcile(user, metrics);

        assertThat(res.newlyUnlocked()).isEmpty();
        verify(repo, never()).save(any());
        AchievementView streak3 = view(res.views(), "STREAK_3");
        assertThat(streak3.unlocked()).isTrue();
        assertThat(streak3.unlockedAt()).isNotNull();
    }

    @Test
    void reconcile_reportsProgress_forLockedBadge() {
        StreakMetrics metrics = new StreakMetrics(0, 30, 0, 0); // 30/50 meals

        AchievementService.ReconcileResult res = service.reconcile(user, metrics);

        AchievementView meals50 = view(res.views(), "MEALS_50");
        assertThat(meals50.unlocked()).isFalse();
        assertThat(meals50.progress()).isEqualTo(30);
        assertThat(meals50.threshold()).isEqualTo(50);
    }

    @Test
    void reconcile_concurrentInsertRace_marksUnlockedButNotNewly() {
        when(repo.save(any())).thenThrow(new DataIntegrityViolationException("dup"));
        StreakMetrics metrics = new StreakMetrics(0, 0, 0, 1); // earns FIRST_WEIGHT

        AchievementService.ReconcileResult res = service.reconcile(user, metrics);

        assertThat(res.newlyUnlocked()).isEmpty();              // raced — not celebrated by us
        assertThat(view(res.views(), "FIRST_WEIGHT").unlocked()).isTrue(); // but still shown unlocked
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
