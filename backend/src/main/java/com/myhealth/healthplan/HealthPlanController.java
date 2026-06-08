package com.myhealth.healthplan;

import com.myhealth.auth.CurrentUser;
import com.myhealth.healthplan.HealthPlanDtos.HealthPlanResponse;
import com.myhealth.healthplan.HealthPlanDtos.HealthPlanSettingsRequest;
import com.myhealth.healthplan.HealthPlanDtos.HealthPlanSettingsResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health-plan")
public class HealthPlanController {
    private final CurrentUser currentUser;
    private final HealthPlanService healthPlan;

    public HealthPlanController(CurrentUser currentUser, HealthPlanService healthPlan) {
        this.currentUser = currentUser;
        this.healthPlan = healthPlan;
    }

    @GetMapping
    HealthPlanResponse get(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return healthPlan.get(currentUser.require(), date);
    }

    @GetMapping("/today")
    HealthPlanResponse today() {
        return healthPlan.get(currentUser.require(), LocalDate.now());
    }

    @GetMapping("/settings")
    HealthPlanSettingsResponse settings() {
        return healthPlan.settings(currentUser.require());
    }

    @PutMapping("/settings")
    HealthPlanSettingsResponse updateSettings(@Valid @RequestBody HealthPlanSettingsRequest request) {
        return healthPlan.updateSettings(currentUser.require(), request);
    }
}
