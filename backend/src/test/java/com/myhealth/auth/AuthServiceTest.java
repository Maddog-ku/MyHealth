package com.myhealth.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.myhealth.auth.AuthDtos.AuthResponse;
import com.myhealth.auth.AuthDtos.LoginRequest;
import com.myhealth.auth.AuthDtos.RefreshRequest;
import com.myhealth.auth.AuthDtos.RegisterRequest;
import com.myhealth.common.ApiException;
import com.myhealth.common.ErrorCode;
import com.myhealth.common.Hashing;
import com.myhealth.config.AppProperties;
import com.myhealth.user.AppUser;
import com.myhealth.user.BodyMeasurement;
import com.myhealth.user.BodyMeasurementRepository;
import com.myhealth.user.Experience;
import com.myhealth.user.Gender;
import com.myhealth.user.Goal;
import com.myhealth.user.RefreshToken;
import com.myhealth.user.RefreshTokenRepository;
import com.myhealth.user.Role;
import com.myhealth.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock BodyMeasurementRepository bodyMeasurements;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;

    AppProperties properties;
    AuthService service;

    @BeforeEach
    void setUp() {
        properties = new AppProperties(
                new AppProperties.Jwt("test-secret-test-secret-test-secret-32bytes!!", 15, 30),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Ai("local", "http://localhost:11434", "qwen2.5:7b", "qwen2-vl:7b", 300),
                "./uploads");
        service = new AuthService(users, refreshTokens, bodyMeasurements, passwordEncoder, jwtService, properties);
    }

    private RegisterRequest validRegisterRequest() {
        return new RegisterRequest(
                "Alice@Example.com",
                "Secret123",
                "  Alice  ",
                Gender.female,
                new BigDecimal("165.0"),
                new BigDecimal("55.0"),
                30,
                new BigDecimal("22.0"),
                new BigDecimal("23.0"),
                1400,
                new BigDecimal("70.0"),
                new BigDecimal("55.0"),
                Goal.fat_loss,
                List.of("dumbbell", "mat"),
                Experience.beginner,
                null,
                null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T readField(Object target, String name, Class<T> type) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(target);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private AppUser persistedUser() {
        AppUser u = new AppUser();
        u.setEmail("alice@example.com");
        u.setName("Alice");
        u.setPasswordHash("hashed");
        u.setRole(Role.USER);
        try {
            var idField = AppUser.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(u, 42L);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        return u;
    }

    // --- register ---

    @Test
    void register_persistsUserProfileAndInitialMeasurement_andNormalizesEmail() {
        RegisterRequest req = validRegisterRequest();
        when(users.existsByEmailIgnoreCase("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123")).thenReturn("hashed");
        when(users.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            try {
                var idField = AppUser.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(u, 1L);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            return u;
        });

        AuthDtos.UserResponse response = service.register(req);

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(userCaptor.capture());
        AppUser savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("alice@example.com");
        assertThat(savedUser.getName()).isEqualTo("Alice");
        assertThat(savedUser.getPasswordHash()).isEqualTo("hashed");
        assertThat(savedUser.getProfile()).isNotNull();
        assertThat(savedUser.getProfile().getEquipment()).containsExactly("dumbbell", "mat");
        assertThat(savedUser.getProfile().getTheme()).isEqualTo("system");
        assertThat(savedUser.getProfile().getLanguage()).isEqualTo("zh-TW");

        ArgumentCaptor<BodyMeasurement> measurementCaptor = ArgumentCaptor.forClass(BodyMeasurement.class);
        verify(bodyMeasurements).save(measurementCaptor.capture());
        BodyMeasurement m = measurementCaptor.getValue();
        assertThat(readField(m, "weightKg", BigDecimal.class)).isEqualByComparingTo("55.0");
        assertThat(readField(m, "bodyFatPct", BigDecimal.class)).isEqualByComparingTo("22.0");
        assertThat(readField(m, "note", String.class)).isEqualTo("registration");
        assertThat(readField(m, "user", AppUser.class)).isSameAs(savedUser);

        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.name()).isEqualTo("Alice");
    }

    @Test
    void register_throwsConflict_whenEmailExists() {
        RegisterRequest req = validRegisterRequest();
        when(users.existsByEmailIgnoreCase("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(req))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ae = (ApiException) e;
                    assertThat(ae.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ae.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                });
        verify(users, never()).save(any());
        verify(bodyMeasurements, never()).save(any());
    }

    @Test
    void register_defaultsEquipmentToEmptyArray_whenNull() {
        RegisterRequest req = new RegisterRequest(
                "bob@example.com", "Secret123", "Bob",
                Gender.male, new BigDecimal("180"), new BigDecimal("75"),
                null, null, null, null, null, null, null,
                null, // equipment null
                null, null, null);
        when(users.existsByEmailIgnoreCase(req.email())).thenReturn(false);
        when(passwordEncoder.encode("Secret123")).thenReturn("hashed");
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        service.register(req);

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getProfile().getEquipment()).isEmpty();
    }

    // --- login ---

    @Test
    void login_returnsTokens_andPersistsHashedRefreshToken() {
        AppUser user = persistedUser();
        when(users.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret123", "hashed")).thenReturn(true);
        when(jwtService.issueAccessToken(any(UserPrincipal.class))).thenReturn("access-token");
        when(jwtService.expiresInSeconds()).thenReturn(900L);

        AuthResponse response = service.login(new LoginRequest("alice@example.com", "Secret123"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.user().email()).isEqualTo("alice@example.com");

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(tokenCaptor.capture());
        RefreshToken saved = tokenCaptor.getValue();
        assertThat(saved.getTokenHash()).isEqualTo(Hashing.sha256(response.refreshToken()));
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plusSeconds(60));
        assertThat(saved.isRevoked()).isFalse();
    }

    @Test
    void login_throwsUnauthorized_whenUserMissing() {
        when(users.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "WrongPass1")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).status())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void login_throwsUnauthorized_whenPasswordMismatch() {
        AppUser user = persistedUser();
        when(users.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass1", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("alice@example.com", "WrongPass1")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(refreshTokens, never()).save(any());
    }

    // --- refresh ---

    @Test
    void refresh_revokesOldToken_andIssuesNewTokens() {
        String raw = "raw-refresh-token";
        AppUser user = persistedUser();
        RefreshToken existing = new RefreshToken();
        existing.setUser(user);
        existing.setTokenHash(Hashing.sha256(raw));
        existing.setExpiresAt(Instant.now().plusSeconds(3600));

        when(refreshTokens.findByTokenHash(Hashing.sha256(raw))).thenReturn(Optional.of(existing));
        when(jwtService.issueAccessToken(any(UserPrincipal.class))).thenReturn("new-access");
        when(jwtService.expiresInSeconds()).thenReturn(900L);

        AuthResponse response = service.refresh(new RefreshRequest(raw));

        assertThat(existing.isRevoked()).isTrue();
        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isNotBlank().isNotEqualTo(raw);
        verify(refreshTokens, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void refresh_throwsAndRevokesAll_whenTokenAlreadyRevoked() {
        String raw = "reused";
        AppUser user = persistedUser();
        RefreshToken existing = new RefreshToken();
        existing.setUser(user);
        existing.setTokenHash(Hashing.sha256(raw));
        existing.setExpiresAt(Instant.now().plusSeconds(3600));
        existing.revoke();

        when(refreshTokens.findByTokenHash(Hashing.sha256(raw))).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.refresh(new RefreshRequest(raw)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        verify(refreshTokens).revokeAllByUserId(eq(42L));
        verify(jwtService, never()).issueAccessToken(any());
    }

    @Test
    void refresh_throws_whenTokenExpired_andMarksRevoked() {
        String raw = "expired";
        AppUser user = persistedUser();
        RefreshToken existing = new RefreshToken();
        existing.setUser(user);
        existing.setTokenHash(Hashing.sha256(raw));
        existing.setExpiresAt(Instant.now().minusSeconds(1));

        when(refreshTokens.findByTokenHash(Hashing.sha256(raw))).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.refresh(new RefreshRequest(raw)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        assertThat(existing.isRevoked()).isTrue();
        verify(refreshTokens, never()).revokeAllByUserId(anyLong());
        verify(jwtService, never()).issueAccessToken(any());
    }

    @Test
    void refresh_throws_whenTokenUnknown() {
        when(refreshTokens.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh(new RefreshRequest("missing")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    // --- logout ---

    @Test
    void logout_revokesToken_whenPresent() {
        String raw = "raw";
        AppUser user = persistedUser();
        RefreshToken existing = new RefreshToken();
        existing.setUser(user);
        existing.setTokenHash(Hashing.sha256(raw));
        existing.setExpiresAt(Instant.now().plusSeconds(3600));
        when(refreshTokens.findByTokenHash(Hashing.sha256(raw))).thenReturn(Optional.of(existing));

        service.logout(raw);

        assertThat(existing.isRevoked()).isTrue();
        verify(refreshTokens).save(existing);
    }

    @Test
    void logout_isSilent_whenTokenUnknown() {
        when(refreshTokens.findByTokenHash(any())).thenReturn(Optional.empty());

        service.logout("nope");

        verify(refreshTokens, never()).save(any());
    }
}
