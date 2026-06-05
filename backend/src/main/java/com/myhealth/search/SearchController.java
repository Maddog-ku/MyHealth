package com.myhealth.search;

import com.myhealth.auth.CurrentUser;
import com.myhealth.search.SearchDtos.SearchResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {
    private final CurrentUser currentUser;
    private final SearchService searchService;

    public SearchController(CurrentUser currentUser, SearchService searchService) {
        this.currentUser = currentUser;
        this.searchService = searchService;
    }

    /** Keyword search across the user's meals and workouts; blank {@code q} returns nothing. */
    @GetMapping
    SearchResponse search(@RequestParam(name = "q", required = false) String q,
                          @RequestParam(required = false) Integer limit) {
        return searchService.search(currentUser.require(), q, limit);
    }
}
