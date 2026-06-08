package com.myhealth.system;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myhealth.auth.CurrentUser;
import com.myhealth.auth.JwtAuthenticationFilter;
import com.myhealth.common.GlobalExceptionHandler;
import com.myhealth.system.SystemDtos.ComponentStatus;
import com.myhealth.system.SystemDtos.SystemStatusResponse;
import com.myhealth.user.AppUser;
import com.myhealth.user.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SystemStatusController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SystemStatusControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean CurrentUser currentUser;
    @MockBean SystemStatusService systemStatusService;

    @Test
    void status_returnsComponentHealth() throws Exception {
        AppUser user = new AppUser();
        user.setEmail("u@example.com");
        user.setRole(Role.USER);
        when(currentUser.require()).thenReturn(user);
        when(systemStatusService.status()).thenReturn(new SystemStatusResponse(
                "UP",
                Instant.parse("2026-06-08T00:00:00Z"),
                List.of(
                        new ComponentStatus("backend", "Backend API", "UP", "request handled"),
                        new ComponentStatus("database", "Database", "UP", "connection validated"))));

        mockMvc.perform(get("/api/v1/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components[0].key").value("backend"))
                .andExpect(jsonPath("$.components[1].status").value("UP"));
    }
}
