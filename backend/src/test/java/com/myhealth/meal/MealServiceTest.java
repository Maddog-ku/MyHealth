package com.myhealth.meal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhealth.ai.AiProvider;
import com.myhealth.ai.AiProvider.FoodItem;
import com.myhealth.ai.AiProvider.MealAnalysis;
import com.myhealth.common.ApiException;
import com.myhealth.common.ErrorCode;
import com.myhealth.meal.MealDtos.MealResponse;
import com.myhealth.user.AppUser;
import com.myhealth.user.Role;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class MealServiceTest {

    @Mock MealRepository meals;
    @Mock AiProvider aiProvider;
    @Mock FileStorageService fileStorage;

    final ObjectMapper objectMapper = new ObjectMapper();
    TransactionTemplate transactionTemplate;
    MealService service;
    AppUser owner;

    @BeforeEach
    void setUp() {
        transactionTemplate = mock(TransactionTemplate.class);
        // Run the callback inline (no real transaction). lenient() because not every
        // test exercises the create() path that calls transactionTemplate.execute.
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
        service = new MealService(meals, aiProvider, fileStorage, objectMapper, transactionTemplate);
        owner = userWithId(1L);
    }

    private AppUser userWithId(long id) {
        AppUser u = new AppUser();
        u.setEmail("u@example.com");
        u.setPasswordHash("h");
        u.setRole(Role.USER);
        try {
            Field f = AppUser.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        return u;
    }

    private Meal mealWithId(long id, AppUser user) {
        Meal m = new Meal();
        m.setUser(user);
        m.setDate(LocalDate.now());
        m.setSlot("lunch");
        m.setItemsJson("[]");
        try {
            Field f = Meal.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(m, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        return m;
    }

    @Test
    void create_throwsBadRequest_whenDescriptionAndImageBothMissing() {
        assertThatThrownBy(() -> service.create(owner, null, "  ", "lunch", LocalDate.now()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ae = (ApiException) e;
                    assertThat(ae.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ae.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                });

        verify(aiProvider, never()).analyzeMeal(any(), eq(false));
        verify(meals, never()).save(any());
    }

    @Test
    void create_persistsMeal_andSumsTotals_whenDescriptionOnly() {
        when(fileStorage.storeMealImage(null, LocalDate.of(2026, 5, 30))).thenReturn(null);
        when(aiProvider.analyzeMeal("雞胸肉沙拉", false)).thenReturn(new MealAnalysis(
                List.of(new FoodItem("雞胸肉", 150, 248, 46.5, 5.4, 0.0, 0.92),
                        new FoodItem("生菜", 80, 20, 1.0, 0.2, 4.0, 0.9)),
                "蛋白足夠，可加碳水"));
        when(meals.save(any(Meal.class))).thenAnswer(inv -> inv.getArgument(0));

        MealResponse response = service.create(owner, null, "雞胸肉沙拉", "lunch", LocalDate.of(2026, 5, 30));

        ArgumentCaptor<Meal> captor = ArgumentCaptor.forClass(Meal.class);
        verify(meals).save(captor.capture());
        Meal saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(owner);
        assertThat(saved.getSlot()).isEqualTo("lunch");
        assertThat(saved.getDescription()).isEqualTo("雞胸肉沙拉");
        assertThat(saved.getImageUrl()).isNull();
        assertThat(saved.getTotalKcal()).isEqualTo(268);
        assertThat(saved.getTotalProtein()).isEqualByComparingTo("47.50");
        assertThat(saved.getTotalCarb()).isEqualByComparingTo("4.00");
        assertThat(saved.getAiSuggestion()).isEqualTo("蛋白足夠，可加碳水");

        assertThat(response.totalKcal()).isEqualTo(268);
        assertThat(response.imageUrl()).isNull();
        assertThat(response.items()).hasSize(2);
    }

    @Test
    void create_storesImage_andExposesBackendImageUrl_whenImageOnly() {
        MultipartFile image = new MockMultipartFile("image", "x.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(fileStorage.storeMealImage(eq(image), any())).thenReturn("2026/05/30/uuid.jpg");
        when(aiProvider.analyzeMeal(null, true)).thenReturn(new MealAnalysis(
                List.of(new FoodItem("餐點", 250, 360, 28.0, 14.0, 42.0, 0.72)),
                "估算"));
        when(meals.save(any(Meal.class))).thenAnswer(inv -> {
            Meal m = inv.getArgument(0);
            try {
                Field f = Meal.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(m, 88L);
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException(ex);
            }
            return m;
        });

        MealResponse response = service.create(owner, image, null, "dinner", null);

        assertThat(response.imageUrl()).isEqualTo("/api/v1/meals/88/image");
        assertThat(response.totalKcal()).isEqualTo(360);
    }

    @Test
    void create_fallsBack_andStillPersists_whenAiThrows() {
        when(fileStorage.storeMealImage(null, LocalDate.now())).thenReturn(null);
        when(aiProvider.analyzeMeal(any(), eq(false))).thenThrow(new RuntimeException("ollama down"));
        when(meals.save(any(Meal.class))).thenAnswer(inv -> inv.getArgument(0));

        MealResponse response = service.create(owner, null, "焗烤起司飯", "lunch", null);

        ArgumentCaptor<Meal> captor = ArgumentCaptor.forClass(Meal.class);
        verify(meals).save(captor.capture());
        Meal saved = captor.getValue();
        assertThat(saved.getTotalKcal()).isZero();
        assertThat(saved.getAiSuggestion()).contains("AI 暫不可用");
        assertThat(response.items()).isEmpty();
    }

    @Test
    void create_deletesStoredImage_whenPersistFails() {
        MultipartFile image = new MockMultipartFile("image", "x.jpg", "image/jpeg", new byte[]{1});
        when(fileStorage.storeMealImage(eq(image), any())).thenReturn("2026/05/30/x.jpg");
        when(aiProvider.analyzeMeal(any(), eq(true))).thenReturn(new MealAnalysis(List.of(), null));
        when(meals.save(any(Meal.class))).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.create(owner, image, null, "lunch", LocalDate.now()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");

        verify(fileStorage).delete("2026/05/30/x.jpg");
    }

    @Test
    void list_filtersByUserAndDate() {
        when(meals.findByUserIdAndDateOrderByCreatedAtDesc(1L, LocalDate.of(2026, 5, 30))).thenReturn(List.of());
        assertThat(service.list(owner, LocalDate.of(2026, 5, 30))).isEmpty();
    }

    @Test
    void get_throws404_whenNotOwned() {
        when(meals.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(owner, 99L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).status())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void delete_removesOwnedMeal() {
        Meal meal = mealWithId(7L, owner);
        when(meals.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(meal));

        service.delete(owner, 7L);

        verify(meals).delete(meal);
    }

    @Test
    void loadImage_throws_whenMealHasNoImage() {
        Meal meal = mealWithId(7L, owner);
        meal.setImageUrl(null);
        when(meals.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(meal));

        assertThatThrownBy(() -> service.loadImage(owner, 7L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}
