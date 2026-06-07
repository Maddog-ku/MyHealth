package com.myhealth.user;

import com.myhealth.common.ApiException;
import com.myhealth.common.ErrorCode;
import com.myhealth.common.Hashing;
import com.myhealth.user.SessionDtos.SessionListResponse;
import com.myhealth.user.SessionDtos.SessionResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Active-session management: each non-revoked, unexpired refresh token is one device
 * session. The current session is identified by the caller's own refresh token (passed
 * via header/body), so we never need the raw token in the URL.
 */
@Service
public class SessionService {
    private final RefreshTokenRepository refreshTokens;

    public SessionService(RefreshTokenRepository refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @Transactional(readOnly = true)
    public SessionListResponse list(AppUser user, String currentRawToken) {
        String currentHash = currentRawToken == null || currentRawToken.isBlank()
                ? null : Hashing.sha256(currentRawToken);
        List<SessionResponse> sessions = refreshTokens.findActive(user.getId(), Instant.now()).stream()
                .map(t -> toResponse(t, currentHash))
                .toList();
        return new SessionListResponse(sessions);
    }

    @Transactional
    public void revoke(AppUser user, Long sessionId) {
        RefreshToken token = refreshTokens.findByIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Session not found"));
        if (!token.isRevoked()) {
            token.revoke();
            refreshTokens.save(token);
        }
    }

    /**
     * Log out every other device, keeping only the caller's current session alive. If the
     * supplied token can't be matched to an active session, all active sessions are revoked.
     */
    @Transactional
    public SessionListResponse revokeOthers(AppUser user, String currentRawToken) {
        Long keepId = refreshTokens.findByTokenHash(Hashing.sha256(currentRawToken))
                .filter(t -> t.getUser().getId().equals(user.getId()) && !t.isRevoked())
                .map(RefreshToken::getId)
                .orElse(null);
        if (keepId == null) {
            refreshTokens.revokeAllByUserId(user.getId());
        } else {
            refreshTokens.revokeAllExcept(user.getId(), keepId);
        }
        return list(user, currentRawToken);
    }

    private SessionResponse toResponse(RefreshToken token, String currentHash) {
        boolean current = currentHash != null && currentHash.equals(token.getTokenHash());
        return new SessionResponse(
                token.getId(),
                deviceLabel(token.getDeviceInfo()),
                token.getCreatedAt(),
                token.getLastUsedAt(),
                token.getExpiresAt(),
                current);
    }

    /** Turn a raw User-Agent into a short "Browser · OS" label for display. */
    static String deviceLabel(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "未知裝置";
        }
        String ua = userAgent;
        String os;
        if (ua.contains("iPhone")) {
            os = "iPhone";
        } else if (ua.contains("iPad")) {
            os = "iPad";
        } else if (ua.contains("Android")) {
            os = "Android";
        } else if (ua.contains("Windows")) {
            os = "Windows";
        } else if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            os = "macOS";
        } else if (ua.contains("Linux")) {
            os = "Linux";
        } else {
            os = "其他系統";
        }
        String browser;
        if (ua.contains("Edg")) {
            browser = "Edge";
        } else if (ua.contains("OPR") || ua.contains("Opera")) {
            browser = "Opera";
        } else if (ua.contains("Firefox")) {
            browser = "Firefox";
        } else if (ua.contains("Chrome")) {
            browser = "Chrome";
        } else if (ua.contains("Safari")) {
            browser = "Safari";
        } else {
            browser = "瀏覽器";
        }
        return browser + " · " + os;
    }
}
