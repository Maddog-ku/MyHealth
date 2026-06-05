package com.myhealth.goal;

import com.myhealth.goal.GoalDtos.SetWeightGoalRequest;
import com.myhealth.goal.GoalDtos.WeightGoalProgress;
import com.myhealth.goal.GoalDtos.WeightGoalResponse;
import com.myhealth.user.AppUser;
import com.myhealth.user.BodyMeasurementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One weight goal per user. The start weight/date are snapshotted when the goal is
 * (re)set; current weight, rate, projection and on-track status are all derived live
 * so the figures always reflect the latest measurements (nothing cached).
 */
@Service
public class GoalService {
    /** Need at least a week of history before an average rate / projection is meaningful. */
    private static final int MIN_DAYS_FOR_RATE = 7;
    /** Don't project absurdly far out (10 years) — treat as "not projectable". */
    private static final long MAX_PROJECTION_WEEKS = 520;

    private final WeightGoalRepository goals;
    private final BodyMeasurementRepository bodyMeasurements;
    private final TransactionTemplate transactionTemplate;
    private final ZoneId zoneId = ZoneId.systemDefault();

    public GoalService(WeightGoalRepository goals, BodyMeasurementRepository bodyMeasurements,
                       TransactionTemplate transactionTemplate) {
        this.goals = goals;
        this.bodyMeasurements = bodyMeasurements;
        this.transactionTemplate = transactionTemplate;
    }

    public WeightGoalResponse get(AppUser user) {
        return new WeightGoalResponse(goals.findByUserId(user.getId())
                .map(goal -> progress(user, goal))
                .orElse(null));
    }

    /** Create or replace the goal, re-anchoring start weight/date to "now". */
    public WeightGoalResponse set(AppUser user, SetWeightGoalRequest request) {
        BigDecimal current = currentWeight(user, null);
        LocalDate today = LocalDate.now(zoneId);
        WeightGoal goal = transactionTemplate.execute(status -> {
            WeightGoal existing = goals.findByUserId(user.getId()).orElse(null);
            if (existing == null) {
                existing = new WeightGoal(user, request.targetWeightKg(), current, today, request.targetDate());
            } else {
                existing.setTargetWeightKg(request.targetWeightKg());
                existing.setStartWeightKg(current);
                existing.setStartDate(today);
                existing.setTargetDate(request.targetDate());
            }
            return goals.save(existing);
        });
        return new WeightGoalResponse(progress(user, goal));
    }

    public void delete(AppUser user) {
        transactionTemplate.executeWithoutResult(status -> goals.deleteByUserId(user.getId()));
    }

    private WeightGoalProgress progress(AppUser user, WeightGoal goal) {
        LocalDate today = LocalDate.now(zoneId);
        BigDecimal start = goal.getStartWeightKg();
        BigDecimal target = goal.getTargetWeightKg();
        BigDecimal current = currentWeight(user, start);

        BigDecimal need = target.subtract(start);            // signed total change wanted
        BigDecimal change = current.subtract(start);          // signed change achieved
        BigDecimal remaining = target.subtract(current);      // signed change still to go

        int progressPct = progressPct(need, change, current, target);
        boolean achieved = achieved(need, current, target);

        long days = ChronoUnit.DAYS.between(goal.getStartDate(), today);
        Double ratePerWeek = (days >= MIN_DAYS_FOR_RATE && change.signum() != 0)
                ? change.doubleValue() / (days / 7.0)
                : null;

        LocalDate projectedDate = projectedDate(today, achieved, remaining, need, ratePerWeek);
        Boolean onTrack = onTrack(achieved, goal.getTargetDate(), projectedDate);

        return new WeightGoalProgress(target, start, current, goal.getStartDate(), goal.getTargetDate(),
                scale1(remaining), scale1(change), progressPct, round2(ratePerWeek),
                projectedDate, onTrack, achieved, goal.getCreatedAt());
    }

    private int progressPct(BigDecimal need, BigDecimal change, BigDecimal current, BigDecimal target) {
        if (need.signum() == 0) {
            return current.compareTo(target) == 0 ? 100 : 0;
        }
        double ratio = change.doubleValue() / need.doubleValue();
        return (int) Math.max(0, Math.min(100, Math.round(ratio * 100)));
    }

    private boolean achieved(BigDecimal need, BigDecimal current, BigDecimal target) {
        if (need.signum() < 0) {
            return current.compareTo(target) <= 0;   // losing weight: reached when at/below target
        }
        if (need.signum() > 0) {
            return current.compareTo(target) >= 0;   // gaining weight: reached when at/above target
        }
        return current.compareTo(target) == 0;       // already at target when goal was set
    }

    private LocalDate projectedDate(LocalDate today, boolean achieved, BigDecimal remaining,
                                    BigDecimal need, Double ratePerWeek) {
        if (achieved || ratePerWeek == null) {
            return null;
        }
        // Only project when moving toward the target (rate and remaining share the goal's direction).
        if (Math.signum(ratePerWeek) != need.signum() || remaining.signum() != need.signum()) {
            return null;
        }
        double weeksRemaining = remaining.doubleValue() / ratePerWeek;
        if (!Double.isFinite(weeksRemaining) || weeksRemaining <= 0 || weeksRemaining > MAX_PROJECTION_WEEKS) {
            return null;
        }
        return today.plusDays(Math.round(weeksRemaining * 7));
    }

    private Boolean onTrack(boolean achieved, LocalDate targetDate, LocalDate projectedDate) {
        if (achieved) {
            return true;
        }
        if (targetDate == null || projectedDate == null) {
            return null;
        }
        return !projectedDate.isAfter(targetDate);
    }

    /** Latest recorded weight, falling back to the profile, then to {@code fallback}. */
    private BigDecimal currentWeight(AppUser user, BigDecimal fallback) {
        BigDecimal latest = bodyMeasurements
                .findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(user.getId(), Instant.now())
                .map(m -> m.getWeightKg())
                .orElse(null);
        if (latest != null) {
            return latest;
        }
        BigDecimal profileWeight = user.getProfile() == null ? null : user.getProfile().getWeightKg();
        return profileWeight != null ? profileWeight : fallback;
    }

    private BigDecimal scale1(BigDecimal value) {
        return value == null ? null : value.setScale(1, RoundingMode.HALF_UP);
    }

    private Double round2(Double value) {
        return value == null ? null : Math.round(value * 100) / 100.0;
    }
}
