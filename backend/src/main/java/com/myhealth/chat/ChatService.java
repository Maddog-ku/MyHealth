package com.myhealth.chat;

import com.myhealth.ai.AiProvider;
import com.myhealth.ai.AiProvider.ChatTurn;
import com.myhealth.chat.ChatDtos.ChatMessageResponse;
import com.myhealth.chat.ChatDtos.ChatReplyResponse;
import com.myhealth.stats.StatsDtos.DailyStatsResponse;
import com.myhealth.stats.StatsService;
import com.myhealth.user.AppUser;
import com.myhealth.user.Profile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
    /** How many prior messages to feed back into the model as context. */
    private static final int HISTORY_WINDOW = 12;

    private final ChatMessageRepository messages;
    private final AiProvider provider;
    private final StatsService stats;

    public ChatService(ChatMessageRepository messages, AiProvider provider, StatsService stats) {
        this.messages = messages;
        this.provider = provider;
        this.stats = stats;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> history(AppUser user) {
        return messages.findByUserIdOrderByCreatedAtAsc(user.getId()).stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    @Transactional
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

        String reply = provider.chat(buildContext(user), turns, text);
        if (reply == null || reply.isBlank()) {
            // content is NOT NULL; never let a misbehaving provider trigger a constraint error.
            reply = "抱歉，我現在無法回覆，請稍後再試一次。";
        }
        ChatMessage assistantMessage = messages.save(new ChatMessage(user, "assistant", reply));

        return new ChatReplyResponse(ChatMessageResponse.from(userMessage), ChatMessageResponse.from(assistantMessage));
    }

    @Transactional
    public void clear(AppUser user) {
        messages.deleteByUserId(user.getId());
    }

    /** A compact factual block injected into the system prompt. */
    private String buildContext(AppUser user) {
        Profile p = user.getProfile();
        StringBuilder sb = new StringBuilder();
        sb.append("暱稱: ").append(user.getName()).append('\n');
        sb.append("性別: ").append(genderLabel(p)).append('\n');
        if (p.getAge() != null) {
            sb.append("年齡: ").append(p.getAge()).append(" 歲\n");
        }
        appendNumber(sb, "身高", p.getHeightCm(), "cm");
        appendNumber(sb, "體重", p.getWeightKg(), "kg");
        appendNumber(sb, "體脂率", p.getBodyFatPct(), "%");
        appendNumber(sb, "肌肉量", p.getMuscleMassKg(), "kg");
        appendNumber(sb, "腰圍", p.getWaistCm(), "cm");
        if (p.getBmrKcal() != null) {
            sb.append("基礎代謝: ").append(p.getBmrKcal()).append(" kcal\n");
        }
        sb.append("目標: ").append(goalLabel(p)).append('\n');
        sb.append("訓練經驗: ").append(experienceLabel(p)).append('\n');

        try {
            DailyStatsResponse today = stats.daily(user, LocalDate.now());
            sb.append("今日攝取: ").append(today.intakeKcal()).append(" kcal\n");
            sb.append("今日消耗: ").append(today.burnKcal()).append(" kcal\n");
            sb.append("今日淨熱量: ").append(today.netKcal()).append(" kcal\n");
            sb.append("今日熱量目標: ").append(today.goalKcal()).append(" kcal\n");
            sb.append("今日運動: 已完成 ").append(today.workoutsDone())
                    .append(" / 共 ").append(today.workoutsPlanned()).append(" 份\n");
        } catch (RuntimeException ex) {
            // Stats are best-effort context; never block the chat if they fail.
        }
        return sb.toString();
    }

    private void appendNumber(StringBuilder sb, String label, BigDecimal value, String unit) {
        if (value != null) {
            sb.append(label).append(": ").append(value.stripTrailingZeros().toPlainString()).append(' ').append(unit).append('\n');
        }
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
