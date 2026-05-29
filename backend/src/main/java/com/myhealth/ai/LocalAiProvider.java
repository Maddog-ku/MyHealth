package com.myhealth.ai;

import com.myhealth.config.AppProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class LocalAiProvider implements AiProvider {
    private final AppProperties properties;
    private final AtomicBoolean loaded = new AtomicBoolean(false);
    private final AtomicReference<Instant> lastUsedAt = new AtomicReference<>();

    public LocalAiProvider(AppProperties properties) {
        this.properties = properties;
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
        loaded.set(false);
    }

    @Override
    public List<ExerciseItem> generateWorkout(String category, int durationMin, String intensity) {
        markUsed();
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

    @Override
    public MealAnalysis analyzeMeal(String description, boolean hasImage) {
        markUsed();
        String name = description == null || description.isBlank() ? "餐點照片估算" : description.strip();
        FoodItem item = new FoodItem(name, hasImage ? 350 : 250, hasImage ? 520 : 360, 28.0, 14.0, 42.0, hasImage ? 0.72 : 0.85);
        return new MealAnalysis(List.of(item), "目前為本地假 AI 估算；請依實際份量手動修正。");
    }
}
