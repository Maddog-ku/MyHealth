package com.myhealth.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myhealth.common.ApiException;
import com.myhealth.common.ErrorCode;
import com.myhealth.config.RateLimitProperties;
import com.myhealth.ratelimit.InMemoryRateLimitStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthRateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final RateLimitProperties properties = new RateLimitProperties();
    private final AuthRateLimiter limiter = new AuthRateLimiter(new InMemoryRateLimitStore(clock), properties);

    @Test
    void checkRegister_blocksAfterLimitForSameIp() {
        MockHttpServletRequest request = request("203.0.113.5");
        for (int i = 0; i < 5; i++) {
            limiter.checkRegister(request);
        }

        assertThatThrownBy(() -> limiter.checkRegister(request))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void checkLogin_blocksAfterLimitForSameIpAndEmail() {
        MockHttpServletRequest request = request("203.0.113.10");
        for (int i = 0; i < 10; i++) {
            limiter.checkLogin(request, "USER@example.com");
        }

        assertThatThrownBy(() -> limiter.checkLogin(request, "user@example.com"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void checkLogin_resetsAfterWindow() {
        MockHttpServletRequest request = request("203.0.113.20");
        for (int i = 0; i < 10; i++) {
            limiter.checkLogin(request, "user@example.com");
        }

        clock.advance(Duration.ofMinutes(1).plusSeconds(1));

        limiter.checkLogin(request, "user@example.com");
    }

    @Test
    void checkRefresh_blocksAfterLimitForSameIp() {
        MockHttpServletRequest request = request("203.0.113.30");
        for (int i = 0; i < 30; i++) {
            limiter.checkRefresh(request);
        }

        assertThatThrownBy(() -> limiter.checkRefresh(request))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void clientIp_ignoresForwardedForByDefault() {
        MockHttpServletRequest request = request("10.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.10, 10.0.0.2");
        for (int i = 0; i < 10; i++) {
            limiter.checkLogin(request, "user@example.com");
        }

        assertThatThrownBy(() -> limiter.checkLogin(request, "user@example.com"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void clientIp_usesForwardedForOnlyWhenConfigured() {
        properties.setTrustForwardedFor(true);
        MockHttpServletRequest first = request("10.0.0.1");
        first.addHeader("X-Forwarded-For", "198.51.100.10, 10.0.0.2");
        MockHttpServletRequest second = request("10.0.0.1");
        second.addHeader("X-Forwarded-For", "198.51.100.11, 10.0.0.2");

        for (int i = 0; i < 10; i++) {
            limiter.checkLogin(first, "user@example.com");
        }

        limiter.checkLogin(second, "user@example.com");
        assertThatThrownBy(() -> limiter.checkLogin(first, "user@example.com"))
                .isInstanceOf(ApiException.class);
    }

    private MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    private static class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-05-30T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
