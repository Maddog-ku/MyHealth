package com.myhealth.ai;

import com.myhealth.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AiIdleWatcher {
    private final AiProvider provider;
    private final AppProperties properties;

    public AiIdleWatcher(AiProvider provider, AppProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${AI_IDLE_CHECK_INTERVAL_MS:300000}")
    void unloadWhenIdle() {
        Instant lastUsedAt = provider.lastUsedAt();
        if (!provider.loaded() || lastUsedAt == null) {
            return;
        }
        Duration idle = Duration.between(lastUsedAt, Instant.now());
        if (idle.getSeconds() > properties.ai().idleTimeoutSec()) {
            provider.unload();
        }
    }
}
