package com.myhealth.meal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhealth.ai.AiProvider;
import com.myhealth.common.JsonColumns;
import com.myhealth.ai.AiProvider.FoodItem;
import com.myhealth.ai.AiProvider.MealImage;
import com.myhealth.common.ApiException;
import com.myhealth.common.ErrorCode;
import com.myhealth.food.FoodService;
import com.myhealth.meal.MealDtos.CopyMealRequest;
import com.myhealth.meal.MealDtos.FavoriteMealRequest;
import com.myhealth.meal.MealDtos.FavoriteMealResponse;
import com.myhealth.meal.FileStorageService.StoredFile;
import com.myhealth.user.AppUser;
import com.myhealth.meal.MealDtos.MealResponse;
import com.myhealth.meal.MealDtos.MealPreviewResponse;
import com.myhealth.meal.MealDtos.RecentMealResponse;
import com.myhealth.meal.MealDtos.UpdateMealRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
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
    private final FavoriteMealRepository favoriteMeals;
    private final AiProvider aiProvider;
    private final FoodService foodService;
    private final FileStorageService fileStorage;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Validator validator;

    public MealService(MealRepository meals, FavoriteMealRepository favoriteMeals,
                       AiProvider aiProvider, FoodService foodService, FileStorageService fileStorage,
                       ObjectMapper objectMapper, TransactionTemplate transactionTemplate, Validator validator) {
        this.meals = meals;
        this.favoriteMeals = favoriteMeals;
        this.aiProvider = aiProvider;
        this.foodService = foodService;
        this.fileStorage = fileStorage;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.validator = validator;
    }

    public MealResponse create(AppUser user, MultipartFile image, String description, String slot, LocalDate date) {
        return create(user, image, description, slot, date, true);
    }

    /**
     * @param requireFoodHint enforce the food-keyword whitelist on the description.
     *                        The chat assistant passes false (intent already classified
     *                        by the model); the public REST endpoint passes true.
     */
    public MealResponse create(AppUser user, MultipartFile image, String description, String slot, LocalDate date,
                               boolean requireFoodHint) {
        String normalizedDescription = validateMealInput(image, description, requireFoodHint);
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

    public MealPreviewResponse preview(AppUser user, MultipartFile image, String description, String slot, LocalDate date) {
        String normalizedDescription = validateMealInput(image, description, true);
        LocalDate mealDate = date == null ? LocalDate.now() : date;
        MealImage mealImage = toMealImage(image);
        AiProvider.MealAnalysis analysis;
        try {
            analysis = aiProvider.analyzeMeal(normalizedDescription, mealImage);
        } catch (RuntimeException ex) {
            log.warn("AI meal preview failed, returning empty analysis: {}", summarizeException(ex));
            analysis = AiProvider.MealAnalysis.empty();
        }
        return toPreview(mealDate, slot, normalizedDescription, groundAgainstCatalog(analysis));
    }

    /**
     * Re-baseline each AI-identified food's nutrition against the food database (AI keeps the
     * identification + grams; the catalog supplies per-100g nutrition). Applied in the preview
     * so the confirm list shows database-grounded numbers the user can still adjust.
     */
    private AiProvider.MealAnalysis groundAgainstCatalog(AiProvider.MealAnalysis analysis) {
        if (analysis == null || analysis.items() == null || analysis.items().isEmpty()) {
            return analysis;
        }
        List<FoodItem> grounded = analysis.items().stream().map(foodService::ground).toList();
        return new AiProvider.MealAnalysis(grounded, analysis.suggestion());
    }

    public MealResponse confirm(AppUser user, MultipartFile image, String description, String slot, LocalDate date,
                                String itemsJson, String aiSuggestion) {
        String normalizedDescription = validateMealInput(image, description, true);
        LocalDate mealDate = date == null ? LocalDate.now() : date;
        List<FoodItem> items = readConfirmedItems(itemsJson);
        validateConfirmedItems(items);
        if (items.size() > 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "items must contain 5 entries or fewer");
        }
        String imageUrl = fileStorage.storeMealImage(image, mealDate);
        AiProvider.MealAnalysis analysis = new AiProvider.MealAnalysis(items, aiSuggestion);
        try {
            return transactionTemplate.execute(status -> persist(user, mealDate, slot, normalizedDescription, imageUrl, analysis));
        } catch (RuntimeException ex) {
            fileStorage.delete(imageUrl);
            throw ex;
        }
    }

    private String validateMealInput(MultipartFile image, String description, boolean requireFoodHint) {
        String normalizedDescription = MealInputGuard.normalizeDescription(description);
        if (normalizedDescription == null && (image == null || image.isEmpty())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "image or description is required");
        }
        MealInputGuard.validateDescription(normalizedDescription, requireFoodHint);
        return normalizedDescription;
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

    private MealPreviewResponse toPreview(LocalDate date, String slot, String description, AiProvider.MealAnalysis analysis) {
        List<FoodItem> items = analysis.items() == null ? List.of() : analysis.items();
        return new MealPreviewResponse(
                date,
                slot,
                description,
                items,
                items.stream().mapToInt(FoodItem::kcal).sum(),
                sum(items.stream().mapToDouble(FoodItem::protein).sum()),
                sum(items.stream().mapToDouble(FoodItem::fat).sum()),
                sum(items.stream().mapToDouble(FoodItem::carb).sum()),
                analysis.suggestion());
    }

    public List<MealResponse> list(AppUser user, LocalDate date) {
        return meals.findByUserIdAndDateOrderByCreatedAtDesc(user.getId(), date).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<RecentMealResponse> recent(AppUser user, LocalDate beforeDate, Integer limit) {
        int size = limit == null ? 5 : Math.max(1, Math.min(10, limit));
        LocalDate cutoff = beforeDate == null ? LocalDate.now() : beforeDate;
        return meals.findRecentBeforeDate(user.getId(), cutoff, PageRequest.of(0, size)).stream()
                .map(this::toRecentResponse)
                .toList();
    }

    public List<FavoriteMealResponse> favorites(AppUser user) {
        return favoriteMeals.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toFavoriteResponse)
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
    public FavoriteMealResponse favorite(AppUser user, Long mealId, FavoriteMealRequest request) {
        Meal meal = findOwned(user, mealId);
        FavoriteMeal favorite = new FavoriteMeal();
        favorite.setUser(user);
        favorite.setSourceMealId(meal.getId());
        favorite.setName(normalizeFavoriteName(request == null ? null : request.name(), meal));
        favorite.setSlot(meal.getSlot());
        favorite.setDescription(meal.getDescription());
        favorite.setItemsJson(meal.getItemsJson());
        favorite.setTotalKcal(meal.getTotalKcal());
        favorite.setTotalProtein(meal.getTotalProtein());
        favorite.setTotalFat(meal.getTotalFat());
        favorite.setTotalCarb(meal.getTotalCarb());
        favorite.setAiSuggestion(meal.getAiSuggestion());
        return toFavoriteResponse(favoriteMeals.save(favorite));
    }

    @Transactional
    public MealResponse copyMeal(AppUser user, Long mealId, CopyMealRequest request) {
        Meal source = findOwned(user, mealId);
        Meal copied = new Meal();
        copied.setUser(user);
        copied.setDate(copyDate(request));
        copied.setSlot(copySlot(request, source.getSlot()));
        copied.setDescription(source.getDescription());
        copied.setImageUrl(null);
        copied.setItemsJson(source.getItemsJson());
        copied.setTotalKcal(source.getTotalKcal());
        copied.setTotalProtein(source.getTotalProtein());
        copied.setTotalFat(source.getTotalFat());
        copied.setTotalCarb(source.getTotalCarb());
        copied.setAiSuggestion(source.getAiSuggestion());
        return toResponse(meals.save(copied));
    }

    @Transactional
    public MealResponse copyFavorite(AppUser user, Long favoriteId, CopyMealRequest request) {
        FavoriteMeal source = findOwnedFavorite(user, favoriteId);
        Meal copied = new Meal();
        copied.setUser(user);
        copied.setDate(copyDate(request));
        copied.setSlot(copySlot(request, source.getSlot()));
        copied.setDescription(source.getDescription());
        copied.setImageUrl(null);
        copied.setItemsJson(source.getItemsJson());
        copied.setTotalKcal(source.getTotalKcal());
        copied.setTotalProtein(source.getTotalProtein());
        copied.setTotalFat(source.getTotalFat());
        copied.setTotalCarb(source.getTotalCarb());
        copied.setAiSuggestion(source.getAiSuggestion());
        return toResponse(meals.save(copied));
    }

    @Transactional
    public MealResponse update(AppUser user, Long id, UpdateMealRequest request) {
        Meal meal = findOwned(user, id);
        applyItems(meal, request.items());
        meal.setAiSuggestion(request.aiSuggestion());
        return toResponse(meals.save(meal));
    }

    @Transactional
    public void deleteFavorite(AppUser user, Long id) {
        FavoriteMeal favorite = findOwnedFavorite(user, id);
        favoriteMeals.delete(favorite);
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

    private FavoriteMeal findOwnedFavorite(AppUser user, Long id) {
        return favoriteMeals.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Favorite meal not found"));
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

    private RecentMealResponse toRecentResponse(Meal meal) {
        return new RecentMealResponse(
                meal.getId(),
                meal.getDate(),
                displayName(meal),
                meal.getSlot(),
                meal.getDescription(),
                readItems(meal.getItemsJson()),
                meal.getTotalKcal(),
                meal.getTotalProtein(),
                meal.getTotalFat(),
                meal.getTotalCarb(),
                meal.getCreatedAt());
    }

    private FavoriteMealResponse toFavoriteResponse(FavoriteMeal favorite) {
        return new FavoriteMealResponse(
                favorite.getId(),
                favorite.getName(),
                favorite.getSlot(),
                favorite.getDescription(),
                readItems(favorite.getItemsJson()),
                favorite.getTotalKcal(),
                favorite.getTotalProtein(),
                favorite.getTotalFat(),
                favorite.getTotalCarb(),
                favorite.getAiSuggestion(),
                favorite.getCreatedAt());
    }

    private LocalDate copyDate(CopyMealRequest request) {
        return request == null || request.date() == null ? LocalDate.now() : request.date();
    }

    private String copySlot(CopyMealRequest request, String fallback) {
        return request == null || request.slot() == null ? fallback : request.slot().name();
    }

    private String normalizeFavoriteName(String name, Meal meal) {
        String normalized = name == null ? null : name.strip().replaceAll("\\s+", " ");
        if (normalized != null && !normalized.isBlank()) {
            return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
        }
        return displayName(meal);
    }

    private String displayName(Meal meal) {
        List<FoodItem> items = readItems(meal.getItemsJson());
        if (!items.isEmpty()) {
            return items.stream()
                    .limit(2)
                    .map(FoodItem::name)
                    .reduce((left, right) -> left + " + " + right)
                    .orElse("常用餐點");
        }
        String description = MealInputGuard.normalizeDescription(meal.getDescription());
        if (description != null) {
            return description.length() > 24 ? description.substring(0, 24) : description;
        }
        return switch (meal.getSlot()) {
            case "breakfast" -> "早餐紀錄";
            case "dinner" -> "晚餐紀錄";
            case "snack" -> "點心紀錄";
            default -> "午餐紀錄";
        };
    }

    private BigDecimal sum(double value) {
        return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String writeItems(List<FoodItem> items) {
        return JsonColumns.write(objectMapper, items);
    }

    private List<FoodItem> readItems(String json) {
        return JsonColumns.read(objectMapper, json, new TypeReference<List<FoodItem>>() {
        });
    }

    private List<FoodItem> readConfirmedItems(String json) {
        try {
            return readItems(json);
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "items must be valid food item JSON");
        }
    }

    private void validateConfirmedItems(List<FoodItem> items) {
        if (items == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "items must be an array");
        }
        for (FoodItem item : items) {
            if (item == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "items contain invalid food values");
            }
            Set<ConstraintViolation<FoodItem>> violations = validator.validate(item);
            if (!violations.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "items contain invalid food values");
            }
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
