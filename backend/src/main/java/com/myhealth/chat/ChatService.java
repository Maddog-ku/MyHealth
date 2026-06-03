package com.myhealth.chat;

import com.myhealth.ai.AiProvider;
import com.myhealth.ai.AiProvider.ChatTurn;
import com.myhealth.chat.ChatDtos.ChatMessageResponse;
import com.myhealth.chat.ChatDtos.ChatReplyResponse;
import com.myhealth.meal.Meal;
import com.myhealth.meal.MealRepository;
import com.myhealth.stats.StatsDtos.DailyStatsResponse;
import com.myhealth.stats.StatsService;
import com.myhealth.user.AppUser;
import com.myhealth.user.Profile;
import com.myhealth.workout.WorkoutPlan;
import com.myhealth.workout.WorkoutPlanRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
    /** How many prior messages to feed back into the model as context. */
    private static final int HISTORY_WINDOW = 12;

    /** Cap on how many of today's logged items are listed in context, to keep the prompt bounded. */
    private static final int MAX_LOGGED_ITEMS = 8;

    private final ChatMessageRepository messages;
    private final AiProvider provider;
    private final StatsService stats;
    private final MealRepository meals;
    private final WorkoutPlanRepository workouts;

    public ChatService(ChatMessageRepository messages, AiProvider provider, StatsService stats,
                       MealRepository meals, WorkoutPlanRepository workouts) {
        this.messages = messages;
        this.provider = provider;
        this.stats = stats;
        this.meals = meals;
        this.workouts = workouts;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> history(AppUser user) {
        return messages.findByUserIdOrderByCreatedAtAsc(user.getId()).stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    @Transactional
    public ChatReplyResponse send(AppUser user, String message) {
        String text = message.strip();

        // Bounded window of recent turns, oldest-first, gathered before we persist
        // the new user message so it isn't duplicated into history.
        List<ChatMessage> recent =
                messages.findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, HISTORY_WINDOW));
        Collections.reverse(recent);
        List<ChatTurn> turns = new ArrayList<>();
        for (ChatMessage m : recent) {
            turns.add(new ChatTurn("user".equals(m.getRole()), m.getContent()));
        }

        ChatMessage userMessage = messages.save(new ChatMessage(user, "user", text));

        String reply = provider.chat(buildContext(user), turns, text);
        if (reply == null || reply.isBlank()) {
            // content is NOT NULL; never let a misbehaving provider trigger a constraint error.
            reply = "抱歉，我現在無法回覆，請稍後再試一次。";
        }
        ChatMessage assistantMessage = messages.save(new ChatMessage(user, "assistant", reply));

        return new ChatReplyResponse(ChatMessageResponse.from(userMessage), ChatMessageResponse.from(assistantMessage));
    }

    @Transactional
    public void clear(AppUser user) {
        messages.deleteByUserId(user.getId());
    }

    /**
     * A compact factual block injected into the system prompt. Every field is always
     * emitted — missing ones say "未提供" — so the model treats them as explicitly
     * unknown rather than guessing. Today's actually-logged meals and workouts are
     * listed so "what did I eat/train today" is answered from real data, not invention.
     */
    private String buildContext(AppUser user) {
        Profile p = user.getProfile();
        LocalDate today = LocalDate.now();
        StringBuilder sb = new StringBuilder();
        sb.append("（以下為系統提供的真實資料，請只依據這些數字回答；標示「未提供」者代表未知，不可臆測）\n");
        sb.append("暱稱: ").append(user.getName()).append('\n');
        sb.append("性別: ").append(genderLabel(p)).append('\n');
        sb.append("年齡: ").append(p.getAge() != null ? p.getAge() + " 歲" : "未提供").append('\n');
        appendNumber(sb, "身高", p.getHeightCm(), "cm");
        appendNumber(sb, "體重", p.getWeightKg(), "kg");
        appendNumber(sb, "體脂率", p.getBodyFatPct(), "%");
        appendNumber(sb, "肌肉量", p.getMuscleMassKg(), "kg");
        appendNumber(sb, "腰圍", p.getWaistCm(), "cm");
        sb.append("基礎代謝: ").append(p.getBmrKcal() != null ? p.getBmrKcal() + " kcal" : "未提供").append('\n');
        sb.append("目標: ").append(goalLabel(p)).append('\n');
        sb.append("訓練經驗: ").append(experienceLabel(p)).append('\n');

        try {
            DailyStatsResponse stat = stats.daily(user, today);
            sb.append("今日攝取: ").append(stat.intakeKcal()).append(" kcal\n");
            sb.append("今日消耗: ").append(stat.burnKcal()).append(" kcal\n");
            sb.append("今日淨熱量: ").append(stat.netKcal()).append(" kcal\n");
            sb.append("今日熱量目標: ").append(stat.goalKcal()).append(" kcal\n");
            sb.append("今日運動: 已完成 ").append(stat.workoutsDone())
                    .append(" / 共 ").append(stat.workoutsPlanned()).append(" 份\n");
        } catch (RuntimeException ex) {
            // Stats are best-effort context; never block the chat if they fail.
        }

        appendTodayMeals(sb, user, today);
        appendTodayWorkouts(sb, user, today);
        return sb.toString();
    }

    private void appendTodayMeals(StringBuilder sb, AppUser user, LocalDate today) {
        try {
            List<Meal> dayMeals = meals.findByUserIdAndDateOrderByCreatedAtDesc(user.getId(), today);
            if (dayMeals.isEmpty()) {
                sb.append("今日餐點紀錄: 尚無\n");
                return;
            }
            sb.append("今日餐點紀錄:\n");
            dayMeals.stream().limit(MAX_LOGGED_ITEMS).forEach(m -> {
                String desc = m.getDescription() != null && !m.getDescription().isBlank()
                        ? m.getDescription().strip()
                        : "（照片紀錄）";
                sb.append("  - ").append(slotLabel(m.getSlot())).append("：").append(desc)
                        .append("，約 ").append(m.getTotalKcal()).append(" kcal\n");
            });
        } catch (RuntimeException ex) {
            // best-effort
        }
    }

    private void appendTodayWorkouts(StringBuilder sb, AppUser user, LocalDate today) {
        try {
            List<WorkoutPlan> dayWorkouts = workouts.findByUserIdAndDateOrderByCreatedAtDesc(user.getId(), today);
            if (dayWorkouts.isEmpty()) {
                sb.append("今日運動紀錄: 尚無\n");
                return;
            }
            sb.append("今日運動紀錄:\n");
            dayWorkouts.stream().limit(MAX_LOGGED_ITEMS).forEach(w -> {
                sb.append("  - ").append(categoryLabel(w.getCategory()))
                        .append(w.isDone() ? "（已完成，消耗約 " + w.effectiveBurnKcal() + " kcal）" : "（未完成）")
                        .append('\n');
            });
        } catch (RuntimeException ex) {
            // best-effort
        }
    }

    private void appendNumber(StringBuilder sb, String label, BigDecimal value, String unit) {
        sb.append(label).append(": ");
        if (value != null) {
            sb.append(value.stripTrailingZeros().toPlainString()).append(' ').append(unit);
        } else {
            sb.append("未提供");
        }
        sb.append('\n');
    }

    private String slotLabel(String slot) {
        if (slot == null) {
            return "餐點";
        }
        return switch (slot) {
            case "breakfast" -> "早餐";
            case "lunch" -> "午餐";
            case "dinner" -> "晚餐";
            case "snack" -> "點心";
            default -> slot;
        };
    }

    private String categoryLabel(String category) {
        if (category == null) {
            return "運動";
        }
        return switch (category) {
            case "abs" -> "腹肌";
            case "waist" -> "腰腹";
            case "legs" -> "練腿";
            case "chest" -> "練胸";
            case "back" -> "練背";
            case "arms" -> "手臂";
            case "glutes" -> "臀";
            case "cardio" -> "有氧";
            case "full_body" -> "全身";
            default -> category;
        };
    }

    private String genderLabel(Profile p) {
        if (p.getGender() == null) {
            return "未提供";
        }
        return switch (p.getGender()) {
            case male -> "男性";
            case female -> "女性";
            case other -> "其他";
        };
    }

    private String goalLabel(Profile p) {
        if (p.getGoal() == null) {
            return "維持健康";
        }
        return switch (p.getGoal()) {
            case fat_loss -> "減脂";
            case muscle_gain -> "增肌";
            case maintain -> "維持";
        };
    }

    private String experienceLabel(Profile p) {
        if (p.getExperience() == null) {
            return "未提供";
        }
        return switch (p.getExperience()) {
            case beginner -> "初學者";
            case intermediate -> "中階";
            case advanced -> "進階";
        };
    }
}
