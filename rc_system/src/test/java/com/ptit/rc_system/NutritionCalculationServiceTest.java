package com.ptit.rc_system;

import com.ptit.rc_system.service.NutritionCalculationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NutritionCalculationServiceTest {

    private final NutritionCalculationService nutritionService = new NutritionCalculationService();

    @Test
    void calculatesTargetCaloriesForMaleWithModerateActivityAndGainGoal() {
        double bmr = nutritionService.calculateBMR(50, 170, 20, "male");
        double tdee = nutritionService.calculateTDEE(bmr, 1.55);
        double targetCalories = nutritionService.caculateTargetCalories(tdee, "gain", "male");

        assertEquals(1467.5, bmr);
        assertEquals(2274.625, tdee);
        assertEquals(2774.625, targetCalories);
    }
}
