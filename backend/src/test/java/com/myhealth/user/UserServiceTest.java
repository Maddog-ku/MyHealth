package com.myhealth.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.myhealth.auth.AuthDtos.ProfileResponse;
import com.myhealth.auth.AuthDtos.UserResponse;
import com.myhealth.user.UserDtos.ProfileUpdateRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository users;
    @Mock BodyMeasurementRepository bodyMeasurements;

    UserService service;
    AppUser user;
    Profile profile;

    @BeforeEach
    void setUp() {
        service = new UserService(users, bodyMeasurements);
        user = new AppUser();
        user.setEmail("alice@example.com");
        user.setName("Alice");
        user.setRole(Role.USER);
        profile = new Profile();
        profile.setGender(Gender.female);
        profile.setHeightCm(new BigDecimal("165"));
        profile.setWeightKg(new BigDecimal("55"));
        user.setProfile(profile);
    }

    @SuppressWarnings("unchecked")
    private static <T> T read(Object target, String name, Class<T> type) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(target);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    void me_returnsCurrentUserResponse() {
        UserResponse response = service.me(user);
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.profile().heightCm()).isEqualByComparingTo("165");
    }

    @Test
    void updateProfile_writesProfileFields_persistsBodyMeasurement_andSavesUser() {
        ProfileUpdateRequest request = new ProfileUpdateRequest(
                Gender.male,
                new BigDecimal("180"),
                new BigDecimal("78.5"),
                32,
                new BigDecimal("18.0"),
                new BigDecimal("34.0"),
                1700,
                new BigDecimal("82"),
                new BigDecimal("55"),
                Goal.muscle_gain,
                List.of("barbell", "rack"),
                Experience.intermediate,
                "dark",
                "zh-TW");

        ProfileResponse response = service.updateProfile(user, request);

        assertThat(profile.getHeightCm()).isEqualByComparingTo("180");
        assertThat(profile.getWeightKg()).isEqualByComparingTo("78.5");
        assertThat(profile.getGoal()).isEqualTo(Goal.muscle_gain);
        assertThat(profile.getEquipment()).containsExactly("barbell", "rack");
        assertThat(profile.getTheme()).isEqualTo("dark");

        ArgumentCaptor<BodyMeasurement> captor = ArgumentCaptor.forClass(BodyMeasurement.class);
        verify(bodyMeasurements).save(captor.capture());
        BodyMeasurement saved = captor.getValue();
        assertThat(read(saved, "weightKg", BigDecimal.class)).isEqualByComparingTo("78.5");
        assertThat(read(saved, "note", String.class)).isEqualTo("profile_update");
        assertThat(read(saved, "user", AppUser.class)).isSameAs(user);

        verify(users).save(user);
        assertThat(response.experience()).isEqualTo(Experience.intermediate);
    }

    @Test
    void updateProfile_defaultsEquipmentEmpty_andTheme_andLanguage_whenNull() {
        ProfileUpdateRequest request = new ProfileUpdateRequest(
                Gender.female, new BigDecimal("160"), new BigDecimal("50"),
                null, null, null, null, null, null, null,
                null,  // equipment
                null,
                null,  // theme
                null); // language

        service.updateProfile(user, request);

        assertThat(profile.getEquipment()).isEmpty();
        assertThat(profile.getTheme()).isEqualTo("system");
        assertThat(profile.getLanguage()).isEqualTo("zh-TW");
    }

    @Test
    void deleteAccount_delegatesToRepository() {
        service.deleteAccount(user);
        verify(users).delete(user);
    }

    @Test
    void updateProfile_touchesUpdatedAt() {
        var before = profile.getUpdatedAt();
        sleepMillis(5);
        service.updateProfile(user, new ProfileUpdateRequest(
                Gender.male, new BigDecimal("180"), new BigDecimal("70"),
                null, null, null, null, null, null, null, null, null, null, null));
        assertThat(profile.getUpdatedAt()).isAfter(before);
    }

    private static void sleepMillis(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
