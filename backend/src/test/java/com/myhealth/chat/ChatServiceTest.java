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

import com.myhealth.ai.AiProvider;
import com.myhealth.ai.AiProvider.ExerciseItem;
import com.myhealth.ai.AiProvider.MealLog;
import com.myhealth.ai.AiProvider.WeightLog;
import com.myhealth.ai.AiProvider.WorkoutRequest;
import com.myhealth.chat.ChatDtos.ChatReplyResponse;
import com.myhealth.meal.MealDtos.MealResponse;
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
    @Mock MealService mealService;
    @Mock WorkoutService workoutService;
    @Mock com.myhealth.user.UserService userService;

    ChatService service;
    AppUser user;

    @BeforeEach
    void setUp() {
        service = new ChatService(messages, provider, stats, meals, workouts, mealService, workoutService, userService);
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
        when(stats.daily(any(), any())).thenReturn(dailyStats());
        // Default: no actionable intent — individual tests override as needed.
        when(provider.detectMealLog(any())).thenReturn(MealLog.none());
        when(provider.detectWorkoutRequest(any())).thenReturn(WorkoutRequest.none());
        when(provider.detectWeightLog(any())).thenReturn(WeightLog.none());
    }

    @Test
    void mealLogIntent_recordsMeal_andSkipsChat() {
        when(provider.detectMealLog(any())).thenReturn(new MealLog(true, "lunch", "雞胸肉沙拉"));
        when(mealService.create(eq(user), isNull(), eq("雞胸肉沙拉"), eq("lunch"), any(), eq(false)))
                .thenReturn(meal("雞胸肉沙拉", 300));

        ChatReplyResponse res = service.send(user, "我午餐吃了雞胸肉沙拉");

        assertThat(res.mealLogged()).isTrue();
        assertThat(res.loggedDate()).isEqualTo(LocalDate.now().toString());
        assertThat(res.reply().content()).contains("午餐");
        verify(mealService).create(eq(user), isNull(), eq("雞胸肉沙拉"), eq("lunch"), any(), eq(false));
        verify(provider, never()).chat(any(), any(), any());
    }

    @Test
    void questionWithFoodWord_classifiedNone_fallsBackToChat() {
        when(provider.detectMealLog(any())).thenReturn(MealLog.none());
        when(provider.chat(any(), any(), eq("晚餐吃什麼比較好"))).thenReturn("建議高蛋白餐 🍗");

        ChatReplyResponse res = service.send(user, "晚餐吃什麼比較好");

        assertThat(res.mealLogged()).isFalse();
        assertThat(res.reply().content()).isEqualTo("建議高蛋白餐 🍗");
        verify(mealService, never()).create(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void messageWithoutFoodCue_skipsClassifier() {
        when(provider.chat(any(), any(), eq("深蹲怎麼做"))).thenReturn("膝蓋朝腳尖、核心收緊 💪");

        ChatReplyResponse res = service.send(user, "深蹲怎麼做");

        assertThat(res.reply().content()).contains("核心");
        verify(provider, never()).detectMealLog(any());
    }

    @Test
    void blankReply_isReplacedByFallback() {
        when(provider.chat(any(), any(), any())).thenReturn("  ");

        ChatReplyResponse res = service.send(user, "深蹲怎麼做");

        assertThat(res.reply().content()).isNotBlank();
    }

    @Test
    void mealLoggingFailure_returnsGracefulReply() {
        when(provider.detectMealLog(any())).thenReturn(new MealLog(true, "dinner", "芒果"));
        when(mealService.create(any(), any(), any(), any(), any(), eq(false)))
                .thenThrow(new RuntimeException("boom"));

        ChatReplyResponse res = service.send(user, "我晚餐吃芒果");

        assertThat(res.mealLogged()).isFalse();
        assertThat(res.reply().content()).contains("沒記成功");
    }

    @Test
    void workoutRequest_generatesPlan_andSkipsChat() {
        when(provider.detectWorkoutRequest(any())).thenReturn(new WorkoutRequest(true, "legs", 30, "medium"));
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
        when(provider.detectWeightLog(any())).thenReturn(new WeightLog(true, 68.5));
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
    void messageWithoutWeightCue_skipsWeightClassifier() {
        when(provider.chat(any(), any(), eq("深蹲怎麼做"))).thenReturn("膝蓋朝腳尖 💪");

        service.send(user, "深蹲怎麼做");

        verify(provider, never()).detectWeightLog(any());
    }

    @Test
    void weightQuestion_classifiedNone_fallsBackToChat() {
        when(provider.detectWeightLog(any())).thenReturn(WeightLog.none());
        when(provider.chat(any(), any(), eq("我體重會不會太重"))).thenReturn("體重要看整體狀態 🙂");

        ChatReplyResponse res = service.send(user, "我體重會不會太重");

        assertThat(res.weightLogged()).isFalse();
        verify(userService, never()).logWeight(any(), any());
    }

    @Test
    void weightLoggingFailure_returnsGracefulReply() {
        when(provider.detectWeightLog(any())).thenReturn(new WeightLog(true, 70.0));
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

    private WorkoutPlanResponse plan(String category, int kcal) {
        ExerciseItem item = new ExerciseItem("深蹲", 4, "12", 60, 40, 70, "膝蓋朝腳尖", List.of());
        return new WorkoutPlanResponse(1L, LocalDate.now(), category, List.of(item), kcal, null, false, Instant.now());
    }

    private MealResponse meal(String desc, int kcal) {
        return new MealResponse(1L, LocalDate.now(), "lunch", desc, null, List.of(), kcal,
                new BigDecimal("40"), new BigDecimal("5"), new BigDecimal("10"), "不錯的選擇", Instant.now());
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
