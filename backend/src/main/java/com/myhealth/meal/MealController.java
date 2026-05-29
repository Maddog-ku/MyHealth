package com.myhealth.meal;

import com.myhealth.auth.CurrentUser;
import com.myhealth.common.PageEnvelope;
import com.myhealth.meal.FileStorageService.StoredFile;
import com.myhealth.meal.MealDtos.MealResponse;
import com.myhealth.meal.MealDtos.UpdateMealRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
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

    public MealController(CurrentUser currentUser, MealService mealService) {
        this.currentUser = currentUser;
        this.mealService = mealService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<MealResponse> create(
            @RequestPart(required = false) MultipartFile image,
            @RequestParam(required = false) String description,
            @RequestParam String slot,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mealService.create(currentUser.require(), image, description, slot, date));
    }

    @GetMapping
    PageEnvelope<MealResponse> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return PageEnvelope.unpaged(mealService.list(currentUser.require(), date));
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
                .body(image.resource());
    }

    @PutMapping("/{id}")
    MealResponse update(@PathVariable Long id, @Valid @RequestBody UpdateMealRequest request) {
        return mealService.update(currentUser.require(), id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        mealService.delete(currentUser.require(), id);
        return ResponseEntity.noContent().build();
    }
}
