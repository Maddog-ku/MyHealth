package com.myhealth.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhealth.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Local Ollama-backed AiProvider. Uses {@link OllamaClient} (which sends
 * {@code keep_alive=0} on every call so the model unloads immediately after
 * each response). Falls back to hardcoded templates if Ollama is unreachable
 * or returns malformed JSON, so the application remains usable when no model
 * is available.
 */
@Component
public class LocalAiProvider implements AiProvider {
    private static final Logger log = LoggerFactory.getLogger(LocalAiProvider.class);

    private final AppProperties properties;
    private final OllamaClient ollama;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean loaded = new AtomicBoolean(false);
    private final AtomicReference<Instant> lastUsedAt = new AtomicReference<>();

    public LocalAiProvider(AppProperties properties, OllamaClient ollama, ObjectMapper objectMapper) {
        this.properties = properties;
        this.ollama = ollama;
        this.objectMapper = objectMapper;
    }

    @Override
    public String provider() {
        return properties.ai().provider();
    }

    @Override
    public String textModel() {
        return properties.ai().textModel();
    }

    @Override
    public String visionModel() {
        return properties.ai().visionModel();
    }

    @Override
    public boolean loaded() {
        return loaded.get();
    }

    @Override
    public Instant lastUsedAt() {
        return lastUsedAt.get();
    }

    @Override
    public void markUsed() {
        loaded.set(true);
        lastUsedAt.set(Instant.now());
    }

    @Override
    public void unload() {
        ollama.unload(properties.ai().textModel());
        loaded.set(false);
    }

    @Override
    public List<ExerciseItem> generateWorkout(String category, int durationMin, String intensity) {
        markUsed();
        String system = """
                你是一名專業健身教練。請依照使用者指定的訓練分類、時長與強度，
                推薦 3 到 6 個動作。回答必須是 JSON 物件，schema 如下：
                {"items":[
                  {"name":"動作中文名","sets":4,"reps":"次數或秒數（字串）",
                   "restSec":45,"kcal":40,"note":"動作要點","alt":["替代動作1","替代動作2"]}
                ]}
                只能回 JSON，不要加任何說明文字。""";
        String user = "分類: %s%n時長: %d 分鐘%n強度: %s".formatted(category, durationMin, intensity);
        String raw = null;
        try {
            raw = ollama.chat(properties.ai().textModel(), system, user, true, Duration.ofSeconds(45));
            List<ExerciseItem> items = parseWorkoutItems(raw);
            if (items.isEmpty()) {
                log.warn("Ollama returned empty workout items, falling back. raw={}", raw);
                return fallbackWorkout(category);
            }
            // Cap to avoid pathological outputs
            return items.size() > 8 ? items.subList(0, 8) : items;
        } catch (OllamaClient.OllamaException ex) {
            log.warn("Ollama generateWorkout failed: {}", ex.getMessage());
            return fallbackWorkout(category);
        } catch (Exception ex) {
            log.warn("Workout parse failed, falling back. raw={}", raw, ex);
            return fallbackWorkout(category);
        } finally {
            // Proactively release the model after each request, regardless of outcome.
            // OllamaClient.chat uses keep_alive=10s; this call drops it to 0 immediately.
            ollama.unload(properties.ai().textModel());
            loaded.set(false);
        }
    }

    @Override
    public MealAnalysis analyzeMeal(String description, boolean hasImage) {
        markUsed();
        String system = """
                你是一名專業營養師。請依照使用者描述的餐點估算份量與熱量（蛋白、脂肪、碳水都用公克）。
                回答必須是 JSON 物件，schema 如下：
                {"items":[{"name":"食物名稱","grams":150,"kcal":248,
                          "protein":46.5,"fat":5.4,"carb":0.0,"confidence":0.85}],
                 "suggestion":"一段中文建議，10–30 字"}
                估算誤差以 ±20% 為宜，confidence 表示估算可信度（0~1）。
                只能回 JSON，不要加任何說明文字。""";
        String hint = hasImage ? "（使用者也附了餐點照片，文字可能未填寫，此時請以照片可能內容估算一份合理餐點）" : "";
        String userInput = description == null || description.isBlank() ? "（無文字描述）" : description.strip();
        String user = "餐點描述: %s%n%s".formatted(userInput, hint);
        try {
            String raw = ollama.chat(properties.ai().textModel(), system, user, true, Duration.ofSeconds(60));
            MealAnalysis parsed = parseMealAnalysis(raw);
            if (parsed.items().isEmpty()) {
                log.warn("Ollama returned empty meal items, falling back (raw={})", raw);
                return fallbackMeal(userInput, hasImage);
            }
            return parsed;
        } catch (OllamaClient.OllamaException ex) {
            log.warn("Ollama analyzeMeal failed: {}", ex.getMessage());
            return fallbackMeal(userInput, hasImage);
        } catch (Exception ex) {
            log.warn("Meal analysis parse failed, falling back to stub", ex);
            return fallbackMeal(userInput, hasImage);
        } finally {
            ollama.unload(properties.ai().textModel());
            loaded.set(false);
        }
    }

    List<ExerciseItem> parseWorkoutItems(String json) throws Exception {
        String extracted = extractFirstJsonObject(json);
        JsonNode root = objectMapper.readTree(extracted);
        JsonNode items = root.path("items");
        if (!items.isArray()) {
            return List.of();
        }
        return objectMapper.convertValue(items, new TypeReference<>() {
        });
    }

    MealAnalysis parseMealAnalysis(String json) throws Exception {
        String extracted = extractFirstJsonObject(json);
        JsonNode root = objectMapper.readTree(extracted);
        JsonNode items = root.path("items");
        List<FoodItem> foods = items.isArray()
                ? objectMapper.convertValue(items, new TypeReference<List<FoodItem>>() {
        })
                : List.of();
        String suggestion = root.path("suggestion").asText("");
        return new MealAnalysis(foods, suggestion);
    }

    /**
     * Some models (e.g. gemma4) prepend or append "thinking" prose around the JSON
     * payload even in JSON mode. Walk the string, tracking braces while respecting
     * string literals and escapes, and return the first balanced top-level object.
     */
    static String extractFirstJsonObject(String raw) {
        if (raw == null) return "";
        int start = raw.indexOf('{');
        if (start < 0) return raw;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return raw.substring(start, i + 1);
                }
            }
        }
        return raw.substring(start);  // unbalanced — try as-is, parser will fail and we fall back
    }

    // -- offline fallback templates --

    private List<ExerciseItem> fallbackWorkout(String category) {
        Map<String, List<ExerciseItem>> templates = Map.of(
                "abs", List.of(
                        new ExerciseItem("捲腹", 4, "15", 45, 40, "下背貼地，避免用脖子出力", List.of("死蟲式")),
                        new ExerciseItem("棒式", 3, "45s", 45, 35, "身體維持一直線", List.of("跪姿棒式")),
                        new ExerciseItem("登山者", 3, "30s", 60, 55, "穩定核心，膝蓋朝胸口收", List.of("慢速登山者"))),
                "legs", List.of(
                        new ExerciseItem("深蹲", 4, "12", 60, 70, "膝蓋朝腳尖方向", List.of("椅子深蹲")),
                        new ExerciseItem("弓箭步", 3, "每側10", 60, 65, "保持軀幹穩定", List.of("反向弓箭步")),
                        new ExerciseItem("臀橋", 4, "15", 45, 45, "頂端夾臀一秒", List.of("單腳臀橋"))),
                "cardio", List.of(
                        new ExerciseItem("開合跳", 4, "45s", 45, 80, "落地保持輕盈", List.of("踏步開合")),
                        new ExerciseItem("高抬腿", 4, "30s", 45, 75, "核心收緊", List.of("原地快走")),
                        new ExerciseItem("波比跳", 3, "10", 75, 95, "依能力調整速度", List.of("半波比"))));
        return templates.getOrDefault(category, List.of(
                new ExerciseItem("動態暖身", 3, "60s", 30, 30, "活動肩髖關節", List.of("快走暖身")),
                new ExerciseItem("徒手深蹲", 3, "12", 60, 55, "控制下放速度", List.of("椅子深蹲")),
                new ExerciseItem("棒式", 3, "30s", 45, 35, "維持呼吸", List.of("跪姿棒式"))));
    }

    private MealAnalysis fallbackMeal(String description, boolean hasImage) {
        String name = description == null || description.isBlank() ? "餐點照片估算" : description;
        FoodItem item = new FoodItem(name, 250, hasImage ? 520 : 360, 28.0, 14.0, 42.0, hasImage ? 0.55 : 0.6);
        return new MealAnalysis(List.of(item), "AI 暫時不可用，回退到平均估算；請依實際份量手動修正。");
    }
}
