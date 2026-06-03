package com.myhealth.meal;

import com.myhealth.common.ApiException;
import com.myhealth.common.ErrorCode;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

final class MealInputGuard {
    private static final int MAX_DESCRIPTION_LENGTH = 300;

    private static final Pattern FOOD_HINT = Pattern.compile("""
            (?iu)(早餐|午餐|晚餐|宵夜|點心|餐點|便當|飯|米飯|白飯|糙米|麵|麵包|吐司|粥|湯|沙拉|壽司|水餃|\
            雞|雞胸|牛|牛肉|豬|豬肉|魚|鮭魚|蝦|蛋|豆腐|起司|乳酪|優格|牛奶|豆漿|咖啡|茶|果汁|水|\
            蔬菜|青菜|花椰菜|地瓜|馬鈴薯|玉米|水果|香蕉|蘋果|燕麥|堅果|蛋白|碳水|脂肪|熱量|卡路里|\
            kcal|calorie|rice|noodle|bread|toast|oat|chicken|beef|pork|fish|salmon|shrimp|egg|tofu|cheese|\
            yogurt|milk|coffee|tea|juice|salad|vegetable|banana|apple|potato|meal|breakfast|lunch|dinner|snack)
            """.replaceAll("\\s+", ""));

    private static final Pattern PROMPT_INJECTION_HINT = Pattern.compile(
            "(?iu)(忽略.*規則|忽略.*指示|系統提示|開發者訊息|prompt|system prompt|developer message|ignore previous|ignore above|json schema|扮演|角色扮演)");

    private MealInputGuard() {
    }

    static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.strip().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    static void validateDescription(String description) {
        validateDescription(description, true);
    }

    /**
     * @param requireFoodHint when true, the text must contain a recognised food keyword.
     *                        The chat-logging path passes false: the model already judged
     *                        this to be a meal, and the keyword whitelist can never cover
     *                        every food (e.g. 芒果), so enforcing it there wrongly rejects
     *                        valid meals. Length + prompt-injection checks always apply.
     */
    static void validateDescription(String description, boolean requireFoodHint) {
        String normalized = normalizeDescription(description);
        if (normalized == null) {
            return;
        }
        if (normalized.length() > MAX_DESCRIPTION_LENGTH) {
            throw invalid("餐點描述請控制在 300 字內，並只填寫食物、飲品與份量。");
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (PROMPT_INJECTION_HINT.matcher(lower).find()) {
            throw invalid("請只輸入餐點內容，不要輸入指令、角色扮演或系統提示文字。");
        }
        if (requireFoodHint && !FOOD_HINT.matcher(lower).find()) {
            throw invalid("請確認輸入內容是否為飲食或餐點描述，例如「雞胸肉 150g、白飯一碗」。");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, message);
    }
}
