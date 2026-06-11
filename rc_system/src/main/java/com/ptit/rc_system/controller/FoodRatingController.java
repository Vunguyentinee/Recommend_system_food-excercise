package com.ptit.rc_system.controller;

import com.ptit.rc_system.service.NutritionPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
public class FoodRatingController {

    private final NutritionPlanService nutritionPlanService;

    public FoodRatingController(NutritionPlanService nutritionPlanService) {
        this.nutritionPlanService = nutritionPlanService;
    }

    @PostMapping("/api/nutrition/rate")
    public Object rateNutritionPlanItem(@RequestBody NutritionPlanService.FoodRatingCommand command) {
        try {
            return nutritionPlanService.ratePlanItem(command);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(createErrorResponse(ex.getMessage(), "FOOD_RATING_FAILED"));
        }
    }

    @PostMapping("/api/nutrition/favorites")
    public Object saveFavoriteRatings(@RequestBody NutritionPlanService.FavoriteFoodRatingCommand command) {
        try {
            return nutritionPlanService.saveFavoriteRatings(command);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(createErrorResponse(ex.getMessage(), "FAVORITE_RATING_FAILED"));
        }
    }

    private Map<String, Object> createErrorResponse(String message, String errorCode) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", message);
        response.put("errorCode", errorCode);
        response.put("data", new ArrayList<>());
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}
