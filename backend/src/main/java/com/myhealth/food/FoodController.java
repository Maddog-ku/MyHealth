package com.myhealth.food;

import com.myhealth.food.FoodDtos.FoodResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/foods")
public class FoodController {
    private final FoodService foodService;

    public FoodController(FoodService foodService) {
        this.foodService = foodService;
    }

    @GetMapping
    List<FoodResponse> search(@RequestParam(name = "q", required = false) String q,
                              @RequestParam(required = false) Integer limit) {
        return foodService.search(q, limit);
    }
}
