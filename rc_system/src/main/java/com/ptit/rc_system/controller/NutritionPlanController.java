package com.ptit.rc_system.controller;

import com.ptit.rc_system.service.NutritionPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@CrossOrigin(origins = "*")
@RestController
public class NutritionPlanController {

    private final NutritionPlanService nutritionPlanService;
    private final NutritionController nutritionController;

    public NutritionPlanController(NutritionPlanService nutritionPlanService,
                                   NutritionController nutritionController) {
        this.nutritionPlanService = nutritionPlanService;
        this.nutritionController = nutritionController;
    }

    @GetMapping("/api/nutrition/plan")
    public Object getOrCreateNutritionPlan(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "false") boolean regenerate) {
        if (!regenerate) {
            Optional<Map<String, Object>> existingPlan = nutritionPlanService.findExistingTodayPlan(userId);
            if (existingPlan.isPresent()) {
                return existingPlan.get();
            }
        }

        if (regenerate && nutritionPlanService.hasCompletedTodayPlan(userId)) {
            return ResponseEntity.badRequest().body(createErrorResponse(
                    "Không thể đổi thực đơn sau khi đã hoàn thành/chấm điểm món ăn.",
                    "FOOD_PLAN_ALREADY_STARTED"
            ));
        }



        Map<String, Object> profile = nutritionController.loadLatestProfileForPlan(userId);
        Double targetCalories = numberOrNull(profile.get("targetCalories"));
        Map<String, List<Map<String, Object>>> meals = nutritionController.generateMealsForUser(userId, profile);
        return nutritionPlanService.replaceTodayPlan(userId, regenerate, targetCalories, meals);
    }

    @GetMapping("/api/nutrition/history")
    public Object getNutritionHistory(
            @RequestParam Long userId,
            @RequestParam(required = false) LocalDate date) {
        return nutritionPlanService.history(userId, date);
    }

    private Double numberOrNull(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
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
