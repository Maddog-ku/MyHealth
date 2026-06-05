package com.myhealth.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myhealth.auth.CurrentUser;
import com.myhealth.auth.JwtAuthenticationFilter;
import com.myhealth.common.GlobalExceptionHandler;
import com.myhealth.search.SearchDtos.SearchResponse;
import com.myhealth.search.SearchDtos.SearchResult;
import com.myhealth.user.AppUser;
import com.myhealth.user.Role;
import java.time.LocalDate;
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

@WebMvcTest(controllers = SearchController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SearchControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean SearchService searchService;
    @MockBean CurrentUser currentUser;

    private AppUser stubUser() {
        AppUser u = new AppUser();
        u.setEmail("u@example.com");
        u.setRole(Role.USER);
        return u;
    }

    @Test
    void search_returns200_withResults() throws Exception {
        AppUser user = stubUser();
        when(currentUser.require()).thenReturn(user);
        when(searchService.search(eq(user), eq("雞"), isNull())).thenReturn(new SearchResponse("雞",
                List.of(new SearchResult("MEAL", 11L, "雞胸肉沙拉", "午餐", LocalDate.of(2026, 6, 5), 420))));

        mockMvc.perform(get("/api/v1/search").param("q", "雞"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("雞"))
                .andExpect(jsonPath("$.results[0].type").value("MEAL"))
                .andExpect(jsonPath("$.results[0].title").value("雞胸肉沙拉"))
                .andExpect(jsonPath("$.results[0].kcal").value(420));
    }

    @Test
    void search_returns200_whenQueryOmitted() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(searchService.search(any(), isNull(), isNull())).thenReturn(new SearchResponse("", List.of()));

        mockMvc.perform(get("/api/v1/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(0));

        verify(searchService).search(any(), isNull(), isNull());
    }
}
