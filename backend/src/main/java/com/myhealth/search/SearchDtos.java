package com.myhealth.search;

import java.time.LocalDate;
import java.util.List;

public final class SearchDtos {
    private SearchDtos() {
    }

    /**
     * One hit across the user's logs.
     *
     * @param type  MEAL | WORKOUT (drives the icon and which page the UI opens)
     * @param title display-ready primary line (meal description / workout category label)
     */
    public record SearchResult(String type, Long id, String title, String subtitle, LocalDate date, int kcal) {
    }

    public record SearchResponse(String query, List<SearchResult> results) {
    }
}
