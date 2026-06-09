package com.myhealth.meal;

import com.myhealth.ai.AiEndpointRateLimiter;
import com.myhealth.auth.CurrentUser;
import com.myhealth.common.PageEnvelope;
import com.myhealth.meal.FileStorageService.StoredFile;
import com.myhealth.meal.MealDtos.CopyMealRequest;
import com.myhealth.meal.MealDtos.FavoriteMealRequest;
import com.myhealth.meal.MealDtos.FavoriteMealResponse;
import com.myhealth.meal.MealDtos.MealPreviewResponse;
import com.myhealth.meal.MealDtos.MealResponse;
import com.myhealth.meal.MealDtos.RecentMealResponse;
import com.myhealth.user.AppUser;
import com.myhealth.meal.MealDtos.UpdateMealRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/meals")
public class MealController {
    private final CurrentUser currentUser;
    private final MealService mealService;
    private final AiEndpointRateLimiter rateLimiter;

    public MealController(CurrentUser currentUser, MealService mealService, AiEndpointRateLimiter rateLimiter) {
        this.currentUser = currentUser;
        this.mealService = mealService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<MealResponse> create(
            @RequestPart(required = false) MultipartFile image,
            @RequestParam(required = false) String description,
            @RequestParam MealSlot slot,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        AppUser user = currentUser.require();
        rateLimiter.checkMealCreate(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mealService.create(user, image, description, slot.name(), date));
    }

    @PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    MealPreviewResponse preview(
            @RequestPart(required = false) MultipartFile image,
            @RequestParam(required = false) String description,
            @RequestParam MealSlot slot,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        AppUser user = currentUser.require();
        rateLimiter.checkMealCreate(user);
        return mealService.preview(user, image, description, slot.name(), date);
    }

    @PostMapping(path = "/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<MealResponse> confirm(
            @RequestPart(required = false) MultipartFile image,
            @RequestParam(required = false) String description,
            @RequestParam MealSlot slot,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String items,
            @RequestParam(required = false) String aiSuggestion) {
        AppUser user = currentUser.require();
        rateLimiter.checkMealCreate(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mealService.confirm(user, image, description, slot.name(), date, items, aiSuggestion));
    }

    @GetMapping
    PageEnvelope<MealResponse> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return PageEnvelope.unpaged(mealService.list(currentUser.require(), date));
    }

    @GetMapping("/recent")
    PageEnvelope<RecentMealResponse> recent(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate beforeDate,
            @RequestParam(required = false) Integer limit) {
        return PageEnvelope.unpaged(mealService.recent(currentUser.require(), beforeDate, limit));
    }

    @GetMapping("/favorites")
    List<FavoriteMealResponse> favorites() {
        return mealService.favorites(currentUser.require());
    }

    @GetMapping("/{id}")
    MealResponse get(@PathVariable Long id) {
        return mealService.get(currentUser.require(), id);
    }

    @GetMapping("/{id}/image")
    ResponseEntity<Resource> image(@PathVariable Long id) {
        StoredFile image = mealService.loadImage(currentUser.require(), id);
        MediaType mediaType = image.contentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(image.contentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(image.resource());
    }

    @PutMapping("/{id}")
    MealResponse update(@PathVariable Long id, @Valid @RequestBody UpdateMealRequest request) {
        return mealService.update(currentUser.require(), id, request);
    }

    @PostMapping("/{id}/favorite")
    ResponseEntity<FavoriteMealResponse> favorite(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) FavoriteMealRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mealService.favorite(currentUser.require(), id, request));
    }

    @PostMapping("/{id}/copy")
    ResponseEntity<MealResponse> copyMeal(@PathVariable Long id, @Valid @RequestBody CopyMealRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mealService.copyMeal(currentUser.require(), id, request));
    }

    @PostMapping("/favorites/{id}/copy")
    ResponseEntity<MealResponse> copyFavorite(@PathVariable Long id, @Valid @RequestBody CopyMealRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mealService.copyFavorite(currentUser.require(), id, request));
    }

    @DeleteMapping("/favorites/{id}")
    ResponseEntity<Void> deleteFavorite(@PathVariable Long id) {
        mealService.deleteFavorite(currentUser.require(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        mealService.delete(currentUser.require(), id);
        return ResponseEntity.noContent().build();
    }
}
