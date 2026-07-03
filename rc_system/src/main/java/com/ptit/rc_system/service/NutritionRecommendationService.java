package com.ptit.rc_system.service;

import com.ptit.rc_system.dto.FoodPortion;
import com.ptit.rc_system.entity.Food;
import com.ptit.rc_system.entity.InteractionLog;
import com.ptit.rc_system.repository.FoodRepository;
import com.ptit.rc_system.repository.InteractionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import org.springframework.cache.annotation.Cacheable;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class NutritionRecommendationService {
    private static final Logger logger = LoggerFactory.getLogger(NutritionRecommendationService.class);

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
    private JdbcTemplate jdbcTemplate;

    // ===== TRIPLE HYBRID RECOMMENDATION =====
    public Map<String, Object> recommendTripleHybrid(Long userId, Double weight, Double height,
                                                     Integer age, String gender, Double activityLevel,
                                                     String healthGoal, boolean isTraditional, int topK) {
        logger.info("🔄 Triple Hybrid: CF (60%) + Content-Based (40%)");

        // 60% từ Hybrid CF (gọi Python service)
        List<Long> cfRecs = aiRecommendationClient.recommendHybrid(userId, topK * 2, 0.6);

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
        double targetCalories = nutritionService.caculateTargetCalories(tdee, healthGoal, gender);

        List<Food> allFoods = foodRepository.findAll();
        List<FoodPortion> cbRecs = nutritionService.generateLunchCombo(targetCalories, allFoods, isTraditional);

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

    // ===== SMART RECOMMENDATION ORCHESTRATION =====
    @Cacheable(value = "nutritionRecommendations", key = "#userId")
    public Map<String, Object> recommendSmart(Long userId, Double weight, Double height,
                                              Integer age, String gender, Double activityLevel,
                                              String healthGoal, boolean isTraditional, int topK) {
        logger.info("🔍 Smart Recommend called: userId={}, hasProfile={}",
                userId, weight != null && height != null);

        // ===== CASE 1: User Cũ + Full Profile (Tối ưu nhất) =====
        if (userId != null && weight != null && height != null && age != null && gender != null
                && activityLevel != null && healthGoal != null) {

            boolean userExists = interactionLogRepository.existsByUserId(userId);
            if (userExists) {
                logger.info("✅ CASE 1: Old User + Full Profile → Triple Hybrid (60% CF + 40% CB)");
                double bmr = nutritionService.calculateBMR(weight, height, age, gender);
                double tdee = nutritionService.calculateTDEE(bmr, activityLevel);
                double targetCalories = nutritionService.caculateTargetCalories(tdee, healthGoal, gender);
                
                Map<String, Object> responseMap = recommendTripleHybrid(userId, weight, height, age, gender,
                        activityLevel, healthGoal, isTraditional, topK);

                @SuppressWarnings("unchecked")
                List<Food> data = (List<Food>) responseMap.getOrDefault("data", List.of());

                Map<String, List<Map<String, Object>>> meals = buildDailyPlan(
                        data, foodRepository.findAll(), targetCalories);
                List<Map<String, Object>> flat = flattenMeals(meals);
                if (!flat.isEmpty()) {
                    Map<String, Object> payload = createResponse(flat, "TRIPLE_HYBRID_DAILY", topK);
                    payload.put("meals", meals);
                    return payload;
                }

                List<FoodPortion> fallback = nutritionService.generateLunchCombo(targetCalories, foodRepository.findAll(), isTraditional);
                List<Food> fallbackFoods = fallback.stream().map(FoodPortion::getFood).toList();
                Map<String, List<Map<String, Object>>> fallbackMeals = buildDailyPlan(
                        fallbackFoods, foodRepository.findAll(), targetCalories);
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
                List<Long> recs = aiRecommendationClient.recommendHybrid(userId, topK, 0.6);

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
            double targetCalories = nutritionService.caculateTargetCalories(tdee, healthGoal, gender);

            List<Food> allFoods = foodRepository.findAll();
            List<FoodPortion> result = nutritionService.generateLunchCombo(targetCalories, allFoods, isTraditional);
            List<Food> candidateFoods = result.stream().map(FoodPortion::getFood).toList();

            Map<String, List<Map<String, Object>>> meals = buildDailyPlan(
                    candidateFoods, allFoods, targetCalories);
            Map<String, Object> payload = createResponse(flattenMeals(meals), "CONTENT_BASED_DAILY", topK);
            payload.put("meals", meals);
            return payload;
        }

        // ===== CASE 4: User Mới + NO Profile (Popularity-Based) =====
        logger.info("✅ CASE 4: NEW User + NO Profile → Popularity-Based");
        List<Long> popularFoods = collaborativeFilteringService.getPopularFoods(topK);
        List<Food> popular = foodRepository.findAllById(popularFoods);
        Map<String, List<Map<String, Object>>> meals = buildDailyPlan(popular, foodRepository.findAll());
        Map<String, Object> payload = createResponse(flattenMeals(meals), "POPULARITY_DAILY", topK);
        payload.put("meals", meals);
        return payload;
    }

    // ===== ONBOARDING INITIAL RECOMMENDATION =====
    public Map<String, Object> onboardingInitialRecommendation(Double weight, Double height, Integer age,
                                                                String gender, Double activityLevel, String healthGoal,
                                                                boolean isTraditional, int topK) {
        logger.info("🆕 Onboarding: New user initial recommendation");

        if (weight != null && height != null && age != null && gender != null
                && activityLevel != null && healthGoal != null) {

            double bmr = nutritionService.calculateBMR(weight, height, age, gender);
            double tdee = nutritionService.calculateTDEE(bmr, activityLevel);
            double targetCalories = nutritionService.caculateTargetCalories(tdee, healthGoal, gender);

            List<Food> allFoods = foodRepository.findAll();
            List<FoodPortion> result = nutritionService.generateLunchCombo(targetCalories, allFoods, isTraditional);

            logger.info("✅ Onboarding recommendation: {} foods", result.size());
            return createResponse(result, "ONBOARDING_TDEE");
        }

        List<Long> popularFoods = collaborativeFilteringService.getPopularFoods(topK);
        return createResponse(foodRepository.findAllById(popularFoods), "ONBOARDING_POPULARITY", topK);
    }

    // ===== SUGGEST NEXT ACTION AFTER RATING =====
    public Map<String, Object> suggestAfterRating(Long userId, int topK) {
        logger.info("📊 Checking if user {} can use CF", userId);

        long ratingCount = interactionLogRepository.findByUserIdAndRatingIsNotNull(userId).size();
        logger.info("User {} has {} ratings", userId, ratingCount);

        if (ratingCount >= 3) {
            logger.info("✅ User {} has enough ratings → Switch to CF", userId);
            List<Long> recs = aiRecommendationClient.recommendHybrid(userId, topK, 0.6);

            if (recs.isEmpty()) {
                recs = collaborativeFilteringService.getPopularFoods(topK);
            }

            return createResponse(foodRepository.findAllById(recs), "CF_AFTER_RATING", topK);
        } else {
            logger.warn("⚠️ User {} has only {} ratings → Continue with popularity", userId, ratingCount);
            List<Long> popularFoods = collaborativeFilteringService.getPopularFoods(topK);
            return createResponse(foodRepository.findAllById(popularFoods), "POPULARITY_CONTINUE", topK);
        }
    }

    // ===== GET LATEST PROFILE FOR PLAN =====
    public Map<String, Object> loadLatestProfileForPlan(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT TOP 1 Weight AS weight, Height AS height, Age AS age, Gender AS gender, "
                        + "Activity_Level AS activityLevel, Health_goal AS healthGoal, "
                        + "Target_Calories AS targetCalories "
                        + "FROM Health_Profiles WHERE UserID = ? "
                        + "ORDER BY Last_updated DESC, ProfileID DESC",
                userId
        );
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    // ===== GENERATE MEALS FOR USER =====
    @SuppressWarnings("unchecked")
    public Map<String, List<Map<String, Object>>> generateMealsForUser(Long userId, Map<String, Object> profile) {
        Object response = recommendSmart(
                userId,
                numberOrNull(profile.get("weight")),
                numberOrNull(profile.get("height")),
                integerOrNull(profile.get("age")),
                stringOrNull(profile.get("gender")),
                numberOrNull(profile.get("activityLevel")),
                stringOrNull(profile.get("healthGoal")),
                true,
                9
        );
        Map<String, Object> responseMap = response instanceof Map ? (Map<String, Object>) response : Map.of();
        Object meals = responseMap.get("meals");
        if (meals instanceof Map<?, ?> mealMap) {
            Map<String, List<Map<String, Object>>> typedMeals = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mealMap.entrySet()) {
                typedMeals.put(String.valueOf(entry.getKey()), (List<Map<String, Object>>) entry.getValue());
            }
            return typedMeals;
        }
        return buildDailyPlan(foodRepository.findAll().stream().limit(9).toList(), foodRepository.findAll());
    }

    // ===== PLAN BUILDERS AND CALORIE BALANCE =====
    private Map<String, List<Map<String, Object>>> buildDailyPlan(List<Food> candidates, List<Food> fallbackFoods) {
        return buildDailyPlan(candidates, fallbackFoods, null);
    }

    private Map<String, List<Map<String, Object>>> buildDailyPlan(List<Food> candidates, List<Food> fallbackFoods,
                                                                  Double targetCalories) {
        Set<Long> usedIds = new HashSet<>();
        Map<String, List<Map<String, Object>>> meals = new LinkedHashMap<>();

        String mixedMealKey = pickRandomMixedMealKey();
        boolean isBreakfastTraditional = !"breakfast".equals(mixedMealKey);
        boolean isLunchTraditional = !"lunch".equals(mixedMealKey);
        boolean isDinnerTraditional = !"dinner".equals(mixedMealKey);

        List<Map<String, Object>> breakfast = buildMealPlan(
                "breakfast", isBreakfastTraditional, candidates, fallbackFoods, usedIds,
                calculateMealTargetCalories(targetCalories, 0.30));
        List<Map<String, Object>> lunch = buildMealPlan(
                "lunch", isLunchTraditional, candidates, fallbackFoods, usedIds,
                calculateMealTargetCalories(targetCalories, 0.40));
        List<Map<String, Object>> dinner = buildMealPlan(
                "dinner", isDinnerTraditional, candidates, fallbackFoods, usedIds,
                calculateMealTargetCalories(targetCalories, 0.30));

        meals.put("breakfast", breakfast);
        meals.put("lunch", lunch);
        meals.put("dinner", dinner);
        rebalanceDailyCalories(meals, targetCalories);
        return meals;
    }

    private List<Map<String, Object>> buildMealPlan(String mealKey, boolean isTraditional,
                                                    List<Food> candidates, List<Food> fallbackFoods,
                                                    Set<Long> usedIds, Double mealTargetCalories) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (isTraditional) {
            SelectedFood carb = pickFoodByCategory(
                    mealKey, "Món tinh bột", candidates, fallbackFoods, usedIds,
                    calculateCategoryTargetCalories(mealTargetCalories, 0.45), 2.0);
            SelectedFood protein = pickFoodByCategory(
                    mealKey, "Món đạm", candidates, fallbackFoods, usedIds,
                    calculateCategoryTargetCalories(mealTargetCalories, 0.35), 1.5);
            SelectedFood veg = pickFoodByCategory(
                    mealKey, "Món rau", candidates, fallbackFoods, usedIds,
                    calculateCategoryTargetCalories(mealTargetCalories, 0.20), 1.5);
            if (veg == null) {
                veg = pickFoodByCategory(
                        mealKey, "Món hoa quả", candidates, fallbackFoods, usedIds,
                        calculateCategoryTargetCalories(mealTargetCalories, 0.20), 1.5);
            }

            addIfPresent(result, carb, mealKey, usedIds);
            addIfPresent(result, protein, mealKey, usedIds);
            addIfPresent(result, veg, mealKey, usedIds);
        } else {
            SelectedFood mixed = pickFoodByCategory(
                    mealKey, "Món hỗn hợp", candidates, fallbackFoods, usedIds,
                    calculateCategoryTargetCalories(mealTargetCalories, 0.85), 2.0);
            SelectedFood water = pickFoodByCategory(
                    mealKey, "Món nước", candidates, fallbackFoods, usedIds,
                    calculateCategoryTargetCalories(mealTargetCalories, 0.15), 2.0);

            addIfPresent(result, mixed, mealKey, usedIds);
            addIfPresent(result, water, mealKey, usedIds);
        }

        return result;
    }

    private SelectedFood pickFoodByCategory(String mealKey, String category,
                                            List<Food> primary, List<Food> fallback,
                                            Set<Long> usedIds, Double targetCalories,
                                            double maxPortionMultiplier) {
        SelectedFood picked = findBestMatching(
                mealKey, category, primary, usedIds, targetCalories, maxPortionMultiplier);
        if (picked != null) return picked;
        return findBestMatching(
                mealKey, category, fallback, usedIds, targetCalories, maxPortionMultiplier);
    }

    private SelectedFood findBestMatching(String mealKey, String category, List<Food> foods,
                                          Set<Long> usedIds, Double targetCalories,
                                          double maxPortionMultiplier) {
        SelectedFood bestMatch = null;
        double bestDifference = Double.MAX_VALUE;
        for (Food food : foods) {
            if (food == null || usedIds.contains(food.getId())) continue;
            if (category != null && (food.getCategory() == null || !food.getCategory().equalsIgnoreCase(category))) {
                continue;
            }
            if (!matchesMealType(food, mealKey)) {
                continue;
            }
            if (food.getCalories() == null || food.getCalories() <= 0) {
                continue;
            }

            double portionMultiplier = targetCalories == null
                    ? 1.0
                    : roundToHalf(targetCalories / food.getCalories());
            if (portionMultiplier < 0.5) {
                continue;
            }
            if (portionMultiplier > maxPortionMultiplier) {
                portionMultiplier = maxPortionMultiplier;
            }

            double adjustedCalories = food.getCalories() * portionMultiplier;
            double difference = targetCalories == null
                    ? 0.0
                    : Math.abs(targetCalories - adjustedCalories);
            if (difference < bestDifference) {
                bestMatch = new SelectedFood(food, portionMultiplier, roundToOneDecimal(adjustedCalories));
                bestDifference = difference;
            }
        }
        return bestMatch;
    }

    private void rebalanceDailyCalories(Map<String, List<Map<String, Object>>> meals, Double targetCalories) {
        if (targetCalories == null) return;

        List<Map<String, Object>> foods = flattenMeals(meals);
        double totalCalories = calculateTotalCalories(foods);

        while (true) {
            PortionAdjustment bestAdjustment = null;
            double currentDifference = Math.abs(targetCalories - totalCalories);

            for (Map<String, Object> food : foods) {
                double currentPortion = ((Number) food.get("portionMultiplier")).doubleValue();
                double baseCalories = ((Number) food.get("baseCalories")).doubleValue();
                double maxPortion = maxPortionMultiplierForCategory((String) food.get("category"));

                for (double nextPortion : new double[]{currentPortion - 0.5, currentPortion + 0.5}) {
                    if (nextPortion < 0.5 || nextPortion > maxPortion) continue;

                    double nextCalories = roundToOneDecimal(baseCalories * nextPortion);
                    double nextTotal = totalCalories - ((Number) food.get("calories")).doubleValue() + nextCalories;
                    double nextDifference = Math.abs(targetCalories - nextTotal);
                    if (nextDifference < currentDifference
                            && (bestAdjustment == null || nextDifference < bestAdjustment.difference())) {
                        bestAdjustment = new PortionAdjustment(food, nextPortion, nextCalories, nextTotal, nextDifference);
                    }
                }
            }

            if (bestAdjustment == null) return;
            bestAdjustment.food().put("portionMultiplier", bestAdjustment.portionMultiplier());
            bestAdjustment.food().put("calories", bestAdjustment.calories());
            totalCalories = bestAdjustment.totalCalories();
        }
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

    private Double calculateMealTargetCalories(Double targetCalories, double ratio) {
        return targetCalories == null ? null : targetCalories * ratio;
    }

    private Double calculateCategoryTargetCalories(Double mealTargetCalories, double ratio) {
        return mealTargetCalories == null ? null : mealTargetCalories * ratio;
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double roundToHalf(double value) {
        return Math.round(value * 2.0) / 2.0;
    }

    private double calculateTotalCalories(List<Map<String, Object>> foods) {
        return foods.stream()
                .mapToDouble(food -> ((Number) food.get("calories")).doubleValue())
                .sum();
    }

    private double maxPortionMultiplierForCategory(String category) {
        if ("Món đạm".equalsIgnoreCase(category)
                || "Món rau".equalsIgnoreCase(category)
                || "Món hoa quả".equalsIgnoreCase(category)) {
            return 1.5;
        }
        return 2.0;
    }

    private String pickRandomMixedMealKey() {
        String[] meals = {"breakfast", "lunch", "dinner"};
        return meals[new Random().nextInt(meals.length)];
    }

    private boolean matchesMealType(Food food, String mealKey) {
        if (food == null || food.getMealType() == null) return false;
        return food.getMealType().toLowerCase().contains(mealKey.toLowerCase());
    }

    private void addIfPresent(List<Map<String, Object>> target, SelectedFood selectedFood,
                              String mealKey, Set<Long> usedIds) {
        if (selectedFood == null) return;
        usedIds.add(selectedFood.food().getId());
        target.add(toMealFoodPayload(selectedFood, mealKey));
    }

    private Map<String, Object> toMealFoodPayload(SelectedFood selectedFood, String mealKey) {
        Food food = selectedFood.food();
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", food.getId());
        payload.put("name", food.getName());
        payload.put("category", food.getCategory());
        payload.put("baseCalories", food.getCalories());
        payload.put("portionMultiplier", selectedFood.portionMultiplier());
        payload.put("calories", selectedFood.adjustedCalories());
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

    private Double numberOrNull(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private java.lang.Integer integerOrNull(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record SelectedFood(Food food, double portionMultiplier, double adjustedCalories) {}

    private record PortionAdjustment(Map<String, Object> food, double portionMultiplier, double calories,
                                     double totalCalories, double difference) {}
}
