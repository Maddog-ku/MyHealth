package com.myhealth.ai;

import com.myhealth.config.RateLimitProperties;
import com.myhealth.ratelimit.RateLimitStore;
import com.myhealth.user.AppUser;
import org.springframework.stereotype.Service;

@Service
public class AiEndpointRateLimiter {
    private final RateLimitStore store;
    private final RateLimitProperties properties;

    public AiEndpointRateLimiter(RateLimitStore store, RateLimitProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    public void checkWorkoutGenerate(AppUser user) {
        store.check("ai:workouts:generate:%s".formatted(userKey(user)), properties.getAiLimit(), properties.getWindow());
    }

    public void checkMealCreate(AppUser user) {
        store.check("ai:meals:create:%s".formatted(userKey(user)), properties.getAiLimit(), properties.getWindow());
    }

    public void checkChat(AppUser user) {
        store.check("ai:chat:%s".formatted(userKey(user)), properties.getAiLimit(), properties.getWindow());
    }

    public void checkReport(AppUser user) {
        store.check("ai:reports:weekly:%s".formatted(userKey(user)), properties.getAiLimit(), properties.getWindow());
    }

    public void checkSchedulePlan(AppUser user) {
        store.check("ai:workouts:schedule:%s".formatted(userKey(user)), properties.getAiLimit(), properties.getWindow());
    }

    private String userKey(AppUser user) {
        if (user.getId() != null) {
            return "user:%d".formatted(user.getId());
        }
        return "email:%s".formatted(user.getEmail());
    }
}
