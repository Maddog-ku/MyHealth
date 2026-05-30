package com.myhealth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
    @NotNull
    private Backend backend = Backend.MEMORY;
    @Min(1)
    private int registerLimit = 5;
    @Min(1)
    private int loginLimit = 10;
    @Min(1)
    private int refreshLimit = 30;
    @Min(1)
    private int aiLimit = 20;
    @NotNull
    private Duration window = Duration.ofMinutes(1);
    private boolean trustForwardedFor = false;
    @Valid
    @NotNull
    private Redis redis = new Redis();

    public Backend getBackend() {
        return backend;
    }

    public void setBackend(Backend backend) {
        this.backend = backend;
    }

    public int getLoginLimit() {
        return loginLimit;
    }

    public int getRegisterLimit() {
        return registerLimit;
    }

    public void setRegisterLimit(int registerLimit) {
        this.registerLimit = registerLimit;
    }

    public void setLoginLimit(int loginLimit) {
        this.loginLimit = loginLimit;
    }

    public int getRefreshLimit() {
        return refreshLimit;
    }

    public void setRefreshLimit(int refreshLimit) {
        this.refreshLimit = refreshLimit;
    }

    public int getAiLimit() {
        return aiLimit;
    }

    public void setAiLimit(int aiLimit) {
        this.aiLimit = aiLimit;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        this.window = window;
    }

    public boolean isTrustForwardedFor() {
        return trustForwardedFor;
    }

    public void setTrustForwardedFor(boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public Redis getRedis() {
        return redis;
    }

    public void setRedis(Redis redis) {
        this.redis = redis;
    }

    public enum Backend {
        MEMORY,
        REDIS
    }

    public static class Redis {
        @NotBlank
        private String uri = "redis://localhost:6379";
        @NotBlank
        private String keyPrefix = "myhealth:rate-limit";
        private boolean failOpen = false;
        @NotNull
        private Duration requestTimeout = Duration.ofSeconds(2);
        @NotNull
        private Duration ttlPadding = Duration.ofSeconds(10);

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public boolean isFailOpen() {
            return failOpen;
        }

        public void setFailOpen(boolean failOpen) {
            this.failOpen = failOpen;
        }

        public Duration getRequestTimeout() {
            return requestTimeout;
        }

        public void setRequestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
        }

        public Duration getTtlPadding() {
            return ttlPadding;
        }

        public void setTtlPadding(Duration ttlPadding) {
            this.ttlPadding = ttlPadding;
        }
    }
}
