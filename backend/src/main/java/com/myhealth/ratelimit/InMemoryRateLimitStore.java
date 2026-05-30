package com.myhealth.ratelimit;

import com.myhealth.common.ApiException;
import com.myhealth.common.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.rate-limit.backend", havingValue = "memory", matchIfMissing = true)
public class InMemoryRateLimitStore implements RateLimitStore {
    private static final int CLEANUP_INTERVAL = 256;
    private static final int CLEANUP_SIZE_THRESHOLD = 10_000;

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicInteger checks = new AtomicInteger();

    public InMemoryRateLimitStore() {
        this(Clock.systemUTC());
    }

    public InMemoryRateLimitStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void check(String key, int limit, Duration windowLength) {
        Instant now = Instant.now(clock);
        cleanupExpiredWindowsIfNeeded(now);
        Window window = windows.computeIfAbsent(key, ignored -> new Window(now.plus(windowLength)));
        synchronized (window) {
            if (!now.isBefore(window.resetAt)) {
                window.resetAt = now.plus(windowLength);
                window.count = 0;
            }
            if (window.count >= limit) {
                throw rateLimited();
            }
            window.count++;
        }
    }

    int windowCount() {
        return windows.size();
    }

    private void cleanupExpiredWindowsIfNeeded(Instant now) {
        int currentChecks = checks.incrementAndGet();
        if (windows.size() < CLEANUP_SIZE_THRESHOLD && currentChecks % CLEANUP_INTERVAL != 0) {
            return;
        }
        windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().resetAt));
    }

    private ApiException rateLimited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED,
                "Too many requests. Please retry later.");
    }

    private static class Window {
        private Instant resetAt;
        private int count;

        private Window(Instant resetAt) {
            this.resetAt = resetAt;
        }
    }
}
