package com.myhealth.user;

import com.myhealth.auth.AuthMapper;
import com.myhealth.auth.AuthDtos.ProfileResponse;
import com.myhealth.auth.AuthDtos.UserResponse;
import com.myhealth.meal.FileStorageService;
import com.myhealth.meal.MealRepository;
import com.myhealth.user.UserDtos.ProfileUpdateRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class UserService {
    private final UserRepository users;
    private final BodyMeasurementRepository bodyMeasurements;
    private final MealRepository meals;
    private final FileStorageService fileStorage;

    public UserService(UserRepository users, BodyMeasurementRepository bodyMeasurements,
                       MealRepository meals, FileStorageService fileStorage) {
        this.users = users;
        this.bodyMeasurements = bodyMeasurements;
        this.meals = meals;
        this.fileStorage = fileStorage;
    }

    public UserResponse me(AppUser user) {
        return AuthMapper.toUserResponse(user);
    }

    @Transactional
    public ProfileResponse updateProfile(AppUser user, ProfileUpdateRequest request) {
        Profile profile = user.getProfile();
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
        profile.setAssistantAvatar(request.assistantAvatar());
        profile.setTheme(request.theme() == null ? "system" : request.theme());
        profile.setLanguage(request.language() == null ? "zh-TW" : request.language());
        profile.touch();

        BodyMeasurement measurement = new BodyMeasurement();
        measurement.setUser(user);
        measurement.setWeightKg(profile.getWeightKg());
        measurement.setBodyFatPct(profile.getBodyFatPct());
        measurement.setMuscleMassKg(profile.getMuscleMassKg());
        measurement.setBmrKcal(profile.getBmrKcal());
        measurement.setWaistCm(profile.getWaistCm());
        measurement.setBodyWaterPct(profile.getBodyWaterPct());
        measurement.setNote("profile_update");
        bodyMeasurements.save(measurement);

        users.save(user);
        return AuthMapper.toProfileResponse(profile);
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

    @Transactional
    public void deleteAccount(AppUser user) {
        List<String> imageUrls = meals.findImageUrlsByUserId(user.getId());
        users.delete(user);
        runAfterCommit(() -> imageUrls.forEach(fileStorage::delete));
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
