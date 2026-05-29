package com.myhealth.ai;

import com.myhealth.ai.AiDtos.AiStatusResponse;
import com.myhealth.ai.AiDtos.UnloadResponse;
import com.myhealth.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {
    private final AiProvider provider;
    private final AppProperties properties;

    public AiController(AiProvider provider, AppProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    @GetMapping("/status")
    AiStatusResponse status() {
        return new AiStatusResponse(
                provider.provider(),
                provider.textModel(),
                provider.visionModel(),
                provider.loaded(),
                provider.lastUsedAt(),
                properties.ai().idleTimeoutSec());
    }

    @PostMapping("/unload")
    UnloadResponse unload() {
        provider.unload();
        return new UnloadResponse(true);
    }
}
