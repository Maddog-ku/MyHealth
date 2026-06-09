package com.myhealth.chat;

import com.myhealth.ai.AiProvider;
import com.myhealth.ai.AiProvider.ChatIntent;
import com.myhealth.ai.AiProvider.ChatTurn;
import com.myhealth.ai.AiProvider.MealLog;
import com.myhealth.ai.AiProvider.WeightLog;
import com.myhealth.ai.AiProvider.WorkoutRequest;
import com.myhealth.chat.ChatDtos.ChatMessageResponse;
import com.myhealth.chat.ChatDtos.ChatReplyResponse;
import com.myhealth.healthplan.HealthPlanDtos.HealthPlanResponse;
import com.myhealth.healthplan.HealthPlanDtos.PlanAction;
import com.myhealth.healthplan.HealthPlanService;
import com.myhealth.meal.Meal;
import com.myhealth.meal.MealDtos.MealResponse;
import com.myhealth.meal.MealRepository;
import com.myhealth.meal.MealService;
import com.myhealth.stats.StatsDtos.DailyStatsResponse;
import com.myhealth.stats.StatsService;
import com.myhealth.user.AppUser;
import com.myhealth.user.Profile;
import com.myhealth.user.UserService;
import com.myhealth.workout.WorkoutCategory;
import com.myhealth.workout.WorkoutIntensity;
import com.myhealth.workout.WorkoutDtos.GenerateWorkoutRequest;
import com.myhealth.workout.WorkoutDtos.WorkoutPlanResponse;
import com.myhealth.workout.WorkoutPlan;
import com.myhealth.workout.WorkoutPlanRepository;
import com.myhealth.workout.WorkoutService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
    /** How many prior messages to feed back into the model as context. */
    private static final int HISTORY_WINDOW = 12;

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** Cap on how many of today's logged items are listed in context, to keep the prompt bounded. */
    private static final int MAX_LOGGED_ITEMS = 8;

    /** Cheap pre-gate: only run the (costly) meal-intent classifier when the text hints at eating/logging. */
    private static final Set<String> MEAL_CUES = Set.of(
            "吃", "喝", "早餐", "午餐", "晚餐", "宵夜", "點心", "餐", "記錄", "紀錄", "記一下", "幫我記", "剛");

    /** Cheap pre-gate for the workout-plan classifier. */
    private static final Set<String> WORKOUT_CUES = Set.of(
            "練", "訓練", "運動", "健身", "菜單", "課表", "深蹲", "棒式", "伏地", "重訓", "有氧", "腹肌", "核心",
            "腿", "胸", "背", "臀", "手臂", "二頭", "三頭", "暖身", "拉伸", "伸展");

    /** Cheap pre-gate for the weight-log classifier. */
    private static final Set<String> WEIGHT_CUES = Set.of(
            "體重", "公斤", "kg", "KG", "量體重", "重了", "瘦了", "胖了");

    private final ChatMessageRepository messages;
    private final AiProvider provider;
    private final StatsService stats;
    private final MealRepository meals;
    private final WorkoutPlanRepository workouts;
    private final MealService mealService;
    private final WorkoutService workoutService;
    private final UserService userService;
    private final HealthPlanService healthPlan;

    public ChatService(ChatMessageRepository messages, AiProvider provider, StatsService stats,
                       MealRepository meals, WorkoutPlanRepository workouts, MealService mealService,
                       WorkoutService workoutService, UserService userService, HealthPlanService healthPlan) {
        this.messages = messages;
        this.provider = provider;
        this.stats = stats;
        this.meals = meals;
        this.workouts = workouts;
        this.mealService = mealService;
        this.workoutService = workoutService;
        this.userService = userService;
        this.healthPlan = healthPlan;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> history(AppUser user) {
        return messages.findByUserIdOrderByCreatedAtAsc(user.getId()).stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    /**
     * Deliberately NOT @Transactional: the AI calls below can take tens of seconds, and
     * we must not hold a DB connection open for that long. Each repository.save() commits
     * on its own, and MealService.create() manages its own transaction internally.
     */
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

        // Detect actionable intents (record a meal / plan a workout / log weight) in a single
        // model call — only when a cheap keyword pre-gate hints at one — otherwise plain chat.
        ChatIntent intent = (mightBeMealLog(text) || mightBeWorkout(text) || mightBeWeight(text))
                ? provider.detectIntent(text) : ChatIntent.none();
        MealLog mealLog = "log_meal".equals(intent.action())
                ? new MealLog(true, intent.slot(), intent.food()) : MealLog.none();
        WorkoutRequest workoutReq = "plan_workout".equals(intent.action())
                ? new WorkoutRequest(true, intent.category(), intent.durationMin(), intent.intensity()) : WorkoutRequest.none();
        WeightLog weightLog = "log_weight".equals(intent.action())
                ? new WeightLog(true, intent.weightKg()) : WeightLog.none();

        String reply;
        boolean mealLogged = false;
        boolean workoutLogged = false;
        boolean weightLogged = false;
        LocalDate today = LocalDate.now();
        if (mealLog.isMeal()) {
            try {
                String slot = resolveSlot(mealLog.slot());
                // requireFoodHint=false: the model already classified this as a meal; the
                // keyword whitelist would wrongly reject valid foods like 芒果.
                MealResponse meal = mealService.create(user, null, mealLog.food(), slot, today, false);
                reply = buildMealLoggedReply(user, slot, meal, today);
                mealLogged = true;
            } catch (RuntimeException ex) {
                log.warn("Chat meal logging failed: {}", ex.getMessage());
                reply = "我想幫你記下這一餐，但這次沒記成功 😣 你可以到「飲食追蹤」再記一次，或換個說法告訴我吃了什麼。";
            }
        } else if (workoutReq.isWorkout()) {
            try {
                WorkoutPlanResponse plan = workoutService.generate(user, new GenerateWorkoutRequest(
                        today, resolveCategory(workoutReq.category()),
                        clampDuration(workoutReq.durationMin()), resolveIntensity(workoutReq.intensity()), null));
                reply = buildWorkoutPlannedReply(plan);
                workoutLogged = true;
            } catch (RuntimeException ex) {
                log.warn("Chat workout planning failed: {}", ex.getMessage());
                reply = "我想幫你排一份訓練菜單，但這次沒成功 😣 你可以到「運動菜單」再試一次，或換個說法告訴我想練哪裡。";
            }
        } else if (weightLog.isWeight()) {
            try {
                BigDecimal saved = userService.logWeight(user, BigDecimal.valueOf(weightLog.weightKg())
                        .setScale(1, java.math.RoundingMode.HALF_UP));
                reply = buildWeightLoggedReply(saved);
                weightLogged = true;
            } catch (RuntimeException ex) {
                log.warn("Chat weight logging failed: {}", ex.getMessage());
                reply = "我想幫你記下體重，但這次沒記成功 😣 你可以到「個人資料」再更新一次。";
            }
        } else {
            reply = provider.chat(buildContext(user), turns, text);
        }
        if (reply == null || reply.isBlank()) {
            // content is NOT NULL; never let a misbehaving provider trigger a constraint error.
            reply = "抱歉，我現在無法回覆，請稍後再試一次。";
        }
        ChatMessage assistantMessage = messages.save(new ChatMessage(user, "assistant", reply));

        return new ChatReplyResponse(
                ChatMessageResponse.from(userMessage),
                ChatMessageResponse.from(assistantMessage),
                mealLogged,
                workoutLogged,
                weightLogged,
                (mealLogged || workoutLogged || weightLogged) ? today.toString() : null);
    }

    private boolean mightBeMealLog(String text) {
        return MEAL_CUES.stream().anyMatch(text::contains);
    }

    private boolean mightBeWorkout(String text) {
        return WORKOUT_CUES.stream().anyMatch(text::contains);
    }

    private boolean mightBeWeight(String text) {
        return WEIGHT_CUES.stream().anyMatch(text::contains);
    }

    /** Deterministic confirmation built from the real persisted weight. */
    private String buildWeightLoggedReply(BigDecimal weightKg) {
        return ("好的，我幫你把體重記成 " + plain(weightKg) + " 公斤了 📝\n"
                + "之後的體重趨勢圖會自動更新，繼續加油！💪").strip();
    }

    /** Map the model's category string to the enum; fall back to a safe full-body plan. */
    private WorkoutCategory resolveCategory(String category) {
        if (category != null) {
            try {
                return WorkoutCategory.valueOf(category.strip().toLowerCase());
            } catch (IllegalArgumentException ignored) {
                // fall through to default
            }
        }
        return WorkoutCategory.full_body;
    }

    private WorkoutIntensity resolveIntensity(String intensity) {
        if (intensity != null) {
            try {
                return WorkoutIntensity.valueOf(intensity.strip().toLowerCase());
            } catch (IllegalArgumentException ignored) {
                // fall through to default
            }
        }
        return WorkoutIntensity.medium;
    }

    private int clampDuration(int durationMin) {
        return Math.max(10, Math.min(180, durationMin <= 0 ? 30 : durationMin));
    }

    /** Deterministic summary of the generated plan, built from the real saved entity. */
    private String buildWorkoutPlannedReply(WorkoutPlanResponse plan) {
        StringBuilder r = new StringBuilder();
        r.append("好的，我幫你排好「").append(categoryLabel(plan.category())).append("」的菜單了 💪\n");
        plan.items().forEach(item ->
                r.append("・").append(item.name()).append(" ").append(item.sets()).append(" 組 × ")
                        .append(item.reps()).append('\n'));
        r.append("預估消耗約 ").append(plan.totalKcal()).append(" 大卡。完成後記得到「運動菜單」打卡喔！");
        return r.toString().strip();
    }

    /** Use the model's slot when valid, else infer from the current time of day. */
    private String resolveSlot(String slot) {
        if (slot != null) {
            String s = slot.strip().toLowerCase();
            if (s.equals("breakfast") || s.equals("lunch") || s.equals("dinner") || s.equals("snack")) {
                return s;
            }
        }
        int hour = LocalTime.now().getHour();
        if (hour >= 5 && hour < 11) return "breakfast";
        if (hour >= 11 && hour < 15) return "lunch";
        if (hour >= 15 && hour < 21) return "dinner";
        return "snack";
    }

    /** Deterministic, fully grounded confirmation — built from the real saved meal + today's totals. */
    private String buildMealLoggedReply(AppUser user, String slot, MealResponse meal, LocalDate today) {
        StringBuilder r = new StringBuilder();
        String food = meal.description() != null ? meal.description() : mealLogFoodFallback(meal);
        if (meal.totalKcal() <= 0) {
            r.append("我先幫你把「").append(food).append("」記到").append(slotLabel(slot))
                    .append("了 📝 不過這次 AI 估不出熱量，你可以到「飲食追蹤」手動補上數字 🙏\n");
        } else {
            r.append("好的，我幫你記到").append(slotLabel(slot)).append("了 📝\n");
            r.append("・").append(food).append('\n');
            r.append("・約 ").append(meal.totalKcal()).append(" 大卡");
            r.append("（蛋白 ").append(plain(meal.totalProtein())).append("g／脂肪 ")
                    .append(plain(meal.totalFat())).append("g／碳水 ").append(plain(meal.totalCarb()))
                    .append("g，AI 估算）\n");
        }
        if (meal.aiSuggestion() != null && !meal.aiSuggestion().isBlank()) {
            r.append(meal.aiSuggestion().strip()).append('\n');
        }
        try {
            DailyStatsResponse s = stats.daily(user, today);
            r.append("今日累計攝取約 ").append(s.intakeKcal()).append(" / 目標 ").append(s.goalKcal()).append(" 大卡。");
        } catch (RuntimeException ex) {
            // totals are a nice-to-have; omit on failure
        }
        return r.toString().strip();
    }

    private String mealLogFoodFallback(MealResponse meal) {
        if (meal.items() != null && !meal.items().isEmpty()) {
            return meal.items().get(0).name();
        }
        return "這一餐";
    }

    private String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
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
        appendHealthPlan(sb, user, today);
        return sb.toString();
    }

    /**
     * The same Health Plan summary the Dashboard shows, so the coach answers "我今天該做什麼"
     * from the user's real prioritized next actions instead of inventing advice. Best-effort:
     * the chat must never fail just because the plan couldn't be assembled.
     */
    private void appendHealthPlan(StringBuilder sb, AppUser user, LocalDate today) {
        try {
            HealthPlanResponse plan = healthPlan.get(user, today);
            sb.append("健康計畫就緒分數: ").append(plan.readinessScore()).append(" / 100\n");
            List<PlanAction> actions = plan.nextActions();
            if (actions == null || actions.isEmpty()) {
                sb.append("今日健康計畫建議: 目前沒有待辦，維持現狀即可\n");
                return;
            }
            sb.append("今日健康計畫建議（依優先順序；使用者問「今天該做什麼」時請據此回答，勿自行發明）:\n");
            int i = 1;
            for (PlanAction action : actions) {
                sb.append("  ").append(i++).append(". ").append(action.title())
                        .append("：").append(action.detail()).append('\n');
            }
        } catch (RuntimeException ex) {
            // best-effort context; never block the chat if the plan can't be built
        }
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
