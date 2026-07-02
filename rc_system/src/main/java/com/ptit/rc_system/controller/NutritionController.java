package com.ptit.rc_system.controller;

import com.ptit.rc_system.entity.InteractionLog;
import com.ptit.rc_system.repository.InteractionLogRepository;
import com.ptit.rc_system.dto.FoodPortion;
import com.ptit.rc_system.entity.Food;
import com.ptit.rc_system.repository.FoodRepository;
import com.ptit.rc_system.service.AiRecommendationClient;
import com.ptit.rc_system.service.FoodCollaborativeFilteringService;
import com.ptit.rc_system.service.NutritionCalculationService;
import com.ptit.rc_system.service.NutritionRecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
public class NutritionController {
    private static final Logger logger = LoggerFactory.getLogger(NutritionController.class);

    @Autowired
    private NutritionCalculationService nutritionService;

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private AiRecommendationClient aiRecommendationClient;

    @Autowired
    private InteractionLogRepository interactionLogRepository;

    @Autowired
    private FoodCollaborativeFilteringService collaborativeFilteringService;

    @Autowired
    private NutritionRecommendationService nutritionRecommendationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ===== ENDPOINT 1: User-Based CF =====
    @GetMapping("/api/recommend-cf")
    public Object recommendByCollaborativeFiltering(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "5") int topK) {

        boolean userExists = interactionLogRepository.existsByUserId(userId);
        if (!userExists) {
            logger.warn("❌ User {} does not exist", userId);
            return createErrorResponse("User not found", "USER_NOT_FOUND");
        }

        List<Long> recommendedFoodIds = aiRecommendationClient
                .recommendUserBased(userId, topK);

        if (recommendedFoodIds.isEmpty()) {
            logger.warn("⚠️ CF returned empty for userId: {}, using local CF", userId);
            recommendedFoodIds = collaborativeFilteringService.recommendFoodsForUser(userId, topK);
        }

        if (recommendedFoodIds.isEmpty()) {
            logger.warn("⚠️ Local CF empty for userId: {}, using top-rated fallback", userId);
            recommendedFoodIds = collaborativeFilteringService.getTopRatedFoodsByUser(userId, topK);
        }

        recommendedFoodIds = fillToTopK(recommendedFoodIds, topK);

        return createResponse(foodRepository.findAllById(recommendedFoodIds), "USER_BASED_CF", topK);
    }

    // ===== ENDPOINT 2: Item-Based CF =====
    @GetMapping("/api/recommend-cf-item")
    public Object recommendByItemBased(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "5") int topK) {

        boolean userExists = interactionLogRepository.existsByUserId(userId);
        if (!userExists) {
            return createErrorResponse("User not found", "USER_NOT_FOUND");
        }

        List<Long> recommendedFoodIds = aiRecommendationClient
                .recommendItemBased(userId, topK);

        if (recommendedFoodIds.isEmpty()) {
            logger.warn("⚠️ Item-Based CF returned empty for userId: {}, using local CF", userId);
            recommendedFoodIds = collaborativeFilteringService.recommendFoodsByItemBased(userId, topK);
        }

        if (recommendedFoodIds.isEmpty()) {
            logger.warn("⚠️ Item-Based local CF empty for userId: {}, using top-rated fallback", userId);
            recommendedFoodIds = collaborativeFilteringService.getTopRatedFoodsByUser(userId, topK);
        }

        recommendedFoodIds = fillToTopK(recommendedFoodIds, topK);

        return createResponse(foodRepository.findAllById(recommendedFoodIds), "ITEM_BASED_CF", topK);
    }

    // ===== ENDPOINT 3: Hybrid CF =====
    @GetMapping("/api/recommend-cf-hybrid")
    public Object recommendByHybridCF(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(defaultValue = "0.6") double userWeight) {

        boolean userExists = interactionLogRepository.existsByUserId(userId);
        if (!userExists) {
            return createErrorResponse("User not found", "USER_NOT_FOUND");
        }

        List<Long> recommendedFoodIds = aiRecommendationClient
                .recommendHybrid(userId, topK, userWeight);

        if (recommendedFoodIds.isEmpty()) {
            logger.warn("⚠️ Hybrid CF returned empty for userId: {}, using local hybrid", userId);
            recommendedFoodIds = collaborativeFilteringService.recommendFoodsHybrid(userId, topK, userWeight);
        }

        if (recommendedFoodIds.isEmpty()) {
            logger.warn("⚠️ Hybrid local CF empty for userId: {}, using top-rated fallback", userId);
            recommendedFoodIds = collaborativeFilteringService.getTopRatedFoodsByUser(userId, topK);
        }

        recommendedFoodIds = fillToTopK(recommendedFoodIds, topK);

        return createResponse(foodRepository.findAllById(recommendedFoodIds), "HYBRID_CF", topK);
    }

    // ===== ENDPOINT 4: Content-Based (TDEE) =====
    @GetMapping("/api/recommend-tdee")
    public Object recommendByTDEE(
            @RequestParam double weight,
            @RequestParam double height,
            @RequestParam int age,
            @RequestParam String gender,
            @RequestParam double activityLevel,
            @RequestParam String healthGoal,
            @RequestParam(defaultValue = "true") boolean isTraditional) {

        logger.info("📊 TDEE Recommendation: weight={}, height={}, age={}, gender={}",
                weight, height, age, gender);

        double bmr = nutritionService.calculateBMR(weight, height, age, gender);
        double tdee = nutritionService.calculateTDEE(bmr, activityLevel);
        double targetCalories = nutritionService.caculateTargetCalories(tdee, healthGoal, gender);

        List<Food> allFoods = foodRepository.findAll();
        List<FoodPortion> result = nutritionService.generateLunchCombo(targetCalories, allFoods, isTraditional);

        logger.info("✅ TDEE: BMR={}, TDEE={}, Target={}, Foods={}",
                Math.round(bmr), Math.round(tdee), Math.round(targetCalories), result.size());

        return createResponse(result, "CONTENT_BASED_TDEE");
    }

    // ===== ENDPOINT 5: POPULAR FOODS (Fallback for New Users) =====
    @GetMapping("/api/recommend-popular")
    public Object recommendPopularFoods(
            @RequestParam(defaultValue = "5") int topK) {

        logger.info("📊 Popular Foods: topK={}", topK);

        List<Long> popularFoodIds = collaborativeFilteringService.getPopularFoods(topK);

        if (popularFoodIds.isEmpty()) {
            logger.warn("⚠️ No popular foods found, returning random foods");
            popularFoodIds = foodRepository.findAll()
                    .stream()
                    .limit(topK)
                    .map(Food::getId)
                    .collect(Collectors.toList());
        }

        return createResponse(foodRepository.findAllById(popularFoodIds), "POPULARITY_BASED", topK);
    }

    // ===== ENDPOINT 6: SMART RECOMMENDATION - Cho Cả User Cũ & User Mới =====
    @GetMapping("/api/recommend-smart")
    public Object recommendSmart(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Double weight,
            @RequestParam(required = false) Double height,
            @RequestParam(required = false) Integer age,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) Double activityLevel,
            @RequestParam(required = false) String healthGoal,
            @RequestParam(defaultValue = "true") boolean isTraditional,
            @RequestParam(defaultValue = "5") int topK) {

        return nutritionRecommendationService.recommendSmart(userId, weight, height, age, gender,
                activityLevel, healthGoal, isTraditional, topK);
    }

    // ===== ENDPOINT 7: ONBOARDING FLOW (For New Users) =====
    @GetMapping("/api/onboard/initial-recommendation")
    public Object onboardingInitialRecommendation(
            @RequestParam(required = false) Double weight,
            @RequestParam(required = false) Double height,
            @RequestParam(required = false) Integer age,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) Double activityLevel,
            @RequestParam(required = false) String healthGoal,
            @RequestParam(defaultValue = "true") boolean isTraditional,
            @RequestParam(defaultValue = "10") int topK) {

        return nutritionRecommendationService.onboardingInitialRecommendation(weight, height, age, gender,
                activityLevel, healthGoal, isTraditional, topK);
    }

    // ===== ENDPOINT 8: Suggest Next Action After Rating N Items =====
    @GetMapping("/api/suggest-after-rating")
    public Object suggestAfterRating(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "5") int topK) {

        return nutritionRecommendationService.suggestAfterRating(userId, topK);
    }

    // ===== API: Get User Rating History =====
    @GetMapping("/api/user-ratings/{userId}")
    public Object getUserRatings(@PathVariable Long userId) {
        List<InteractionLog> logs = interactionLogRepository.findByUserIdAndRatingIsNotNull(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("totalRatings", logs.size());
        response.put("ratings", logs);
        response.put("avgRating", logs.stream()
                .mapToDouble(InteractionLog::getRating)
                .average()
                .orElse(0.0));

        return response;
    }

    // ===== API: Get Recommendation Status =====
    @GetMapping("/api/user-status/{userId}")
    public Object getUserStatus(@PathVariable Long userId) {
        List<InteractionLog> logs = interactionLogRepository.findByUserIdAndRatingIsNotNull(userId);
        boolean userExists = !logs.isEmpty();

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("exists", userExists);
        response.put("ratingCount", logs.size());
        response.put("canUseCollaborativeFiltering", logs.size() >= 3);
        response.put("recommendedStrategy", logs.size() >= 3 ? "COLLABORATIVE_FILTERING" : "CONTENT_BASED");

        if (logs.size() >= 3) {
            response.put("suggestion", "User has enough ratings. Use /api/recommend-smart with userId");
        } else {
            response.put("suggestion", "User needs " + (3 - logs.size()) + " more ratings to use CF");
        }

        return response;
    }

    // ===== DEBUG ENDPOINTS =====
    @GetMapping("/api/debug/cf-status")
    public Object debugCFStatus(@RequestParam Long userId) {
        Map<Long, Map<Long, Double>> matrix = collaborativeFilteringService.buildUserItemMatrix();

        Map<String, Object> debug = new HashMap<>();
        debug.put("totalUsers", matrix.size());
        debug.put("totalFoods", matrix.values().stream().flatMap(m -> m.keySet().stream()).distinct().count());

        Map<Long, Double> targetRatings = matrix.getOrDefault(userId, new HashMap<>());
        debug.put("user" + userId + "RatingCount", targetRatings.size());
        debug.put("user" + userId + "Ratings", targetRatings);

        Map<Long, Double> similarities = new HashMap<>();
        for (Long otherUserId : matrix.keySet()) {
            if (!otherUserId.equals(userId)) {
                double sim = collaborativeFilteringService.calculateSimilarity(
                        targetRatings, matrix.get(otherUserId)
                );
                if (sim > 0) similarities.put(otherUserId, sim);
            }
        }

        debug.put("similarUsers", similarities);
        debug.put("similarUsersCount", similarities.size());

        return debug;
    }

    @GetMapping("/api/debug/cf-detailed")
    public Object debugCFDetailed(@RequestParam Long userId) {
        Map<Long, Map<Long, Double>> matrix = collaborativeFilteringService.buildUserItemMatrix();

        Map<Long, Double> targetUserRatings = matrix.getOrDefault(userId, new HashMap<>());

        Set<Long> allFoodIds = new HashSet<>();
        for (Map<Long, Double> ratings : matrix.values()) {
            allFoodIds.addAll(ratings.keySet());
        }

        Set<Long> unratedFoods = new HashSet<>(allFoodIds);
        unratedFoods.removeAll(targetUserRatings.keySet());

        Map<Long, java.lang.Integer> foodRatedBySimilarUsers = new HashMap<>();
        for (Long otherUserId : matrix.keySet()) {
            if (!otherUserId.equals(userId)) {
                double sim = collaborativeFilteringService.calculateSimilarity(
                        targetUserRatings, matrix.get(otherUserId)
                );
                if (sim > 0) {
                    for (Long foodId : matrix.get(otherUserId).keySet()) {
                        if (unratedFoods.contains(foodId)) {
                            foodRatedBySimilarUsers.put(foodId,
                                    foodRatedBySimilarUsers.getOrDefault(foodId, 0) + 1);
                        }
                    }
                }
            }
        }

        Map<String, Object> debug = new HashMap<>();
        debug.put("totalFoods", allFoodIds.size());
        debug.put("userRatedCount", targetUserRatings.size());
        debug.put("unratedFoodsCount", unratedFoods.size());
        debug.put("unratedFoods", unratedFoods);
        debug.put("foodRatedBySimilarUsers", foodRatedBySimilarUsers);
        debug.put("similarUsersHaveFoodsForUserCount", foodRatedBySimilarUsers.size());

        return debug;
    }

    // ===== HELPER METHODS =====
    private List<Long> fillToTopK(List<Long> ids, int topK) {
        if (ids == null) {
            ids = new ArrayList<>();
        }

        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(ids);

        if (uniqueIds.size() < topK) {
            for (Long foodId : collaborativeFilteringService.getPopularFoods(topK * 2)) {
                uniqueIds.add(foodId);
                if (uniqueIds.size() >= topK) {
                    break;
                }
            }
        }

        if (uniqueIds.isEmpty()) {
            List<Long> randomIds = foodRepository.findAll().stream()
                    .limit(topK)
                    .map(Food::getId)
                    .collect(Collectors.toList());
            uniqueIds.addAll(randomIds);
        }

        return new ArrayList<>(uniqueIds).subList(0, Math.min(topK, uniqueIds.size()));
    }

    private Map<String, Object> createResponse(List<?> items, String strategy) {
        return createResponse(items, strategy, items.size());
    }

    private Map<String, Object> createResponse(List<?> items, String strategy, int topK) {
        Map<String, Object> response = new HashMap<>();
        response.put("data", items);
        response.put("strategy", strategy);
        response.put("count", items.size());
        response.put("requested", topK);
        response.put("timestamp", System.currentTimeMillis());
        return response;
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
