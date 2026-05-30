package com.myhealth.auth;

import com.myhealth.config.RateLimitProperties;
import com.myhealth.ratelimit.RateLimitStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class AuthRateLimiter {
    private final RateLimitStore store;
    private final RateLimitProperties properties;

    public AuthRateLimiter(RateLimitStore store, RateLimitProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    public void checkRegister(HttpServletRequest request) {
        store.check(AuthRateLimitKeys.register(request, properties.isTrustForwardedFor()),
                properties.getRegisterLimit(), properties.getWindow());
    }

    public void checkLogin(HttpServletRequest request, String email) {
        store.check(AuthRateLimitKeys.login(request, email, properties.isTrustForwardedFor()),
                properties.getLoginLimit(), properties.getWindow());
    }

    public void checkRefresh(HttpServletRequest request) {
        store.check(AuthRateLimitKeys.refresh(request, properties.isTrustForwardedFor()),
                properties.getRefreshLimit(), properties.getWindow());
    }
}
