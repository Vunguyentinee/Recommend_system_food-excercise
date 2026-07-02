package com.ptit.rc_system.controller;

import com.ptit.rc_system.entity.Food;
import com.ptit.rc_system.repository.FoodRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@CrossOrigin(origins = "*")
@RestController
public class FoodAdminController {

    @Autowired
    private FoodRepository foodRepository;

    @GetMapping("/api/admin/foods")
    public List<Food> getAllFoods() {
        return foodRepository.findAll();
    }

    @GetMapping("/api/admin/foods/{id}")
    public Object getFoodById(@PathVariable Long id) {
        return foodRepository.findById(id)
                .map(food -> (Object) food)
                .orElseGet(() -> createErrorResponse("Food not found", "FOOD_NOT_FOUND"));
    }

    @PostMapping("/api/admin/foods")
    public Object createFood(@RequestBody Food food) {
        return foodRepository.save(food);
    }

    @PutMapping("/api/admin/foods/{id}")
    public Object updateFood(@PathVariable Long id, @RequestBody Food update) {
        Optional<Food> existing = foodRepository.findById(id);
        if (existing.isEmpty()) {
            return createErrorResponse("Food not found", "FOOD_NOT_FOUND");
        }

        Food food = existing.get();
        food.setName(update.getName());
        food.setCalories(update.getCalories());
        food.setProtein(update.getProtein());
        food.setCarbs(update.getCarbs());
        food.setFat(update.getFat());
        food.setServingUnit(update.getServingUnit());
        food.setMealType(update.getMealType());
        food.setCategory(update.getCategory());

        return foodRepository.save(food);
    }

    @DeleteMapping("/api/admin/foods/{id}")
    public Object deleteFood(@PathVariable Long id) {
        if (!foodRepository.existsById(id)) {
            return createErrorResponse("Food not found", "FOOD_NOT_FOUND");
        }
        foodRepository.deleteById(id);
        return Map.of("deleted", true, "foodId", id);
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
