package com.myhealth.export;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myhealth.auth.AuthDtos.UserResponse;
import com.myhealth.auth.CurrentUser;
import com.myhealth.auth.JwtAuthenticationFilter;
import com.myhealth.common.GlobalExceptionHandler;
import com.myhealth.export.ExportDtos.ExportFile;
import com.myhealth.user.AppUser;
import com.myhealth.user.Role;
import java.time.Instant;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ExportController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ExportControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean DataExportService exportService;
    @MockBean CurrentUser currentUser;

    AppUser stubUser() {
        AppUser u = new AppUser();
        u.setEmail("alice@example.com");
        u.setName("Alice");
        u.setRole(Role.USER);
        return u;
    }

    @Test
    void export_returnsJson_withAttachmentDispositionHeader() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(exportService.export(any())).thenReturn(new ExportFile(
                "2026-06-07T00:00:00Z",
                new UserResponse(1L, "alice@example.com", "Alice", Role.USER, null,
                        Instant.parse("2026-05-30T00:00:00Z")),
                List.of(), List.of(), List.of(), null, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/me/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition", Matchers.containsString("myhealth-export-")))
                .andExpect(jsonPath("$.account.email").value("alice@example.com"))
                .andExpect(jsonPath("$.exportedAt").value("2026-06-07T00:00:00Z"));
    }
}
