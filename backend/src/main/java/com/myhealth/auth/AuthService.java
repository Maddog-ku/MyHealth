package com.myhealth.auth;

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
import com.myhealth.user.Profile;
import com.myhealth.user.RefreshToken;
import com.myhealth.user.RefreshTokenRepository;
import com.myhealth.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final BodyMeasurementRepository bodyMeasurements;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties properties;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       BodyMeasurementRepository bodyMeasurements, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AppProperties properties) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.bodyMeasurements = bodyMeasurements;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Transactional
    public AuthDtos.UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "Email already registered");
        }
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setName(request.name().trim());

        Profile profile = new Profile();
        applyProfile(profile, request);
        user.setProfile(profile);
        AppUser saved = users.save(user);

        BodyMeasurement measurement = new BodyMeasurement();
        measurement.setUser(saved);
        measurement.setWeightKg(profile.getWeightKg());
        measurement.setBodyFatPct(profile.getBodyFatPct());
        measurement.setMuscleMassKg(profile.getMuscleMassKg());
        measurement.setBmrKcal(profile.getBmrKcal());
        measurement.setWaistCm(profile.getWaistCm());
        measurement.setBodyWaterPct(profile.getBodyWaterPct());
        measurement.setNote("registration");
        bodyMeasurements.save(measurement);

        return AuthMapper.toUserResponse(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        return login(request, null);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String deviceInfo) {
        AppUser user = users.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Invalid email or password");
        }
        return issueTokens(user, deviceInfo, Instant.now());
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        return refresh(request, null);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request, String deviceInfo) {
        RefreshToken token = refreshTokens.findByTokenHash(Hashing.sha256(request.refreshToken()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_REFRESH_TOKEN, "Invalid refresh token"));

        if (token.isRevoked()) {
            refreshTokens.revokeAllByUserId(token.getUser().getId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token reuse detected");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            token.revoke();
            refreshTokens.save(token);
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_REFRESH_TOKEN, "Invalid refresh token");
        }

        token.revoke();
        refreshTokens.save(token);
        // Carry the session forward across rotation: keep the original login time and device
        // so the session list stays stable, while the new row records its own issue time.
        String device = deviceInfo != null ? deviceInfo : token.getDeviceInfo();
        return issueTokens(token.getUser(), device, token.getCreatedAt());
    }

    /**
     * Change the signed-in user's password after verifying the current one, then log out
     * every other device for safety. The caller's own session is preserved when its refresh
     * token is supplied (so the user stays logged in here); otherwise all sessions are revoked.
     */
    @Transactional
    public void changePassword(AppUser user, String currentPassword, String newPassword, String currentRawRefreshToken) {
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "目前密碼不正確");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "新密碼不可與目前密碼相同");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(user);

        Long keepId = currentRawRefreshToken == null || currentRawRefreshToken.isBlank()
                ? null
                : refreshTokens.findByTokenHash(Hashing.sha256(currentRawRefreshToken))
                        .filter(t -> t.getUser().getId().equals(user.getId()) && !t.isRevoked())
                        .map(RefreshToken::getId)
                        .orElse(null);
        if (keepId == null) {
            refreshTokens.revokeAllByUserId(user.getId());
        } else {
            refreshTokens.revokeAllExcept(user.getId(), keepId);
        }
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(Hashing.sha256(refreshToken)).ifPresent(token -> {
            token.revoke();
            refreshTokens.save(token);
        });
    }

    /** Max length we persist for the device label (User-Agent); the column is VARCHAR(255). */
    private static final int MAX_DEVICE_INFO = 255;

    private AuthResponse issueTokens(AppUser user, String deviceInfo, Instant createdAt) {
        UserPrincipal principal = new UserPrincipal(user);
        String accessToken = jwtService.issueAccessToken(principal);
        String rawRefreshToken = UUID.randomUUID().toString();

        Instant now = Instant.now();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(Hashing.sha256(rawRefreshToken));
        refreshToken.setDeviceInfo(truncate(deviceInfo));
        refreshToken.setCreatedAt(createdAt == null ? now : createdAt);
        refreshToken.setLastUsedAt(now);
        refreshToken.setExpiresAt(now.plus(properties.jwt().refreshTtl()));
        refreshTokens.save(refreshToken);

        return new AuthResponse(accessToken, rawRefreshToken, "Bearer", jwtService.expiresInSeconds(), AuthMapper.toSummary(user));
    }

    private String truncate(String deviceInfo) {
        if (deviceInfo == null || deviceInfo.isBlank()) {
            return null;
        }
        String trimmed = deviceInfo.strip();
        return trimmed.length() > MAX_DEVICE_INFO ? trimmed.substring(0, MAX_DEVICE_INFO) : trimmed;
    }

    private void applyProfile(Profile profile, RegisterRequest request) {
        profile.setGender(request.gender());
        profile.setHeightCm(request.heightCm());
        profile.setWeightKg(request.weightKg());
        profile.setAge(request.age());
        profile.setBodyFatPct(request.bodyFatPct());
        profile.setMuscleMassKg(request.muscleMassKg());
        profile.setBmrKcal(request.bmrKcal());
        profile.setWaistCm(request.waistCm());
        profile.setBodyWaterPct(request.bodyWaterPct());
        profile.setGoal(request.goal());
        profile.setEquipment(normalizeEquipment(request.equipment()));
        profile.setExperience(request.experience());
        profile.setTheme(request.theme() == null ? "system" : request.theme());
        profile.setLanguage(request.language() == null ? "zh-TW" : request.language());
    }

    private String[] normalizeEquipment(List<String> equipment) {
        if (equipment == null) {
            return new String[0];
        }
        return equipment.stream()
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .distinct()
                .toArray(String[]::new);
    }
}
