package com.myhealth.streak;

import com.myhealth.streak.StreakDtos.AchievementView;
import com.myhealth.user.AppUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persists which {@link AchievementCatalog} badges a user has unlocked and, on each
 * reconcile, awards any newly-earned ones. The catalog itself is code, not data.
 */
@Service
public class AchievementService {
    private static final Logger log = LoggerFactory.getLogger(AchievementService.class);

    private final AchievementRepository repo;
    private final TransactionTemplate transactionTemplate;

    public AchievementService(AchievementRepository repo, TransactionTemplate transactionTemplate) {
        this.repo = repo;
        this.transactionTemplate = transactionTemplate;
    }

    /** @param newlyUnlocked badge codes awarded by this call (for the UI to celebrate) */
    public record ReconcileResult(List<AchievementView> views, List<String> newlyUnlocked) {
    }

    /**
     * Award every badge whose threshold {@code metrics} now meets but that the user
     * hasn't unlocked yet, then return the full badge wall plus what was just awarded.
     */
    public ReconcileResult reconcile(AppUser user, StreakMetrics metrics) {
        Map<String, Instant> unlockedAt = new HashMap<>();
        repo.findByUserId(user.getId())
                .forEach(a -> unlockedAt.put(a.getCode(), a.getUnlockedAt()));

        List<String> newlyUnlocked = new ArrayList<>();
        for (AchievementCatalog badge : AchievementCatalog.values()) {
            if (!badge.achieved(metrics) || unlockedAt.containsKey(badge.name())) {
                continue;
            }
            try {
                Instant at = transactionTemplate.execute(s -> repo.save(new Achievement(user, badge.name())).getUnlockedAt());
                unlockedAt.put(badge.name(), at);
                newlyUnlocked.add(badge.name());
            } catch (DataIntegrityViolationException race) {
                // A concurrent reconcile already inserted this badge (UNIQUE user_id+code).
                // It's unlocked, just not by us — show it as unlocked, but don't celebrate.
                log.debug("Achievement {} already awarded concurrently", badge.name());
                unlockedAt.put(badge.name(), Instant.now());
            }
        }

        List<AchievementView> views = new ArrayList<>();
        for (AchievementCatalog badge : AchievementCatalog.values()) {
            Instant at = unlockedAt.get(badge.name());
            views.add(new AchievementView(
                    badge.name(), badge.title(), badge.emoji(), badge.description(),
                    badge.threshold(), badge.progress(metrics), at != null, at));
        }
        return new ReconcileResult(views, newlyUnlocked);
    }
}
