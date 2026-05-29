package com.ptit.rc_system.controller;

import com.ptit.rc_system.entity.InteractionLog;
import com.ptit.rc_system.repository.InteractionLogRepository;
import com.ptit.rc_system.dto.FoodPortion;
import com.ptit.rc_system.entity.Food;
import com.ptit.rc_system.repository.FoodRepository;
import com.ptit.rc_system.service.AiRecommendationClient;
import com.ptit.rc_system.service.CollaborativeFilteringService;
import com.ptit.rc_system.service.NutritionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
public class NutritionController {
    private static final Logger logger = LoggerFactory.getLogger(NutritionController.class);

    @Autowired
    private NutritionService nutritionService;

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private AiRecommendationClient aiRecommendationClient;

    @Autowired
    private InteractionLogRepository interactionLogRepository;

    @Autowired
    private CollaborativeFilteringService collaborativeFilteringService;

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

    // ===== ✅ ENDPOINT 5: POPULAR FOODS (Fallback for New Users) =====
    @GetMapping("/api/recommend-popular")
    public Object recommendPopularFoods(
            @RequestParam(defaultValue = "5") int topK) {

        logger.info("📊 Popular Foods: topK={}", topK);

        List<Long> popularFoodIds = getPopularFoods(topK);

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

    // ===== ✅ ENDPOINT 6: SMART RECOMMENDATION - Cho Cả User Cũ & User Mới =====
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

        logger.info("🔍 Smart Recommend called: userId={}, hasProfile={}",
                userId, weight != null && height != null);

        // ===== CASE 1: User Cũ + Full Profile (Tối ưu nhất) =====
        if (userId != null && weight != null && height != null && age != null && gender != null
                && activityLevel != null && healthGoal != null) {

            boolean userExists = interactionLogRepository.existsByUserId(userId);
            if (userExists) {
                logger.info("✅ CASE 1: Old User + Full Profile → Triple Hybrid (60% CF + 40% CB)");
                Object response = recommendTripleHybrid(userId, weight, height, age, gender,
                        activityLevel, healthGoal, isTraditional, topK);

                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) response;
                @SuppressWarnings("unchecked")
                List<Food> data = (List<Food>) responseMap.getOrDefault("data", List.of());

                Map<String, List<Map<String, Object>>> meals = buildDailyPlan(data, foodRepository.findAll());
                List<Map<String, Object>> flat = flattenMeals(meals);
                if (!flat.isEmpty()) {
                    Map<String, Object> payload = createResponse(flat, "TRIPLE_HYBRID_DAILY", topK);
                    payload.put("meals", meals);
                    return payload;
                }

                double bmr = nutritionService.calculateBMR(weight, height, age, gender);
                double tdee = nutritionService.calculateTDEE(bmr, activityLevel);
                double targetCalories = nutritionService
                        .caculateTargetCalories(tdee, healthGoal, gender);
                List<FoodPortion> fallback = nutritionService
                        .generateLunchCombo(targetCalories, foodRepository.findAll(), isTraditional);
                List<Food> fallbackFoods = fallback.stream().map(FoodPortion::getFood).toList();
                Map<String, List<Map<String, Object>>> fallbackMeals = buildDailyPlan(fallbackFoods, foodRepository.findAll());
                Map<String, Object> payload = createResponse(flattenMeals(fallbackMeals), "CONTENT_BASED_FOR_MEAL_STYLE", topK);
                payload.put("meals", fallbackMeals);
                return payload;
            }
        }

        // ===== CASE 2: User Cũ, Chỉ có userId (Dùng CF) =====
        if (userId != null) {
            boolean userExists = interactionLogRepository.existsByUserId(userId);
            if (userExists) {
                logger.info("✅ CASE 2: Old User + userId only → Hybrid CF");
                List<Long> recs = aiRecommendationClient
                    .recommendHybrid(userId, topK, 0.6);

                if (recs.isEmpty()) {
                    logger.warn("⚠️ CF empty → Local hybrid CF");
                    recs = collaborativeFilteringService.recommendFoodsHybrid(userId, topK, 0.6);
                }

                if (recs.isEmpty()) {
                    logger.warn("⚠️ Hybrid empty → Top-Rated fallback");
                    recs = collaborativeFilteringService.getTopRatedFoodsByUser(userId, topK);
                }

                recs = fillToTopK(recs, topK);

                List<Food> foods = foodRepository.findAllById(recs);
                Map<String, List<Map<String, Object>>> meals = buildDailyPlan(foods, foodRepository.findAll());
                Map<String, Object> payload = createResponse(flattenMeals(meals), "HYBRID_CF_DAILY", topK);
                payload.put("meals", meals);
                return payload;
            } else {
                logger.warn("⚠️ User {} not found", userId);
            }
        }

        // ===== CASE 3: User Mới + Full Profile (Content-Based) =====
        if (weight != null && height != null && age != null && gender != null
                && activityLevel != null && healthGoal != null) {

            logger.info("✅ CASE 3: NEW User + Full Profile → Content-Based (TDEE)");
            double bmr = nutritionService.calculateBMR(weight, height, age, gender);
            double tdee = nutritionService.calculateTDEE(bmr, activityLevel);
            double targetCalories = nutritionService
                    .caculateTargetCalories(tdee, healthGoal, gender);

            List<Food> allFoods = foodRepository.findAll();
            List<FoodPortion> result = nutritionService.generateLunchCombo(targetCalories, allFoods, isTraditional);
            List<Food> candidateFoods = result.stream().map(FoodPortion::getFood).toList();

            Map<String, List<Map<String, Object>>> meals = buildDailyPlan(candidateFoods, allFoods);
            Map<String, Object> payload = createResponse(flattenMeals(meals), "CONTENT_BASED_DAILY", topK);
            payload.put("meals", meals);
            return payload;
        }

        // ===== CASE 4: User Mới + NO Profile (Popularity-Based) =====
        logger.info("✅ CASE 4: NEW User + NO Profile → Popularity-Based");
        List<Long> popularFoods = getPopularFoods(topK);
        List<Food> popular = foodRepository.findAllById(popularFoods);
        Map<String, List<Map<String, Object>>> meals = buildDailyPlan(popular, foodRepository.findAll());
        Map<String, Object> payload = createResponse(flattenMeals(meals), "POPULARITY_DAILY", topK);
        payload.put("meals", meals);
        return payload;
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

        logger.info("🆕 Onboarding: New user initial recommendation");

        if (weight != null && height != null && age != null && gender != null
                && activityLevel != null && healthGoal != null) {

            // Content-Based recommendation
            double bmr = nutritionService.calculateBMR(weight, height, age, gender);
            double tdee = nutritionService.calculateTDEE(bmr, activityLevel);
            double targetCalories = nutritionService
                    .caculateTargetCalories(tdee, healthGoal, gender);

            List<Food> allFoods = foodRepository.findAll();
            List<FoodPortion> result = nutritionService.generateLunchCombo(targetCalories, allFoods, isTraditional);

            logger.info("✅ Onboarding recommendation: {} foods", result.size());
            return createResponse(result, "ONBOARDING_TDEE");
        }

        // Fallback: Popularity-based
        List<Long> popularFoods = getPopularFoods(topK);
        return createResponse(foodRepository.findAllById(popularFoods), "ONBOARDING_POPULARITY", topK);
    }

    // ===== ✅ ENDPOINT 8: Suggest Next Action After Rating N Items =====
    @GetMapping("/api/suggest-after-rating")
    public Object suggestAfterRating(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "5") int topK) {

        logger.info("📊 Checking if user {} can use CF", userId);

        long ratingCount = interactionLogRepository.findByUserId(userId).size();
        logger.info("User {} has {} ratings", userId, ratingCount);

        if (ratingCount >= 3) {
            logger.info("✅ User {} has enough ratings → Switch to CF", userId);
            List<Long> recs = aiRecommendationClient
                    .recommendHybrid(userId, topK, 0.6);

            if (recs.isEmpty()) {
                recs = getPopularFoods(topK);
            }

            return createResponse(foodRepository.findAllById(recs), "CF_AFTER_RATING", topK);
        } else {
            logger.warn("⚠️ User {} has only {} ratings → Continue with popularity", userId, ratingCount);
            List<Long> popularFoods = getPopularFoods(topK);
            return createResponse(foodRepository.findAllById(popularFoods), "POPULARITY_CONTINUE", topK);
        }
    }

    // ===== HELPER METHOD: Triple Hybrid =====
    private Object recommendTripleHybrid(Long userId, Double weight, Double height,
                                         Integer age, String gender, Double activityLevel,
                                         String healthGoal, boolean isTraditional, int topK) {
        logger.info("🔄 Triple Hybrid: CF (60%) + Content-Based (40%)");

        // 60% từ Hybrid CF
        List<Long> cfRecs = aiRecommendationClient
            .recommendHybrid(userId, topK * 2, 0.6);

        if (cfRecs.isEmpty()) {
            logger.warn("⚠️ CF empty in Triple Hybrid → Local hybrid CF");
            cfRecs = collaborativeFilteringService.recommendFoodsHybrid(userId, topK * 2, 0.6);
        }

        if (cfRecs.isEmpty()) {
            logger.warn("⚠️ CF empty in Triple Hybrid → Top-Rated fallback");
            cfRecs = collaborativeFilteringService.getTopRatedFoodsByUser(userId, topK * 2);
        }

        // 40% từ Content-Based
        double bmr = nutritionService.calculateBMR(weight, height, age, gender);
        double tdee = nutritionService.calculateTDEE(bmr, activityLevel);
        double targetCalories = nutritionService
                .caculateTargetCalories(tdee, healthGoal, gender);

        List<Food> allFoods = foodRepository.findAll();
        List<FoodPortion> cbRecs = nutritionService
                .generateLunchCombo(targetCalories, allFoods, isTraditional);

        // Kết hợp: 60% CF + 40% CB
        List<Food> result = new ArrayList<>();

        // Thêm 60% từ CF
        int cfCount = (int) Math.ceil(topK * 0.6);
        List<Long> cfSubset = cfRecs.subList(0, Math.min(cfCount, cfRecs.size()));
        result.addAll(foodRepository.findAllById(cfSubset));

        // Thêm 40% từ CB
        int cbCount = topK - result.size();
        for (int i = 0; i < Math.min(cbCount, cbRecs.size()); i++) {
            result.add(cbRecs.get(i).getFood());
        }

        logger.info("✅ Triple Hybrid result: {} items (CF: {}, CB: {})",
                result.size(), cfCount, cbCount);

        return createResponse(result, "TRIPLE_HYBRID", topK);
    }

    // ===== ✅ HELPER: Popularity-Based Fallback =====
    private List<Long> getPopularFoods(int topK) {
        logger.info("📊 Getting popular foods");

        List<InteractionLog> allLogs = interactionLogRepository.findAllByFoodIdIsNotNull();

        Map<Long, Integer> foodCount = new HashMap<>();
        Map<Long, Double> foodAvgRating = new HashMap<>();

        for (InteractionLog log : allLogs) {
            Long foodId = log.getFoodId();
            foodCount.put(foodId, foodCount.getOrDefault(foodId, 0) + 1);
            foodAvgRating.put(foodId,
                    foodAvgRating.getOrDefault(foodId, 0.0) + log.getRating());
        }

        foodAvgRating.forEach((foodId, totalRating) ->
                foodAvgRating.put(foodId, totalRating / foodCount.get(foodId)));

        List<Long> popularFoods = foodCount.keySet().stream()
                .sorted((a, b) -> {
                    double scoreA = foodCount.get(a) * foodAvgRating.get(a);
                    double scoreB = foodCount.get(b) * foodAvgRating.get(b);
                    return Double.compare(scoreB, scoreA);
                })
                .limit(topK)
                .collect(Collectors.toList());

        logger.info("✅ Found {} popular foods", popularFoods.size());
        return popularFoods;
    }

    // ===== HELPER: Create Response with Metadata =====
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

    // ===== API: Save Rating =====
    @PostMapping("/api/rate-food")
    public Object rateFood(
            @RequestParam Long userId,
            @RequestParam Long foodId,
            @RequestParam Double rating) {

        if (rating < 0 || rating > 5) {
            logger.warn("❌ Invalid rating: {}", rating);
            return createErrorResponse("Rating must be between 0-5", "INVALID_RATING");
        }

        InteractionLog log = new InteractionLog();
        log.setUserId(userId);
        log.setFoodId(foodId);
        log.setRating(rating);

        InteractionLog saved = interactionLogRepository.save(log);
        logger.info("💾 Saved rating: userId={}, foodId={}, rating={}", userId, foodId, rating);

        return Map.of(
                "logId", saved.getLogId(),
                "userId", userId,
                "foodId", foodId,
                "rating", rating,
                "message", "Rating saved successfully"
        );
    }

    // ===== API: Get User Rating History =====
    @GetMapping("/api/user-ratings/{userId}")
    public Object getUserRatings(@PathVariable Long userId) {
        List<InteractionLog> logs = interactionLogRepository.findByUserId(userId);

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
        List<InteractionLog> logs = interactionLogRepository.findByUserId(userId);
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

    @GetMapping("/api/debug/cf-status")
    public Object debugCFStatus(@RequestParam Long userId) {
        Map<Long, Map<Long, Double>> matrix = collaborativeFilteringService.buildUserItemMatrix();

        Map<String, Object> debug = new HashMap<>();
        debug.put("totalUsers", matrix.size());
        debug.put("totalFoods", matrix.values().stream().flatMap(m -> m.keySet().stream()).distinct().count());

        Map<Long, Double> targetRatings = matrix.getOrDefault(userId, new HashMap<>());
        debug.put("user" + userId + "RatingCount", targetRatings.size());
        debug.put("user" + userId + "Ratings", targetRatings);

        // Kiểm tra similarity
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

        // Tìm những food chưa được user này đánh giá
        Set<Long> allFoodIds = new HashSet<>();
        for (Map<Long, Double> ratings : matrix.values()) {
            allFoodIds.addAll(ratings.keySet());
        }

        Set<Long> unratedFoods = new HashSet<>(allFoodIds);
        unratedFoods.removeAll(targetUserRatings.keySet());

        // Kiểm tra những food chưa được user đánh giá, nhưng được similar user đánh giá
        Map<Long, Integer> foodRatedBySimilarUsers = new HashMap<>();
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

    // ===== ADMIN: Food Management =====
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
        Food saved = foodRepository.save(food);
        return saved;
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

    private List<Long> fillToTopK(List<Long> ids, int topK) {
        if (ids == null) {
            ids = new ArrayList<>();
        }

        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(ids);

        if (uniqueIds.size() < topK) {
            for (Long foodId : getPopularFoods(topK * 2)) {
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

    private List<Food> filterFoodsByMealStyle(List<Food> foods, boolean isTraditional) {
        Set<String> allowed = isTraditional
                ? Set.of("Món tinh bột", "Món đạm", "Món rau", "Món hoa quả")
                : Set.of("Món hỗn hợp", "Món nước");
        return foods.stream()
                .filter(food -> food.getCategory() != null && allowed.contains(food.getCategory()))
                .collect(Collectors.toList());
    }

    private List<Food> getFallbackFoodsByMealStyle(int topK, boolean isTraditional) {
        List<Long> popularIds = getPopularFoods(topK * 2);
        List<Food> popularFoods = foodRepository.findAllById(popularIds);
        List<Food> filtered = filterFoodsByMealStyle(popularFoods, isTraditional);
        if (filtered.size() >= topK) {
            return filtered.subList(0, topK);
        }

        List<Food> allFoods = foodRepository.findAll();
        List<Food> allFiltered = filterFoodsByMealStyle(allFoods, isTraditional);
        LinkedHashMap<Long, Food> merged = new LinkedHashMap<>();
        for (Food food : filtered) {
            merged.put(food.getId(), food);
        }
        for (Food food : allFiltered) {
            merged.putIfAbsent(food.getId(), food);
            if (merged.size() >= topK) {
                break;
            }
        }
        return new ArrayList<>(merged.values());
    }

    private Map<String, List<Map<String, Object>>> buildDailyPlan(List<Food> candidates, List<Food> fallbackFoods) {
        Set<Long> usedIds = new HashSet<>();
        Map<String, List<Map<String, Object>>> meals = new LinkedHashMap<>();

        String mixedMealKey = pickRandomMixedMealKey();
        boolean isBreakfastTraditional = !"breakfast".equals(mixedMealKey);
        boolean isLunchTraditional = !"lunch".equals(mixedMealKey);
        boolean isDinnerTraditional = !"dinner".equals(mixedMealKey);

        List<Map<String, Object>> breakfast = buildMealPlan("breakfast", isBreakfastTraditional, candidates, fallbackFoods, usedIds);
        List<Map<String, Object>> lunch = buildMealPlan("lunch", isLunchTraditional, candidates, fallbackFoods, usedIds);
        List<Map<String, Object>> dinner = buildMealPlan("dinner", isDinnerTraditional, candidates, fallbackFoods, usedIds);

        meals.put("breakfast", breakfast);
        meals.put("lunch", lunch);
        meals.put("dinner", dinner);
        return meals;
    }

    private String pickRandomMixedMealKey() {
        String[] meals = {"breakfast", "lunch", "dinner"};
        return meals[new Random().nextInt(meals.length)];
    }

    private List<Map<String, Object>> buildMealPlan(String mealKey, boolean isTraditional,
                                                    List<Food> candidates, List<Food> fallbackFoods,
                                                    Set<Long> usedIds) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (isTraditional) {
            Food carb = pickFoodByCategory(mealKey, "Món tinh bột", candidates, fallbackFoods, usedIds);
            Food protein = pickFoodByCategory(mealKey, "Món đạm", candidates, fallbackFoods, usedIds);
            Food veg = pickFoodByCategory(mealKey, "Món rau", candidates, fallbackFoods, usedIds);
            if (veg == null) {
                veg = pickFoodByCategory(mealKey, "Món hoa quả", candidates, fallbackFoods, usedIds);
            }

            addIfPresent(result, carb, mealKey, usedIds);
            addIfPresent(result, protein, mealKey, usedIds);
            addIfPresent(result, veg, mealKey, usedIds);
        } else {
            Food mixed = pickFoodByCategory(mealKey, "Món hỗn hợp", candidates, fallbackFoods, usedIds);
            Food water = pickFoodByCategory(mealKey, "Món nước", candidates, fallbackFoods, usedIds);

            addIfPresent(result, mixed, mealKey, usedIds);
            addIfPresent(result, water, mealKey, usedIds);
        }

        return result;
    }

    private Food pickFoodByCategory(String mealKey, String category,
                                    List<Food> primary, List<Food> fallback,
                                    Set<Long> usedIds) {
        Food picked = findFirstMatching(mealKey, category, primary, usedIds, true);
        if (picked != null) return picked;
        return findFirstMatching(mealKey, category, fallback, usedIds, true);
    }

    private Food findFirstMatching(String mealKey, String category, List<Food> foods,
                                   Set<Long> usedIds, boolean strictMealMatch) {
        for (Food food : foods) {
            if (food == null || usedIds.contains(food.getId())) continue;
            if (category != null && (food.getCategory() == null || !food.getCategory().equalsIgnoreCase(category))) {
                continue;
            }
            if (strictMealMatch && !matchesMealType(food, mealKey)) {
                continue;
            }
            return food;
        }
        return null;
    }

    private boolean matchesMealType(Food food, String mealKey) {
        if (food == null || food.getMealType() == null) return false;
        return food.getMealType().toLowerCase().contains(mealKey.toLowerCase());
    }

    private void addIfPresent(List<Map<String, Object>> target, Food food, String mealKey, Set<Long> usedIds) {
        if (food == null) return;
        usedIds.add(food.getId());
        target.add(toMealFoodPayload(food, mealKey));
    }

    private Map<String, Object> toMealFoodPayload(Food food, String mealKey) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", food.getId());
        payload.put("name", food.getName());
        payload.put("category", food.getCategory());
        payload.put("calories", food.getCalories());
        payload.put("protein", food.getProtein());
        payload.put("carbs", food.getCarbs());
        payload.put("fat", food.getFat());
        payload.put("servingUnit", food.getServingUnit());
        payload.put("mealType", capitalizeMeal(mealKey));
        return payload;
    }

    private String capitalizeMeal(String mealKey) {
        if (mealKey == null || mealKey.isEmpty()) return "";
        return mealKey.substring(0, 1).toUpperCase() + mealKey.substring(1).toLowerCase();
    }

    private List<Map<String, Object>> flattenMeals(Map<String, List<Map<String, Object>>> meals) {
        List<Map<String, Object>> flat = new ArrayList<>();
        for (List<Map<String, Object>> items : meals.values()) {
            flat.addAll(items);
        }
        return flat;
    }
}
