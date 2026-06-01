package com.myhealth.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhealth.config.AppProperties;
import java.util.Base64;
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
                你是一名謹慎的健身教練，任務是依照使用者指定的訓練分類、時長與強度，
                產生安全、可執行、保守的訓練菜單。

                嚴格規則：
                1. 只能依照使用者提供的 category、durationMin、intensity 規劃，不可假設傷病、器材、場地或訓練經驗。
                2. 若缺少器材資訊，預設使用徒手或常見低風險動作；不可要求特殊器材。
                3. 不可產生高風險、爆發性過高、需要專人保護或技術門檻高的動作，例如大重量槓鈴、奧林匹克舉重、倒立、翻滾、跳箱極限高度。
                4. 不可輸出醫療、復健或傷病治療處方；如使用者有疼痛或病史，應建議諮詢專業人員，但不要把這句放在 JSON 外。
                5. 動作名稱必須是常見且可理解的繁體中文名稱，不要編造不存在的動作。
                6. 必須推薦 3 到 6 個 items；每個 item 代表一個獨立動作。
                7. sets 必須是 1 到 6 的整數；reps 必須是清楚的次數或秒數字串，例如 "12"、"每側10"、"30s"。
                8. restSec 必須是 15 到 180 的整數；kcal 必須是 5 到 250 的整數，且只是粗估。
                9. note 必須是一句繁體中文動作要點，避免空字串，最多 30 字。
                10. alt 最多 2 個替代動作；替代動作也必須低風險且常見。
                11. 不可輸出 Markdown、註解、推理過程、額外欄位或 JSON 以外的文字。

                強度限制：
                - low：低衝擊、休息較長，避免跳躍與力竭。
                - medium：中等強度，保留 1 到 3 次餘裕。
                - high：可提高密度，但仍不可加入高風險技術動作或力竭要求。

                只允許回傳下列 JSON schema：
                {"items":[
                  {"name":"動作中文名","sets":4,"reps":"次數或秒數（字串）",
                   "restSec":45,"kcal":40,"note":"動作要點","alt":["替代動作1","替代動作2"]}
                ]}
                """;
        String user = "分類: %s%n時長: %d 分鐘%n強度: %s".formatted(category, durationMin, intensity);
        String raw = null;
        try {
            raw = ollama.chat(properties.ai().textModel(), system, user, true, Duration.ofSeconds(45));
            List<ExerciseItem> items = parseWorkoutItems(raw);
            if (items.isEmpty()) {
                log.warn("Ollama returned empty workout items, falling back. raw={}", raw);
                return fallbackWorkout(category);
            }
            List<ExerciseItem> sanitized = sanitizeExerciseItems(items);
            if (sanitized.isEmpty()) {
                log.warn("Ollama workout items were all invalid after sanitization, falling back. raw={}", raw);
                return fallbackWorkout(category);
            }
            return sanitized;
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
    public MealAnalysis analyzeMeal(String description, MealImage image) {
        markUsed();
        boolean hasImage = image != null && image.present();
        String system = """
                你是一名謹慎的營養師，任務是根據「可見的餐點照片」與「使用者文字描述」估算營養。

                嚴格規則：
                1. 只能根據照片中清楚可見的食物、容器、份量線索，以及使用者明確輸入的文字判斷。
                2. 不可猜測看不到的食材、醬料、烹調油、飲料、配菜或品牌；看不清楚時要降低 confidence。
                3. 若無法可靠辨識具體食物，name 請用「無法辨識的餐點」或「疑似主食/疑似肉類/疑似蔬菜」這類保守名稱，不要編造菜名。
                4. 若只能辨識大類，請用大類估算，confidence 不可高於 0.45。
                5. 若完全無法辨識且沒有文字描述，items 必須是空陣列，suggestion 說明需要重新拍攝或手動輸入。
                6. 每個 item 必須代表照片或文字中可辨識的一種食物；最多 5 個 items，不要拆出看不到的成分。
                7. grams、kcal、protein、fat、carb 必須是非負數；confidence 必須介於 0 到 1。
                8. 不可輸出醫療診斷、減重處方或絕對化結論；只能給一般飲食紀錄建議。
                9. 若文字描述與照片衝突，以照片為主，並在 suggestion 簡短提醒「照片與描述可能不一致」。
                10. 不可輸出 Markdown、註解、推理過程、額外欄位或 JSON 以外的文字。

                只允許回傳下列 JSON schema：
                {"items":[{"name":"食物名稱或保守大類","grams":150,"kcal":248,
                          "protein":46.5,"fat":5.4,"carb":0.0,"confidence":0.85}],
                 "suggestion":"一段繁體中文建議，10到30字"}
                """;
        String hint = hasImage ? "照片已隨請求附上；若照片不清楚，請保守回覆，不要猜測具體菜名。" : "";
        String userInput = description == null || description.isBlank() ? "（無文字描述）" : description.strip();
        String user = "餐點描述: %s%n%s".formatted(userInput, hint);
        String model = hasImage ? properties.ai().visionModel() : properties.ai().textModel();
        try {
            List<String> images = hasImage ? List.of(Base64.getEncoder().encodeToString(image.bytes())) : List.of();
            String raw = ollama.chat(model, system, user, images, true, Duration.ofSeconds(60));
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
            ollama.unload(model);
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

    private List<ExerciseItem> sanitizeExerciseItems(List<ExerciseItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .filter(this::validExerciseItem)
                .limit(6)
                .toList();
    }

    private boolean validExerciseItem(ExerciseItem item) {
        return item != null
                && item.name() != null
                && !item.name().isBlank()
                && item.sets() >= 1
                && item.sets() <= 6
                && item.reps() != null
                && !item.reps().isBlank()
                && item.restSec() >= 15
                && item.restSec() <= 180
                && item.kcal() >= 5
                && item.kcal() <= 250
                && item.note() != null
                && !item.note().isBlank();
    }

    MealAnalysis parseMealAnalysis(String json) throws Exception {
        String extracted = extractFirstJsonObject(json);
        JsonNode root = objectMapper.readTree(extracted);
        JsonNode items = root.path("items");
        List<FoodItem> foods = items.isArray()
                ? objectMapper.convertValue(items, new TypeReference<List<FoodItem>>() {
        })
                : List.of();
        foods = sanitizeFoodItems(foods);
        String suggestion = root.path("suggestion").asText("");
        return new MealAnalysis(foods, suggestion);
    }

    private List<FoodItem> sanitizeFoodItems(List<FoodItem> foods) {
        if (foods == null) {
            return List.of();
        }
        return foods.stream()
                .filter(this::validFoodItem)
                .limit(5)
                .toList();
    }

    private boolean validFoodItem(FoodItem item) {
        return item != null
                && item.name() != null
                && !item.name().isBlank()
                && finiteNonNegative(item.grams())
                && item.kcal() >= 0
                && finiteNonNegative(item.protein())
                && finiteNonNegative(item.fat())
                && finiteNonNegative(item.carb())
                && Double.isFinite(item.confidence())
                && item.confidence() >= 0.0
                && item.confidence() <= 1.0;
    }

    private boolean finiteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0;
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
                "waist", List.of(
                        new ExerciseItem("側棒式", 3, "每側30s", 45, 35, "髖部抬高，身體呈一直線", List.of("跪姿側棒式")),
                        new ExerciseItem("俄羅斯轉體", 4, "每側12", 45, 45, "轉動軀幹，骨盆保持穩定", List.of("徒手轉體")),
                        new ExerciseItem("站姿側屈", 3, "每側15", 30, 30, "緩慢側彎，感受側腹收縮", List.of("坐姿側屈"))),
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
        return new MealAnalysis(List.of(), "AI 無法可靠辨識，請重新拍攝或手動輸入。");
    }
}
