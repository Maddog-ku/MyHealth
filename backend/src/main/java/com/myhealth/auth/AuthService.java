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
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "Email already registered");
        }
        AppUser user = new AppUser();
        user.setEmail(request.email().trim().toLowerCase());
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
        AppUser user = users.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Invalid email or password");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
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
        return issueTokens(token.getUser());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(Hashing.sha256(refreshToken)).ifPresent(token -> {
            token.revoke();
            refreshTokens.save(token);
        });
    }

    private AuthResponse issueTokens(AppUser user) {
        UserPrincipal principal = new UserPrincipal(user);
        String accessToken = jwtService.issueAccessToken(principal);
        String rawRefreshToken = UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(Hashing.sha256(rawRefreshToken));
        refreshToken.setExpiresAt(Instant.now().plus(properties.jwt().refreshTtl()));
        refreshTokens.save(refreshToken);

        return new AuthResponse(accessToken, rawRefreshToken, "Bearer", jwtService.expiresInSeconds(), AuthMapper.toSummary(user));
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
        profile.setEquipment(request.equipment() == null ? new String[0] : request.equipment().toArray(String[]::new));
        profile.setExperience(request.experience());
        profile.setTheme(request.theme() == null ? "system" : request.theme());
        profile.setLanguage(request.language() == null ? "zh-TW" : request.language());
    }
}
