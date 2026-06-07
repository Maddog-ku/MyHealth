package com.myhealth.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.myhealth.common.ApiException;
import com.myhealth.common.Hashing;
import com.myhealth.user.SessionDtos.SessionListResponse;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionServiceTest {

    @Mock RefreshTokenRepository refreshTokens;

    SessionService service;
    AppUser user;

    @BeforeEach
    void setUp() {
        service = new SessionService(refreshTokens);
        user = new AppUser();
        user.setEmail("a@b.c");
        user.setRole(Role.USER);
        setId(user, 1L);
    }

    @Test
    void list_flagsCurrentSession_byMatchingRefreshTokenHash() {
        RefreshToken current = token(10L, "raw-current", "Mozilla/5.0 (Macintosh) Chrome/120 Safari/537");
        RefreshToken other = token(11L, "raw-other", "Mozilla/5.0 (iPhone) Safari/604");
        when(refreshTokens.findActive(eq(1L), any())).thenReturn(List.of(current, other));

        SessionListResponse res = service.list(user, "raw-current");

        assertThat(res.sessions()).hasSize(2);
        assertThat(res.sessions().get(0).id()).isEqualTo(10L);
        assertThat(res.sessions().get(0).current()).isTrue();
        assertThat(res.sessions().get(0).device()).isEqualTo("Chrome · macOS");
        assertThat(res.sessions().get(1).current()).isFalse();
        assertThat(res.sessions().get(1).device()).isEqualTo("Safari · iPhone");
    }

    @Test
    void list_marksNoneCurrent_whenNoTokenProvided() {
        when(refreshTokens.findActive(eq(1L), any())).thenReturn(List.of(token(10L, "raw", null)));

        SessionListResponse res = service.list(user, null);

        assertThat(res.sessions().get(0).current()).isFalse();
        assertThat(res.sessions().get(0).device()).isEqualTo("未知裝置");
    }

    @Test
    void revoke_revokesOwnedSession() {
        RefreshToken t = token(10L, "raw", "ua");
        when(refreshTokens.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(t));

        service.revoke(user, 10L);

        assertThat(t.isRevoked()).isTrue();
        verify(refreshTokens).save(t);
    }

    @Test
    void revoke_throwsNotFound_whenSessionMissing() {
        when(refreshTokens.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(user, 99L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void revokeOthers_keepsCurrent_whenTokenMatches() {
        RefreshToken current = token(10L, "raw-current", "ua");
        when(refreshTokens.findByTokenHash(Hashing.sha256("raw-current"))).thenReturn(Optional.of(current));
        when(refreshTokens.findActive(eq(1L), any())).thenReturn(List.of(current));

        service.revokeOthers(user, "raw-current");

        verify(refreshTokens).revokeAllExcept(1L, 10L);
        verify(refreshTokens, never()).revokeAllByUserId(any());
    }

    @Test
    void revokeOthers_revokesAll_whenTokenNotRecognized() {
        when(refreshTokens.findByTokenHash(any())).thenReturn(Optional.empty());
        when(refreshTokens.findActive(eq(1L), any())).thenReturn(List.of());

        service.revokeOthers(user, "stale-token");

        verify(refreshTokens).revokeAllByUserId(1L);
        verify(refreshTokens, never()).revokeAllExcept(any(), any());
    }

    @Test
    void deviceLabel_recognisesCommonBrowsersAndOses() {
        assertThat(SessionService.deviceLabel("Mozilla/5.0 (Windows NT 10.0) Firefox/121")).isEqualTo("Firefox · Windows");
        assertThat(SessionService.deviceLabel("Mozilla/5.0 (Linux; Android 14) Chrome/120")).isEqualTo("Chrome · Android");
        assertThat(SessionService.deviceLabel("Mozilla/5.0 (Windows NT 10.0) Edg/120")).isEqualTo("Edge · Windows");
        assertThat(SessionService.deviceLabel(null)).isEqualTo("未知裝置");
    }

    private RefreshToken token(Long id, String rawToken, String deviceInfo) {
        RefreshToken t = new RefreshToken();
        t.setUser(user);
        t.setTokenHash(Hashing.sha256(rawToken));
        t.setDeviceInfo(deviceInfo);
        t.setCreatedAt(Instant.parse("2026-06-01T00:00:00Z"));
        t.setLastUsedAt(Instant.parse("2026-06-07T00:00:00Z"));
        t.setExpiresAt(Instant.parse("2026-07-01T00:00:00Z"));
        setTokenId(t, id);
        return t;
    }

    private static void setTokenId(RefreshToken token, Long id) {
        try {
            Field f = RefreshToken.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(token, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void setId(AppUser user, Long id) {
        try {
            Field f = AppUser.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
