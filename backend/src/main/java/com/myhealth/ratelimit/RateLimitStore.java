package com.myhealth.ratelimit;

import java.time.Duration;

public interface RateLimitStore {
    void check(String key, int limit, Duration window);
}
