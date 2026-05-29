package com.myhealth.ai;

import java.time.Instant;

public final class AiDtos {
    private AiDtos() {
    }

    public record AiStatusResponse(
            String provider,
            String textModel,
            String visionModel,
            boolean loaded,
            Instant lastUsedAt,
            int idleTimeoutSec
    ) {
    }

    public record UnloadResponse(boolean unloaded) {
    }
}
