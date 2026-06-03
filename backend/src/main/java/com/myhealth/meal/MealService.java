package com.myhealth.meal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhealth.ai.AiProvider;
import com.myhealth.ai.AiProvider.FoodItem;
import com.myhealth.ai.AiProvider.MealImage;
import com.myhealth.common.ApiException;
import com.myhealth.common.ErrorCode;
import com.myhealth.meal.FileStorageService.StoredFile;
import com.myhealth.user.AppUser;
import com.myhealth.meal.MealDtos.MealResponse;
import com.myhealth.meal.MealDtos.UpdateMealRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MealService {
    private static final Logger log = LoggerFactory.getLogger(MealService.class);

    private final MealRepository meals;
    private final AiProvider aiProvider;
    private final FileStorageService fileStorage;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public MealService(MealRepository meals, AiProvider aiProvider, FileStorageService fileStorage, ObjectMapper objectMapper,
                       TransactionTemplate transactionTemplate) {
        this.meals = meals;
        this.aiProvider = aiProvider;
        this.fileStorage = fileStorage;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public MealResponse create(AppUser user, MultipartFile image, String description, String slot, LocalDate date) {
        String normalizedDescription = MealInputGuard.normalizeDescription(description);
        if (normalizedDescription == null && (image == null || image.isEmpty())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "image or description is required");
        }
        MealInputGuard.validateDescription(normalizedDescription);
        LocalDate mealDate = date == null ? LocalDate.now() : date;
        String imageUrl = fileStorage.storeMealImage(image, mealDate);
        MealImage mealImage;
        try {
            mealImage = toMealImage(image);
        } catch (RuntimeException ex) {
            fileStorage.delete(imageUrl);
            throw ex;
        }

        AiProvider.MealAnalysis analysis;
        try {
            analysis = aiProvider.analyzeMeal(normalizedDescription, mealImage);
        } catch (RuntimeException ex) {
            log.warn("AI meal analysis failed, saving empty analysis: {}", summarizeException(ex));
            analysis = AiProvider.MealAnalysis.empty();
        }

        AiProvider.MealAnalysis finalAnalysis = analysis;
        try {
            return transactionTemplate.execute(status -> persist(user, mealDate, slot, normalizedDescription, imageUrl, finalAnalysis));
        } catch (RuntimeException ex) {
            fileStorage.delete(imageUrl);
            throw ex;
        }
    }

    private MealImage toMealImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return null;
        }
        try {
            return new MealImage(image.getContentType(), image.getBytes());
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Unable to read uploaded image for AI analysis", ex);
        }
    }

    private MealResponse persist(AppUser user, LocalDate mealDate, String slot, String description,
                                 String imageUrl, AiProvider.MealAnalysis analysis) {
        Meal meal = new Meal();
        meal.setUser(user);
        meal.setDate(mealDate);
        meal.setSlot(slot);
        meal.setDescription(description);
        meal.setImageUrl(imageUrl);
        applyItems(meal, analysis.items());
        meal.setAiSuggestion(analysis.suggestion());
        return toResponse(meals.save(meal));
    }

    public List<MealResponse> list(AppUser user, LocalDate date) {
        return meals.findByUserIdAndDateOrderByCreatedAtDesc(user.getId(), date).stream()
                .map(this::toResponse)
                .toList();
    }

    public MealResponse get(AppUser user, Long id) {
        return toResponse(findOwned(user, id));
    }

    public StoredFile loadImage(AppUser user, Long id) {
        Meal meal = findOwned(user, id);
        if (meal.getImageUrl() == null || meal.getImageUrl().isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Meal image not found");
        }
        return fileStorage.load(meal.getImageUrl());
    }

    @Transactional
    public MealResponse update(AppUser user, Long id, UpdateMealRequest request) {
        Meal meal = findOwned(user, id);
        applyItems(meal, request.items());
        meal.setAiSuggestion(request.aiSuggestion());
        return toResponse(meals.save(meal));
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        Meal meal = findOwned(user, id);
        String imageUrl = meal.getImageUrl();
        meals.delete(meal);
        if (imageUrl != null) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fileStorage.delete(imageUrl);
                }
            });
        }
    }

    private Meal findOwned(AppUser user, Long id) {
        return meals.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Meal not found"));
    }

    private void applyItems(Meal meal, List<FoodItem> items) {
        List<FoodItem> safeItems = items == null ? List.of() : items;
        meal.setItemsJson(writeItems(safeItems));
        meal.setTotalKcal(safeItems.stream().mapToInt(FoodItem::kcal).sum());
        meal.setTotalProtein(sum(safeItems.stream().mapToDouble(FoodItem::protein).sum()));
        meal.setTotalFat(sum(safeItems.stream().mapToDouble(FoodItem::fat).sum()));
        meal.setTotalCarb(sum(safeItems.stream().mapToDouble(FoodItem::carb).sum()));
    }

    public MealResponse toResponse(Meal meal) {
        return new MealResponse(
                meal.getId(),
                meal.getDate(),
                meal.getSlot(),
                meal.getDescription(),
                meal.getImageUrl() == null ? null : "/api/v1/meals/%d/image".formatted(meal.getId()),
                readItems(meal.getItemsJson()),
                meal.getTotalKcal(),
                meal.getTotalProtein(),
                meal.getTotalFat(),
                meal.getTotalCarb(),
                meal.getAiSuggestion(),
                meal.getCreatedAt());
    }

    private BigDecimal sum(double value) {
        return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String writeItems(List<FoodItem> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize meal items", ex);
        }
    }

    private List<FoodItem> readItems(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to deserialize meal items", ex);
        }
    }

    private String summarizeException(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return "%s: %s".formatted(ex.getClass().getSimpleName(), message.lines().findFirst().orElse(""));
    }

}
