package com.myhealth.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhealth.ai.AiProvider;
import com.myhealth.ai.AiProvider.ExerciseItem;
import com.myhealth.ai.AiProvider.FoodItem;
import com.myhealth.ai.AiProvider.ChatIntent;
import com.myhealth.ai.AiProvider.ScheduleDay;
import com.myhealth.chat.ChatDtos.ChatMealConfirmRequest;
import com.myhealth.chat.ChatDtos.ChatReplyResponse;
import com.myhealth.healthplan.HealthPlanDtos.HealthPlanResponse;
import com.myhealth.healthplan.HealthPlanDtos.PlanAction;
import com.myhealth.meal.MealDtos.MealResponse;
import com.myhealth.meal.MealDtos.MealPreviewResponse;
import com.myhealth.meal.MealRepository;
import com.myhealth.meal.MealService;
import com.myhealth.stats.StatsDtos.DailyStatsResponse;
import com.myhealth.stats.StatsService;
import com.myhealth.user.AppUser;
import com.myhealth.user.Gender;
import com.myhealth.user.Profile;
import com.myhealth.user.Role;
import com.myhealth.meal.Meal;
import com.myhealth.workout.WorkoutDtos.WorkoutPlanResponse;
import com.myhealth.workout.WorkoutPlan;
import com.myhealth.workout.WorkoutSchedule;
import com.myhealth.workout.WorkoutScheduleRepository;
import com.myhealth.workout.WorkoutService;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceTest {

    @Mock ChatMessageRepository messages;
    @Mock AiProvider provider;
    @Mock StatsService stats;
    @Mock MealRepository meals;
    @Mock com.myhealth.workout.WorkoutPlanRepository workouts;
    @Mock WorkoutScheduleRepository workoutSchedules;
    @Mock MealService mealService;
    @Mock WorkoutService workoutService;
    @Mock com.myhealth.user.UserService userService;
    @Mock com.myhealth.healthplan.HealthPlanService healthPlan;
    ObjectMapper objectMapper;

    ChatService service;
    AppUser user;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new ChatService(messages, provider, stats, meals, workouts, mealService, workoutService,
                userService, healthPlan, workoutSchedules, objectMapper);
        user = new AppUser();
        user.setEmail("bob@example.com");
        user.setName("Bob");
        user.setRole(Role.USER);
        setId(user, 7L);
        Profile profile = new Profile();
        profile.setGender(Gender.male);
        profile.setHeightCm(new BigDecimal("178"));
        profile.setWeightKg(new BigDecimal("72"));
        user.setProfile(profile);

        when(messages.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(messages.findByUserIdOrderByCreatedAtDesc(anyLong(), any())).thenReturn(List.of());
        when(meals.findByUserIdAndDateOrderByCreatedAtDesc(anyLong(), any())).thenReturn(List.of());
        when(workouts.findByUserIdAndDateOrderByCreatedAtDesc(anyLong(), any())).thenReturn(List.of());
        when(workoutSchedules.findByUserIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());
        when(stats.daily(any(), any())).thenReturn(dailyStats());
        // Default: no actionable intent — individual tests override as needed.
        when(provider.detectIntent(any())).thenReturn(ChatIntent.none());
    }

    @Test
    void mealLogIntent_returnsMealPreview_andSkipsPersistUntilConfirmed() {
        when(provider.detectIntent(any())).thenReturn(new ChatIntent("log_meal", "lunch", "雞胸肉沙拉", "", 30, "medium", 0));
        when(mealService.preview(eq(user), isNull(), eq("雞胸肉沙拉"), eq("lunch"), any(), eq(false)))
                .thenReturn(mealPreview("雞胸肉沙拉", 300));

        ChatReplyResponse res = service.send(user, "我午餐吃了雞胸肉沙拉");

        assertThat(res.mealLogged()).isFalse();
        assertThat(res.loggedDate()).isNull();
        assertThat(res.mealPreview()).isNotNull();
        assertThat(res.mealPreview().description()).isEqualTo("雞胸肉沙拉");
        assertThat(res.reply().content()).contains("午餐");
        verify(mealService).preview(eq(user), isNull(), eq("雞胸肉沙拉"), eq("lunch"), any(), eq(false));
        verify(mealService, never()).create(any(), any(), any(), any(), any(), anyBoolean());
        verify(provider, never()).chat(any(), any(), any());
    }

    @Test
    void confirmMealPreview_persistsMealAndAddsAssistantConfirmation() {
        List<FoodItem> items = List.of(new FoodItem("芒果", 120, 75, 0.8, 0.5, 18.0, 0.9));
        when(mealService.confirmFromItems(eq(user), eq("芒果"), eq("dinner"), eq(LocalDate.of(2026, 6, 13)),
                eq(items), eq("水果可以搭配蛋白質。"), eq(false)))
                .thenReturn(meal("芒果", 75));

        var res = service.confirmMealPreview(user, new ChatMealConfirmRequest(
                LocalDate.of(2026, 6, 13), "dinner", "芒果", items, "水果可以搭配蛋白質。"));

        assertThat(res.loggedDate()).isEqualTo("2026-06-13");
        assertThat(res.meal().totalKcal()).isEqualTo(75);
        assertThat(res.reply().content()).contains("晚餐").contains("75");
        verify(mealService).confirmFromItems(eq(user), eq("芒果"), eq("dinner"), eq(LocalDate.of(2026, 6, 13)),
                eq(items), eq("水果可以搭配蛋白質。"), eq(false));
    }

    @Test
    void plainChat_injectsHealthPlanPrioritiesIntoContext() {
        when(healthPlan.get(eq(user), any())).thenReturn(new HealthPlanResponse(
                LocalDate.now(), "減脂", 70, null, null, null, null,
                List.of(new PlanAction("LOG_MEAL", "記錄第一餐", "今天尚未記錄餐點，先建立今日飲食基準。", 60, "/meals"))));
        when(provider.chat(any(), any(), eq("我今天該做什麼"))).thenReturn("先記錄今天第一餐吧 🍽️");

        ChatReplyResponse res = service.send(user, "我今天該做什麼");

        assertThat(res.reply().content()).isEqualTo("先記錄今天第一餐吧 🍽️");
        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(provider).chat(context.capture(), any(), eq("我今天該做什麼"));
        assertThat(context.getValue()).contains("健康計畫就緒分數: 70");
        assertThat(context.getValue()).contains("記錄第一餐");  // prioritized next action surfaced to the model
    }

    @Test
    void questionWithFoodWord_classifiedNone_fallsBackToChat() {
        when(provider.detectIntent(any())).thenReturn(ChatIntent.none());
        when(provider.chat(any(), any(), eq("晚餐吃什麼比較好"))).thenReturn("建議高蛋白餐 🍗");

        ChatReplyResponse res = service.send(user, "晚餐吃什麼比較好");

        assertThat(res.mealLogged()).isFalse();
        assertThat(res.reply().content()).isEqualTo("建議高蛋白餐 🍗");
        verify(mealService, never()).create(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void messageWithoutActionCue_skipsClassifier() {
        // No meal/workout/weight keyword → the single intent classifier is never called.
        when(provider.chat(any(), any(), eq("你好啊"))).thenReturn("嗨，今天想聊運動還是飲食呢 🙂");

        ChatReplyResponse res = service.send(user, "你好啊");

        assertThat(res.reply().content()).isNotBlank();
        verify(provider, never()).detectIntent(any());
    }

    @Test
    void blankReply_isReplacedByFallback() {
        when(provider.chat(any(), any(), any())).thenReturn("  ");

        ChatReplyResponse res = service.send(user, "深蹲怎麼做");

        assertThat(res.reply().content()).isNotBlank();
    }

    @Test
    void mealLoggingFailure_returnsGracefulReply() {
        when(provider.detectIntent(any())).thenReturn(new ChatIntent("log_meal", "dinner", "芒果", "", 30, "medium", 0));
        when(mealService.preview(any(), any(), any(), any(), any(), eq(false)))
                .thenThrow(new RuntimeException("boom"));

        ChatReplyResponse res = service.send(user, "我晚餐吃芒果");

        assertThat(res.mealLogged()).isFalse();
        assertThat(res.mealPreview()).isNull();
        assertThat(res.reply().content()).contains("預覽").contains("沒成功");
    }

    @Test
    void workoutRequest_generatesPlan_andSkipsChat() {
        when(provider.detectIntent(any())).thenReturn(new ChatIntent("plan_workout", "", "", "legs", 30, "medium", 0));
        when(workoutService.generate(eq(user), any())).thenReturn(plan("legs", 220));

        ChatReplyResponse res = service.send(user, "幫我排個練腿的菜單");

        assertThat(res.workoutLogged()).isTrue();
        assertThat(res.loggedDate()).isEqualTo(LocalDate.now().toString());
        assertThat(res.reply().content()).contains("練腿");
        verify(workoutService).generate(eq(user), any());
        verify(provider, never()).chat(any(), any(), any());
    }

    @Test
    void weightLogIntent_recordsWeight_andSkipsChat() {
        when(provider.detectIntent(any())).thenReturn(new ChatIntent("log_weight", "", "", "", 30, "medium", 68.5));
        when(userService.logWeight(eq(user), any(BigDecimal.class))).thenReturn(new BigDecimal("68.5"));

        ChatReplyResponse res = service.send(user, "我今天體重 68.5 公斤");

        assertThat(res.weightLogged()).isTrue();
        assertThat(res.loggedDate()).isEqualTo(LocalDate.now().toString());
        assertThat(res.reply().content()).contains("68.5").contains("公斤");
        ArgumentCaptor<BigDecimal> weightCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(userService).logWeight(eq(user), weightCaptor.capture());
        assertThat(weightCaptor.getValue()).isEqualByComparingTo("68.5");
        verify(provider, never()).chat(any(), any(), any());
    }

    @Test
    void howToQuestion_runsClassifierButDoesNotLog() {
        // "深蹲" is a workout cue, so the classifier runs, but a how-to question maps to none.
        when(provider.detectIntent(any())).thenReturn(ChatIntent.none());
        when(provider.chat(any(), any(), eq("深蹲怎麼做"))).thenReturn("膝蓋朝腳尖 💪");

        service.send(user, "深蹲怎麼做");

        verify(workoutService, never()).generate(any(), any());
        verify(userService, never()).logWeight(any(), any());
    }

    @Test
    void weightQuestion_classifiedNone_fallsBackToChat() {
        when(provider.detectIntent(any())).thenReturn(ChatIntent.none());
        when(provider.chat(any(), any(), eq("我體重會不會太重"))).thenReturn("體重要看整體狀態 🙂");

        ChatReplyResponse res = service.send(user, "我體重會不會太重");

        assertThat(res.weightLogged()).isFalse();
        verify(userService, never()).logWeight(any(), any());
    }

    @Test
    void weightLoggingFailure_returnsGracefulReply() {
        when(provider.detectIntent(any())).thenReturn(new ChatIntent("log_weight", "", "", "", 30, "medium", 70.0));
        when(userService.logWeight(any(), any())).thenThrow(new RuntimeException("boom"));

        ChatReplyResponse res = service.send(user, "幫我記體重 70");

        assertThat(res.weightLogged()).isFalse();
        assertThat(res.reply().content()).contains("沒記成功");
    }

    @Test
    void chat_groundsContext_labelingUnknownMetricsAsUnavailable() {
        when(provider.chat(any(), any(), any())).thenReturn("好的");
        ArgumentCaptor<String> ctx = ArgumentCaptor.forClass(String.class);

        service.send(user, "你好"); // no meal/workout cue → plain chat, context is built

        verify(provider).chat(ctx.capture(), any(), eq("你好"));
        String context = ctx.getValue();
        assertThat(context).contains("只依據這些數字"); // grounding preamble
        assertThat(context).contains("性別: 男性");
        assertThat(context).contains("身高: 178 cm");
        assertThat(context).contains("體脂率: 未提供"); // null metric labelled, not invented
    }

    @Test
    void chat_includesTodaysLoggedMeals_inContext() {
        when(provider.chat(any(), any(), any())).thenReturn("好的");
        Meal meal = new Meal();
        meal.setSlot("lunch");
        meal.setDescription("雞胸肉沙拉");
        meal.setTotalKcal(320);
        when(meals.findByUserIdAndDateOrderByCreatedAtDesc(anyLong(), any())).thenReturn(List.of(meal));
        ArgumentCaptor<String> ctx = ArgumentCaptor.forClass(String.class);

        service.send(user, "你好");

        verify(provider).chat(ctx.capture(), any(), any());
        assertThat(ctx.getValue()).contains("今日餐點紀錄").contains("午餐").contains("雞胸肉沙拉").contains("320");
    }

    @Test
    void chat_includesTodaysWorkouts_withCategoryLabel() {
        when(provider.chat(any(), any(), any())).thenReturn("好的");
        WorkoutPlan plan = new WorkoutPlan();
        plan.setCategory("legs");
        plan.setTotalKcal(200);
        plan.setDone(true);
        when(workouts.findByUserIdAndDateOrderByCreatedAtDesc(anyLong(), any())).thenReturn(List.of(plan));
        ArgumentCaptor<String> ctx = ArgumentCaptor.forClass(String.class);

        service.send(user, "你好");

        verify(provider).chat(ctx.capture(), any(), any());
        assertThat(ctx.getValue()).contains("今日運動紀錄").contains("練腿").contains("已完成");
    }

    @Test
    void chat_includesTodaysActiveWorkoutSchedule_inContext() throws Exception {
        when(provider.chat(any(), any(), any())).thenReturn("好的");
        LocalDate today = LocalDate.now();
        WorkoutSchedule schedule = schedule(today.minusDays(today.getDayOfWeek().getValue() - 1),
                List.of(new ScheduleDay(today.getDayOfWeek().getValue(), false, "legs", 45, "下肢力量")));
        when(workoutSchedules.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of(schedule));
        ArgumentCaptor<String> ctx = ArgumentCaptor.forClass(String.class);

        service.send(user, "你好");

        verify(provider).chat(ctx.capture(), any(), any());
        assertThat(ctx.getValue())
                .contains("今日週期課表")
                .contains("練腿")
                .contains("45 分鐘")
                .contains("下肢力量");
    }

    private WorkoutPlanResponse plan(String category, int kcal) {
        ExerciseItem item = new ExerciseItem("深蹲", 4, "12", 60, 40, 70, "膝蓋朝腳尖", List.of());
        return new WorkoutPlanResponse(1L, LocalDate.now(), category, List.of(item), kcal, null, false, Instant.now());
    }

    private MealResponse meal(String desc, int kcal) {
        return new MealResponse(1L, LocalDate.now(), "lunch", desc, null, List.of(), kcal,
                new BigDecimal("40"), new BigDecimal("5"), new BigDecimal("10"), "不錯的選擇", Instant.now());
    }

    private MealPreviewResponse mealPreview(String desc, int kcal) {
        return new MealPreviewResponse(LocalDate.now(), "lunch", desc,
                List.of(new FoodItem(desc, 150, kcal, 40.0, 5.0, 10.0, 0.9)),
                kcal, new BigDecimal("40"), new BigDecimal("5"), new BigDecimal("10"), "不錯的選擇");
    }

    private WorkoutSchedule schedule(LocalDate startDate, List<ScheduleDay> days) throws Exception {
        WorkoutSchedule schedule = new WorkoutSchedule();
        schedule.setUser(user);
        schedule.setGoal("減脂");
        schedule.setStartDate(startDate);
        schedule.setWeeks(4);
        schedule.setDaysPerWeek(3);
        schedule.setIntensity("medium");
        schedule.setDaysJson(objectMapper.writeValueAsString(days));
        return schedule;
    }

    private DailyStatsResponse dailyStats() {
        return new DailyStatsResponse(LocalDate.now(), 300, 0, 300,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("72"), 2000, 0, 0);
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
