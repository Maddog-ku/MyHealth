package com.myhealth.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
                  {"name":"弓箭步","sets":3,"reps":"每側10","restSec":60,"kcal":65,"note":"","alt":[]}
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
    void generateWorkout_capsLargeOutputs_toEightItems() {
        StringBuilder json = new StringBuilder("{\"items\":[");
        for (int i = 0; i < 20; i++) {
            json.append("{\"name\":\"E").append(i).append("\",\"sets\":1,\"reps\":\"1\",\"restSec\":1,\"kcal\":1,\"note\":\"\",\"alt\":[]}");
            if (i < 19) json.append(',');
        }
        json.append("]}");
        when(ollama.chat(any(), any(), any(), eq(true), any())).thenReturn(json.toString());

        List<ExerciseItem> items = provider.generateWorkout("abs", 30, "medium");

        assertThat(items).hasSize(8);
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
        when(ollama.chat(any(), any(), any(), eq(true), any())).thenReturn(json);

        MealAnalysis result = provider.analyzeMeal("雞胸肉沙拉", false);

        assertThat(result.items()).hasSize(1);
        FoodItem food = result.items().get(0);
        assertThat(food.name()).isEqualTo("雞胸肉");
        assertThat(food.kcal()).isEqualTo(248);
        assertThat(food.protein()).isEqualTo(46.5);
        assertThat(result.suggestion()).isEqualTo("蛋白足夠，可加碳水");
    }

    @Test
    void analyzeMeal_fallsBackToStub_whenOllamaUnavailable() {
        when(ollama.chat(any(), any(), any(), anyBoolean(), any()))
                .thenThrow(new OllamaClient.OllamaException("timeout"));

        MealAnalysis result = provider.analyzeMeal("焗烤起司飯", false);

        assertThat(result.items()).hasSize(1);
        assertThat(result.suggestion()).contains("AI 暫時不可用");
    }

    @Test
    void analyzeMeal_fallsBackToStub_whenItemsArrayEmpty() {
        when(ollama.chat(any(), any(), any(), anyBoolean(), any()))
                .thenReturn("{\"items\":[],\"suggestion\":\"…\"}");

        MealAnalysis result = provider.analyzeMeal("白飯", false);

        assertThat(result.items()).hasSize(1);
        assertThat(result.suggestion()).contains("AI 暫時不可用");
    }

    @Test
    void unload_delegatesToOllamaAndFlipsLoadedFlag() {
        provider.markUsed();
        assertThat(provider.loaded()).isTrue();

        provider.unload();

        verify(ollama).unload("gemma4:e4b");
        assertThat(provider.loaded()).isFalse();
    }
}
