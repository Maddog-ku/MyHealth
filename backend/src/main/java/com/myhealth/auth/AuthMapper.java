package com.myhealth.auth;

import com.myhealth.auth.AuthDtos.ProfileResponse;
import com.myhealth.auth.AuthDtos.UserResponse;
import com.myhealth.auth.AuthDtos.UserSummary;
import com.myhealth.user.AppUser;
import com.myhealth.user.Profile;
import java.util.Arrays;
import java.util.List;

public final class AuthMapper {
    private AuthMapper() {
    }

    public static UserResponse toUserResponse(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                toProfileResponse(user.getProfile()),
                user.getCreatedAt());
    }

    public static UserSummary toSummary(AppUser user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }

    public static ProfileResponse toProfileResponse(Profile profile) {
        List<String> equipment = profile.getEquipment() == null ? List.of() : Arrays.asList(profile.getEquipment());
        return new ProfileResponse(
                profile.getGender(),
                profile.getHeightCm(),
                profile.getWeightKg(),
                profile.getAge(),
                profile.getBodyFatPct(),
                profile.getMuscleMassKg(),
                profile.getBmrKcal(),
                profile.getWaistCm(),
                profile.getBodyWaterPct(),
                profile.getGoal(),
                equipment,
                profile.getExperience(),
                profile.getTheme(),
                profile.getLanguage());
    }
}
