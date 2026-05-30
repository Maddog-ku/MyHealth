package com.myhealth.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;

final class AuthRateLimitKeys {

    private AuthRateLimitKeys() {
    }

    static String register(HttpServletRequest request) {
        return "auth:register:%s".formatted(clientIp(request, false));
    }

    static String register(HttpServletRequest request, boolean trustForwardedFor) {
        return "auth:register:%s".formatted(clientIp(request, trustForwardedFor));
    }

    static String login(HttpServletRequest request, String email, boolean trustForwardedFor) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return "auth:login:%s:%s".formatted(clientIp(request, trustForwardedFor), normalizedEmail);
    }

    static String refresh(HttpServletRequest request, boolean trustForwardedFor) {
        return "auth:refresh:%s".formatted(clientIp(request, trustForwardedFor));
    }

    static String clientIp(HttpServletRequest request, boolean trustForwardedFor) {
        String forwardedFor = trustForwardedFor ? request.getHeader("X-Forwarded-For") : null;
        if (forwardedFor != null && !forwardedFor.isBlank() && forwardedFor.length() <= 256) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
