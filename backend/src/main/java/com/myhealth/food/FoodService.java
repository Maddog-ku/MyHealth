package com.myhealth.food;

import com.myhealth.food.FoodDtos.FoodResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class FoodService {
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;

    private static final List<FoodCatalogItem> CATALOG = List.of(
            item("chicken-breast", "雞胸肉", "蛋白質", List.of("雞肉", "chicken breast", "chicken"), 150, 165, 31.0, 3.6, 0.0),
            item("egg", "雞蛋", "蛋白質", List.of("水煮蛋", "蛋", "egg"), 50, 155, 12.6, 10.6, 1.1),
            item("salmon", "鮭魚", "蛋白質", List.of("三文魚", "salmon"), 120, 208, 20.0, 13.0, 0.0),
            item("tofu", "板豆腐", "蛋白質", List.of("豆腐", "tofu"), 100, 88, 8.1, 4.8, 1.9),
            item("greek-yogurt", "希臘優格", "蛋白質", List.of("優格", "yogurt", "greek yogurt"), 170, 97, 9.0, 5.0, 3.6),
            item("white-rice", "白飯", "主食", List.of("米飯", "飯", "rice"), 150, 130, 2.7, 0.3, 28.2),
            item("brown-rice", "糙米飯", "主食", List.of("糙米", "brown rice"), 150, 111, 2.6, 0.9, 23.0),
            item("sweet-potato", "地瓜", "主食", List.of("番薯", "sweet potato"), 120, 86, 1.6, 0.1, 20.1),
            item("oatmeal", "燕麥", "主食", List.of("燕麥片", "oats", "oat"), 40, 389, 16.9, 6.9, 66.3),
            item("whole-wheat-toast", "全麥吐司", "主食", List.of("吐司", "麵包", "toast", "bread"), 60, 247, 13.0, 4.2, 41.0),
            item("broccoli", "花椰菜", "蔬菜", List.of("青花菜", "broccoli"), 100, 34, 2.8, 0.4, 6.6),
            item("lettuce", "生菜", "蔬菜", List.of("萵苣", "沙拉菜", "lettuce"), 80, 15, 1.4, 0.2, 2.9),
            item("spinach", "菠菜", "蔬菜", List.of("spinach"), 100, 23, 2.9, 0.4, 3.6),
            item("banana", "香蕉", "水果", List.of("banana"), 120, 89, 1.1, 0.3, 22.8),
            item("apple", "蘋果", "水果", List.of("apple"), 180, 52, 0.3, 0.2, 13.8),
            item("avocado", "酪梨", "脂肪", List.of("牛油果", "avocado"), 100, 160, 2.0, 14.7, 8.5),
            item("almonds", "杏仁", "脂肪", List.of("堅果", "almond", "almonds"), 28, 579, 21.2, 49.9, 21.6),
            item("olive-oil", "橄欖油", "脂肪", List.of("油", "olive oil"), 10, 884, 0.0, 100.0, 0.0)
    );

    public List<FoodResponse> search(String query, Integer limit) {
        String q = normalize(query);
        if (q.isEmpty()) {
            return List.of();
        }
        int cap = Math.min(limit == null ? DEFAULT_LIMIT : Math.max(limit, 1), MAX_LIMIT);
        return CATALOG.stream()
                .filter(item -> item.matches(q))
                .limit(cap)
                .map(FoodCatalogItem::toResponse)
                .toList();
    }

    private static FoodCatalogItem item(String id, String name, String category, List<String> aliases,
                                        int servingGrams, int kcalPer100g, double proteinPer100g,
                                        double fatPer100g, double carbPer100g) {
        return new FoodCatalogItem(id, name, category, aliases, servingGrams, kcalPer100g,
                proteinPer100g, fatPer100g, carbPer100g);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record FoodCatalogItem(
            String id,
            String name,
            String category,
            List<String> aliases,
            int servingGrams,
            int kcalPer100g,
            double proteinPer100g,
            double fatPer100g,
            double carbPer100g
    ) {
        boolean matches(String q) {
            return normalize(name).contains(q)
                    || normalize(category).contains(q)
                    || aliases.stream().anyMatch(alias -> normalize(alias).contains(q));
        }

        FoodResponse toResponse() {
            double ratio = servingGrams / 100.0;
            return new FoodResponse(
                    id,
                    name,
                    category,
                    servingGrams,
                    (int) Math.round(kcalPer100g * ratio),
                    grams(proteinPer100g * ratio),
                    grams(fatPer100g * ratio),
                    grams(carbPer100g * ratio),
                    aliases
            );
        }

        private static BigDecimal grams(double value) {
            return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
        }
    }
}
