package com.myhealth.common;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myhealth.auth.JwtAuthenticationFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(
        controllers = GlobalExceptionHandlerTest.TestController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({ GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class })
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mockMvc;

    @Test
    void apiException_isRenderedAsApiErrorResponse_withMatchingStatusAndCode() throws Exception {
        mockMvc.perform(get("/test/api-exception"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Custom not found"))
                .andExpect(jsonPath("$.path").value("/test/api-exception"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void validationError_returns400_withFieldDetails() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("validation")))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details[*].field").value(org.hamcrest.Matchers.hasItems("email", "name")));
    }

    @Test
    void dataIntegrityViolation_returns409_conflict() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(containsString("already")));
    }

    @Test
    void uncaughtException_returns500_internalError() throws Exception {
        mockMvc.perform(get("/test/explode"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/api-exception")
        void apiException() {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Custom not found");
        }

        @PostMapping("/validation")
        void validation(@Valid @RequestBody Payload payload) {
            // never reached when invalid
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new DataIntegrityViolationException("duplicate");
        }

        @GetMapping("/explode")
        void explode() {
            throw new RuntimeException("kaboom");
        }

        record Payload(@Email @NotBlank String email, @NotBlank String name) {
        }
    }
}
