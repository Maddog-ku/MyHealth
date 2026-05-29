package com.myhealth.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        Ai ai,
        String uploadDir
) {
    public record Jwt(String secret, int accessTtlMin, int refreshTtlDays) {
        public Duration accessTtl() {
            return Duration.ofMinutes(accessTtlMin);
        }

        public Duration refreshTtl() {
            return Duration.ofDays(refreshTtlDays);
        }
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Ai(
            String provider,
            String ollamaBaseUrl,
            String textModel,
            String visionModel,
            int idleTimeoutSec
    ) {
    }
}
