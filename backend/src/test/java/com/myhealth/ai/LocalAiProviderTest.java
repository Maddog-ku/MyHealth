package com.myhealth.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhealth.ai.AiProvider.ExerciseItem;
import com.myhealth.ai.AiProvider.FoodItem;
import com.myhealth.ai.AiProvider.MealAnalysis;
import com.myhealth.config.AppProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalAiProviderTest {

    @Mock OllamaClient ollama;

    final ObjectMapper objectMapper = new ObjectMapper();
    AppProperties properties;
    LocalAiProvider provider;

    @BeforeEach
    void setUp() {
        properties = new AppProperties(
                new AppProperties.Jwt("test-secret-test-secret-test-secret-32bytes!!", 15, 30),
                new AppProperties.Cors(List.of("http://localhost")),
                new AppProperties.Ai("local", "http://localhost:11434", "gemma4:e4b", "gemma4:e4b", 60),
                "./uploads");
        provider = new LocalAiProvider(properties, ollama, objectMapper);
    }

    @Test
    void generateWorkout_parsesOllamaJson_andReturnsItems() {
        String ollamaJson = """
                {"items":[
                  {"name":"深蹲","sets":4,"reps":"12","restSec":60,"kcal":70,"note":"膝蓋朝腳尖","alt":["椅子深蹲"]},
                  {"name":"弓箭步","sets":3,"reps":"每側10","restSec":60,"kcal":65,"note":"保持軀幹穩定","alt":[]}
                ]}
                """;
        when(ollama.chat(eq("gemma4:e4b"), any(), any(), eq(true), any())).thenReturn(ollamaJson);

        List<ExerciseItem> items = provider.generateWorkout("legs", 30, "medium");

        assertThat(items).hasSize(2);
        assertThat(items.get(0).name()).isEqualTo("深蹲");
        assertThat(items.get(0).sets()).isEqualTo(4);
        assertThat(items.get(0).kcal()).isEqualTo(70);
        assertThat(items.get(0).alt()).containsExactly("椅子深蹲");
    }

    @Test
    void generateWorkout_capsLargeOutputs_toSixItems() {
        StringBuilder json = new StringBuilder("{\"items\":[");
        for (int i = 0; i < 20; i++) {
            json.append("{\"name\":\"E").append(i).append("\",\"sets\":1,\"reps\":\"10\",\"restSec\":30,\"kcal\":10,\"note\":\"保持穩定\",\"alt\":[]}");
            if (i < 19) json.append(',');
        }
        json.append("]}");
        when(ollama.chat(any(), any(), any(), eq(true), any())).thenReturn(json.toString());

        List<ExerciseItem> items = provider.generateWorkout("abs", 30, "medium");

        assertThat(items).hasSize(6);
    }

    @Test
    void generateWorkout_promptRequiresSafeJsonOnlyPlan() {
        when(ollama.chat(any(), any(), any(), eq(true), any()))
                .thenReturn("{\"items\":[{\"name\":\"棒式\",\"sets\":3,\"reps\":\"30s\",\"restSec\":45,\"kcal\":30,\"note\":\"身體維持一直線\",\"alt\":[]}]}");

        provider.generateWorkout("abs", 30, "medium");

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(ollama).chat(eq("gemma4:e4b"), systemCaptor.capture(), eq("分類: abs\n時長: 30 分鐘\n強度: medium"), eq(true), any());

        assertThat(systemCaptor.getValue())
                .contains("不可假設傷病、器材、場地或訓練經驗")
                .contains("若缺少器材資訊，預設使用徒手或常見低風險動作")
                .contains("不可產生高風險")
                .contains("不可輸出醫療、復健或傷病治療處方")
                .contains("動作名稱必須是常見且可理解")
                .contains("不要編造不存在的動作")
                .contains("必須推薦 3 到 6 個 items")
                .contains("sets 必須是 1 到 6 的整數")
                .contains("restSec 必須是 15 到 180 的整數")
                .contains("kcal 必須是 5 到 250 的整數")
                .contains("alt 最多 2 個替代動作")
                .contains("不可輸出 Markdown")
                .contains("不可輸出 Markdown、註解、推理過程、額外欄位或 JSON 以外的文字")
                .contains("low：低衝擊")
                .contains("medium：中等強度")
                .contains("high：可提高密度")
                .contains("只允許回傳下列 JSON schema");
    }

    @Test
    void generateWorkout_userPromptContainsOnlyRequestedInputs() {
        when(ollama.chat(any(), any(), any(), eq(true), any()))
                .thenReturn("{\"items\":[{\"name\":\"棒式\",\"sets\":3,\"reps\":\"30s\",\"restSec\":45,\"kcal\":30,\"note\":\"身體維持一直線\",\"alt\":[]}]}");

        provider.generateWorkout("full_body", 45, "high");

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(ollama).chat(eq("gemma4:e4b"), any(), userCaptor.capture(), eq(true), any());

        assertThat(userCaptor.getValue())
                .isEqualTo("分類: full_body\n時長: 45 分鐘\n強度: high")
                .doesNotContain("器材")
                .doesNotContain("傷病")
                .doesNotContain("經驗");
    }

    @Test
    void generateWorkout_filtersInvalidExerciseItemsAndCapsToSix() {
        String json = """
                {"items":[
                  {"name":"","sets":3,"reps":"10","restSec":30,"kcal":30,"note":"穩定控制","alt":[]},
                  {"name":"組數過高","sets":9,"reps":"10","restSec":30,"kcal":30,"note":"穩定控制","alt":[]},
                  {"name":"休息過短","sets":3,"reps":"10","restSec":5,"kcal":30,"note":"穩定控制","alt":[]},
                  {"name":"A","sets":3,"reps":"10","restSec":30,"kcal":30,"note":"穩定控制","alt":[]},
                  {"name":"B","sets":3,"reps":"10","restSec":30,"kcal":30,"note":"穩定控制","alt":[]},
                  {"name":"C","sets":3,"reps":"10","restSec":30,"kcal":30,"note":"穩定控制","alt":[]},
                  {"name":"D","sets":3,"reps":"10","restSec":30,"kcal":30,"note":"穩定控制","alt":[]},
                  {"name":"E","sets":3,"reps":"10","restSec":30,"kcal":30,"note":"穩定控制","alt":[]},
                  {"name":"F","sets":3,"reps":"10","restSec":30,"kcal":30,"note":"穩定控制","alt":[]},
                  {"name":"G","sets":3,"reps":"10","restSec":30,"kcal":30,"note":"穩定控制","alt":[]}
                ]}
                """;
        when(ollama.chat(any(), any(), any(), eq(true), any())).thenReturn(json);

        List<ExerciseItem> items = provider.generateWorkout("abs", 30, "medium");

        assertThat(items).extracting(ExerciseItem::name).containsExactly("A", "B", "C", "D", "E", "F");
    }

    @Test
    void generateWorkout_fallsBackToTemplate_whenAllItemsInvalidAfterSanitization() {
        when(ollama.chat(any(), any(), any(), eq(true), any())).thenReturn("""
                {"items":[
                  {"name":"","sets":3,"reps":"10","restSec":30,"kcal":30,"note":"穩定控制","alt":[]},
                  {"name":"休息過短","sets":3,"reps":"10","restSec":5,"kcal":30,"note":"穩定控制","alt":[]}
                ]}
                """);

        List<ExerciseItem> items = provider.generateWorkout("abs", 30, "medium");

        assertThat(items.get(0).name()).isEqualTo("捲腹");
    }

    @Test
    void generateWorkout_fallsBackToTemplate_whenOllamaThrows() {
        when(ollama.chat(any(), any(), any(), anyBoolean(), any()))
                .thenThrow(new OllamaClient.OllamaException("connection refused"));

        List<ExerciseItem> items = provider.generateWorkout("abs", 30, "medium");

        assertThat(items).isNotEmpty();
        assertThat(items.get(0).name()).isEqualTo("捲腹");  // matches fallback template
    }

    @Test
    void generateWorkout_fallsBackToWaistTemplate_whenOllamaThrows() {
        when(ollama.chat(any(), any(), any(), anyBoolean(), any()))
                .thenThrow(new OllamaClient.OllamaException("down"));

        List<ExerciseItem> items = provider.generateWorkout("waist", 30, "medium");

        assertThat(items.get(0).name()).isEqualTo("側棒式");  // waist fallback template
    }

    @Test
    void generateWorkout_fallsBackToTemplate_whenJsonMalformed() {
        when(ollama.chat(any(), any(), any(), anyBoolean(), any()))
                .thenReturn("not really json at all");

        List<ExerciseItem> items = provider.generateWorkout("legs", 30, "medium");

        assertThat(items.get(0).name()).isEqualTo("深蹲");  // legs fallback
    }

    @Test
    void generateWorkout_fallsBackToTemplate_whenItemsEmpty() {
        when(ollama.chat(any(), any(), any(), anyBoolean(), any())).thenReturn("{\"items\":[]}");

        List<ExerciseItem> items = provider.generateWorkout("cardio", 30, "medium");

        assertThat(items.get(0).name()).isEqualTo("開合跳");  // cardio fallback
    }

    @Test
    void generateWorkout_fallsBackToGeneric_whenCategoryUnknown() {
        when(ollama.chat(any(), any(), any(), anyBoolean(), any()))
                .thenThrow(new OllamaClient.OllamaException("down"));

        List<ExerciseItem> items = provider.generateWorkout("space-jump", 30, "medium");

        assertThat(items.get(0).name()).isEqualTo("動態暖身");
    }

    @Test
    void generateWorkout_markUsed_thenLoadedReturnsFalse_becauseKeepAliveIsZero() {
        when(ollama.chat(any(), any(), any(), anyBoolean(), any()))
                .thenReturn("{\"items\":[{\"name\":\"X\",\"sets\":1,\"reps\":\"1\",\"restSec\":1,\"kcal\":1,\"note\":\"\",\"alt\":[]}]}");

        provider.generateWorkout("abs", 30, "medium");

        assertThat(provider.lastUsedAt()).isNotNull();
        assertThat(provider.loaded()).isFalse();  // released after call by design (keep_alive=0)
    }

    @Test
    void analyzeMeal_parsesItemsAndSuggestion() {
        String json = """
                {"items":[
                  {"name":"雞胸肉","grams":150,"kcal":248,"protein":46.5,"fat":5.4,"carb":0.0,"confidence":0.92}
                ],
                "suggestion":"蛋白足夠，可加碳水"}
                """;
        when(ollama.chat(any(), any(), any(), anyList(), eq(true), any())).thenReturn(json);

        MealAnalysis result = provider.analyzeMeal("雞胸肉沙拉", null);

        assertThat(result.items()).hasSize(1);
        FoodItem food = result.items().get(0);
        assertThat(food.name()).isEqualTo("雞胸肉");
        assertThat(food.kcal()).isEqualTo(248);
        assertThat(food.protein()).isEqualTo(46.5);
        assertThat(result.suggestion()).isEqualTo("蛋白足夠，可加碳水");
    }

    @Test
    void analyzeMeal_withImage_usesVisionModelAndPassesBase64Image() {
        String json = """
                {"items":[
                  {"name":"牛肉飯","grams":350,"kcal":680,"protein":32.0,"fat":22.0,"carb":88.0,"confidence":0.74}
                ],
                "suggestion":"份量偏高，晚餐可清淡"}
                """;
        when(ollama.chat(eq("gemma4:e4b"), any(), any(), eq(List.of("AQID")), eq(true), any())).thenReturn(json);

        MealAnalysis result = provider.analyzeMeal("牛肉飯", new AiProvider.MealImage("image/jpeg", new byte[]{1, 2, 3}));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).name()).isEqualTo("牛肉飯");
    }

    @Test
    void analyzeMeal_promptRequiresConservativeJsonOnlyVisionEstimate() {
        when(ollama.chat(any(), any(), any(), anyList(), eq(true), any()))
                .thenReturn("{\"items\":[],\"suggestion\":\"請重新拍攝或手動輸入\"}");

        provider.analyzeMeal(null, new AiProvider.MealImage("image/jpeg", new byte[]{1, 2, 3}));

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(ollama).chat(eq("gemma4:e4b"), systemCaptor.capture(), userCaptor.capture(), eq(List.of("AQID")), eq(true), any());

        assertThat(systemCaptor.getValue())
                .contains("只能根據照片中清楚可見的食物、容器、份量線索，以及使用者明確輸入的文字判斷")
                .contains("不可猜測看不到的食材")
                .contains("看不清楚時要降低 confidence")
                .contains("無法辨識的餐點")
                .contains("不要編造菜名")
                .contains("若只能辨識大類，請用大類估算，confidence 不可高於 0.45")
                .contains("items 必須是空陣列")
                .contains("最多 5 個 items")
                .contains("不要拆出看不到的成分")
                .contains("confidence 必須介於 0 到 1")
                .contains("不可輸出醫療診斷、減重處方或絕對化結論")
                .contains("若文字描述與照片衝突，以照片為主")
                .contains("不可輸出 Markdown")
                .contains("不可輸出 Markdown、註解、推理過程、額外欄位或 JSON 以外的文字")
                .contains("只允許回傳下列 JSON schema");
        assertThat(userCaptor.getValue()).contains("照片已隨請求附上").contains("不要猜測具體菜名");
    }

    @Test
    void analyzeMeal_withoutImagePromptStillRequiresStrictConservativeJson() {
        when(ollama.chat(any(), any(), any(), anyList(), eq(true), any()))
                .thenReturn("{\"items\":[{\"name\":\"白飯\",\"grams\":150,\"kcal\":240,\"protein\":4.0,\"fat\":0.4,\"carb\":53.0,\"confidence\":0.7}],\"suggestion\":\"份量以描述保守估算\"}");

        provider.analyzeMeal("白飯一碗", null);

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(ollama).chat(eq("gemma4:e4b"), systemCaptor.capture(), userCaptor.capture(), eq(List.of()), eq(true), any());

        assertThat(systemCaptor.getValue())
                .contains("只能根據照片中清楚可見的食物")
                .contains("使用者明確輸入的文字")
                .contains("不可猜測看不到的食材")
                .contains("不可輸出 Markdown、註解、推理過程、額外欄位或 JSON 以外的文字")
                .contains("\"suggestion\":\"一段繁體中文建議，10到30字\"");
        assertThat(userCaptor.getValue())
                .isEqualTo("餐點描述: 白飯一碗\n");
    }

    @Test
    void parseMealAnalysis_filtersInvalidFoodItemsAndCapsToFive() throws Exception {
        String json = """
                {"items":[
                  {"name":"","grams":100,"kcal":100,"protein":1,"fat":1,"carb":1,"confidence":0.5},
                  {"name":"壞資料","grams":-1,"kcal":100,"protein":1,"fat":1,"carb":1,"confidence":0.5},
                  {"name":"壞信心","grams":100,"kcal":100,"protein":1,"fat":1,"carb":1,"confidence":1.5},
                  {"name":"A","grams":100,"kcal":100,"protein":1,"fat":1,"carb":1,"confidence":0.5},
                  {"name":"B","grams":100,"kcal":100,"protein":1,"fat":1,"carb":1,"confidence":0.5},
                  {"name":"C","grams":100,"kcal":100,"protein":1,"fat":1,"carb":1,"confidence":0.5},
                  {"name":"D","grams":100,"kcal":100,"protein":1,"fat":1,"carb":1,"confidence":0.5},
                  {"name":"E","grams":100,"kcal":100,"protein":1,"fat":1,"carb":1,"confidence":0.5},
                  {"name":"F","grams":100,"kcal":100,"protein":1,"fat":1,"carb":1,"confidence":0.5}
                ],"suggestion":"保守估算"}
                """;

        MealAnalysis result = provider.parseMealAnalysis(json);

        assertThat(result.items()).extracting(FoodItem::name).containsExactly("A", "B", "C", "D", "E");
    }

    @Test
    void analyzeMeal_fallsBackToStub_whenOllamaUnavailable() {
        when(ollama.chat(any(), any(), any(), anyList(), anyBoolean(), any()))
                .thenThrow(new OllamaClient.OllamaException("timeout"));

        MealAnalysis result = provider.analyzeMeal("焗烤起司飯", null);

        assertThat(result.items()).isEmpty();
        assertThat(result.suggestion()).contains("AI 無法可靠辨識");
    }

    @Test
    void analyzeMeal_fallsBackToConservativeEstimate_whenItemsArrayEmptyButDescriptionMatches() {
        when(ollama.chat(any(), any(), any(), anyList(), anyBoolean(), any()))
                .thenReturn("{\"items\":[],\"suggestion\":\"…\"}");

        MealAnalysis result = provider.analyzeMeal("白飯", null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).name()).isEqualTo("白飯");
        assertThat(result.items().get(0).confidence()).isEqualTo(0.35);
        assertThat(result.suggestion()).contains("保守估算");
    }

    @Test
    void analyzeMeal_fallsBackToStub_whenItemsArrayEmptyAndNoKeywordMatch() {
        when(ollama.chat(any(), any(), any(), anyList(), anyBoolean(), any()))
                .thenReturn("{\"items\":[],\"suggestion\":\"…\"}");

        MealAnalysis result = provider.analyzeMeal("焗烤起司", null);

        assertThat(result.items()).isEmpty();
        assertThat(result.suggestion()).contains("AI 無法可靠辨識");
    }

    @Test
    void unload_delegatesToOllamaAndFlipsLoadedFlag() {
        provider.markUsed();
        assertThat(provider.loaded()).isTrue();

        provider.unload();

        verify(ollama).unload("gemma4:e4b");
        assertThat(provider.loaded()).isFalse();
    }

    @Test
    void detectWeightLog_parsesLogIntent() {
        when(ollama.chat(any(), any(), any(), eq(true), any()))
                .thenReturn("{\"action\":\"log_weight\",\"weightKg\":68.5}");

        AiProvider.WeightLog log = provider.detectWeightLog("我今天體重 68.5 公斤");

        assertThat(log.isWeight()).isTrue();
        assertThat(log.weightKg()).isEqualTo(68.5);
    }

    @Test
    void detectWeightLog_returnsNone_onNoneAction() {
        when(ollama.chat(any(), any(), any(), eq(true), any()))
                .thenReturn("{\"action\":\"none\",\"weightKg\":0}");

        assertThat(provider.detectWeightLog("我會不會太胖").isWeight()).isFalse();
    }

    @Test
    void detectWeightLog_returnsNone_whenValueOutOfPlausibleRange() {
        when(ollama.chat(any(), any(), any(), eq(true), any()))
                .thenReturn("{\"action\":\"log_weight\",\"weightKg\":5}");  // implausible → rejected

        assertThat(provider.detectWeightLog("我體重 5 公斤").isWeight()).isFalse();
    }

    @Test
    void detectWeightLog_returnsNone_onOllamaFailure() {
        when(ollama.chat(any(), any(), any(), eq(true), any()))
                .thenThrow(new OllamaClient.OllamaException("down"));

        assertThat(provider.detectWeightLog("體重 70").isWeight()).isFalse();
    }

    @Test
    void weeklyReport_returnsModelText_andUnloads() {
        when(ollama.converse(eq("gemma4:e4b"), anyList(), any())).thenReturn("攝取：不錯。本週建議：多喝水 💧");

        String report = provider.weeklyReport("本週總攝取: 4200 kcal");

        assertThat(report).contains("本週建議");
        verify(ollama).unload("gemma4:e4b");
        assertThat(provider.loaded()).isFalse();
    }

    @Test
    void weeklyReport_returnsBlank_whenOllamaFails() {
        when(ollama.converse(any(), anyList(), any())).thenThrow(new OllamaClient.OllamaException("down"));

        assertThat(provider.weeklyReport("本週總攝取: 4200 kcal")).isBlank();
        verify(ollama).unload("gemma4:e4b");
    }

    @Test
    void planWorkoutSchedule_parsesModelSplit_andNormalizesToSevenDays() {
        String json = """
                {"days":[
                  {"weekday":1,"rest":false,"category":"legs","durationMin":40,"focus":"下肢肌力"},
                  {"weekday":2,"rest":true,"category":"","durationMin":0,"focus":"休息"},
                  {"weekday":3,"rest":false,"category":"chest","durationMin":35,"focus":"胸"},
                  {"weekday":4,"rest":true,"category":"","durationMin":0,"focus":"休息"},
                  {"weekday":5,"rest":false,"category":"back","durationMin":35,"focus":"背"},
                  {"weekday":6,"rest":true,"category":"","durationMin":0,"focus":"休息"},
                  {"weekday":7,"rest":true,"category":"","durationMin":0,"focus":"休息"}
                ]}
                """;
        when(ollama.chat(eq("gemma4:e4b"), any(), any(), eq(true), any())).thenReturn(json);

        List<AiProvider.ScheduleDay> days = provider.planWorkoutSchedule("增肌", 3, "medium", "中階");

        assertThat(days).hasSize(7);
        assertThat(days).extracting(AiProvider.ScheduleDay::weekday).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(days.stream().filter(d -> !d.rest()).count()).isEqualTo(3);
        assertThat(days.get(0).category()).isEqualTo("legs");
        assertThat(provider.loaded()).isFalse();  // released after call by design

        // The training experience is fed into the model prompt so the split fits the user's level.
        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(ollama).chat(eq("gemma4:e4b"), any(), userCaptor.capture(), eq(true), any());
        assertThat(userCaptor.getValue()).contains("experience: 中階");
    }

    @Test
    void planWorkoutSchedule_clampsDuration_andDropsUnknownCategoryToRest() {
        // wednesday has a bogus category → becomes rest, leaving 1 training day (matches daysPerWeek=1... )
        String json = """
                {"days":[
                  {"weekday":1,"rest":false,"category":"legs","durationMin":500,"focus":"太久"},
                  {"weekday":2,"rest":true,"category":"","durationMin":0,"focus":"休息"},
                  {"weekday":3,"rest":false,"category":"moonwalk","durationMin":40,"focus":"亂的"},
                  {"weekday":4,"rest":true,"category":"","durationMin":0,"focus":"休息"},
                  {"weekday":5,"rest":true,"category":"","durationMin":0,"focus":"休息"},
                  {"weekday":6,"rest":true,"category":"","durationMin":0,"focus":"休息"},
                  {"weekday":7,"rest":true,"category":"","durationMin":0,"focus":"休息"}
                ]}
                """;
        when(ollama.chat(any(), any(), any(), eq(true), any())).thenReturn(json);

        // daysPerWeek=1: after dropping the bogus category to rest there is exactly 1 training day.
        List<AiProvider.ScheduleDay> days = provider.planWorkoutSchedule("維持", 1, "medium", "初學者");

        assertThat(days.get(0).durationMin()).isEqualTo(90);  // clamped from 500
        assertThat(days.get(2).rest()).isTrue();              // unknown category → rest
        assertThat(days.stream().filter(d -> !d.rest()).count()).isEqualTo(1);
    }

    @Test
    void planWorkoutSchedule_fallsBackToTemplate_whenTrainingCountMismatchesRequest() {
        // Model returns 2 training days but caller asked for 4 → unusable, deterministic fallback.
        when(ollama.chat(any(), any(), any(), eq(true), any())).thenReturn("""
                {"days":[
                  {"weekday":1,"rest":false,"category":"legs","durationMin":40,"focus":"腿"},
                  {"weekday":2,"rest":false,"category":"chest","durationMin":40,"focus":"胸"},
                  {"weekday":3,"rest":true,"category":"","durationMin":0,"focus":"休息"},
                  {"weekday":4,"rest":true,"category":"","durationMin":0,"focus":"休息"},
                  {"weekday":5,"rest":true,"category":"","durationMin":0,"focus":"休息"},
                  {"weekday":6,"rest":true,"category":"","durationMin":0,"focus":"休息"},
                  {"weekday":7,"rest":true,"category":"","durationMin":0,"focus":"休息"}
                ]}
                """);

        List<AiProvider.ScheduleDay> days = provider.planWorkoutSchedule("維持", 4, "medium", "進階");

        assertThat(days).hasSize(7);
        assertThat(days.stream().filter(d -> !d.rest()).count()).isEqualTo(4);  // fallback honours daysPerWeek
    }

    @Test
    void planWorkoutSchedule_fallsBackToTemplate_whenOllamaThrows() {
        when(ollama.chat(any(), any(), any(), anyBoolean(), any()))
                .thenThrow(new OllamaClient.OllamaException("down"));

        List<AiProvider.ScheduleDay> days = provider.planWorkoutSchedule("增肌", 3, "medium", "中階");

        assertThat(days).hasSize(7);
        assertThat(days.stream().filter(d -> !d.rest()).count()).isEqualTo(3);
        assertThat(days.get(0).rest()).isFalse();  // Monday is a training day in every template
        verify(ollama).unload("gemma4:e4b");
    }

    @Test
    void fallbackSchedule_fatLossGoal_includesCardio() {
        List<AiProvider.ScheduleDay> days = provider.fallbackSchedule("減脂", 4);

        assertThat(days.stream().anyMatch(d -> "cardio".equals(d.category()))).isTrue();
    }
}
