package com.ptit.rc_system.service;

import com.ptit.rc_system.entity.DailyPlan;
import com.ptit.rc_system.entity.Food;
import com.ptit.rc_system.entity.InteractionLog;
import com.ptit.rc_system.entity.PlanDetail;
import com.ptit.rc_system.repository.DailyPlanRepository;
import com.ptit.rc_system.repository.FoodRepository;
import com.ptit.rc_system.repository.InteractionLogRepository;
import com.ptit.rc_system.repository.PlanDetailRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NutritionPlanService {
    private static final String FOOD_ITEM_TYPE = "FOOD";

    private final DailyPlanRepository dailyPlanRepository;
    private final PlanDetailRepository planDetailRepository;
    private final FoodRepository foodRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final JdbcTemplate jdbcTemplate;

    public NutritionPlanService(DailyPlanRepository dailyPlanRepository,
                                PlanDetailRepository planDetailRepository,
                                FoodRepository foodRepository,
                                InteractionLogRepository interactionLogRepository,
                                JdbcTemplate jdbcTemplate) {
        this.dailyPlanRepository = dailyPlanRepository;
        this.planDetailRepository = planDetailRepository;
        this.foodRepository = foodRepository;
        this.interactionLogRepository = interactionLogRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> findExistingTodayPlan(Long userId) {
        DailyPlan dailyPlan = findTodayPlan(userId).orElse(null);
        if (dailyPlan == null || planDetailRepository.countByDailyPlanPlanIdAndItemType(
                dailyPlan.getPlanId(), FOOD_ITEM_TYPE) == 0) {
            return Optional.empty();
        }
        return Optional.of(nutritionPlanResponse(dailyPlan.getPlanId(), false));
    }

    @Transactional(readOnly = true)
    public boolean hasCompletedTodayPlan(Long userId) {
        return findTodayPlan(userId)
                .map(plan -> planDetailRepository.existsByDailyPlanPlanIdAndItemTypeAndCompletedTrue(
                        plan.getPlanId(), FOOD_ITEM_TYPE))
                .orElse(false);
    }

    @Transactional
    public Map<String, Object> replaceTodayPlan(Long userId, boolean regenerate, Double targetCalories,
                                                Map<String, List<Map<String, Object>>> meals) {
        DailyPlan dailyPlan = findOrCreateDailyPlan(userId);
        if (targetCalories != null) {
            dailyPlan.setTotalTargetCalories(targetCalories);
            dailyPlan = dailyPlanRepository.save(dailyPlan);
        }
        if (regenerate || planDetailRepository.countByDailyPlanPlanIdAndItemType(
                dailyPlan.getPlanId(), FOOD_ITEM_TYPE) > 0) {
            planDetailRepository.deleteByDailyPlanPlanIdAndItemType(dailyPlan.getPlanId(), FOOD_ITEM_TYPE);
        }

        int sortOrder = 1;
        for (Map.Entry<String, List<Map<String, Object>>> meal : meals.entrySet()) {
            for (Map<String, Object> foodPayload : meal.getValue()) {
                PlanDetail detail = new PlanDetail();
                detail.setDailyPlan(dailyPlan);
                detail.setItemType(FOOD_ITEM_TYPE);
                detail.setFoodId(Math.toIntExact(((Number) foodPayload.get("id")).longValue()));
                detail.setQuantity(numberOrDefault(foodPayload.get("portionMultiplier"), 1.0));
                detail.setCompleted(false);
                detail.setSortOrder(sortOrder++);
                detail.setWorkoutPhase((String) foodPayload.getOrDefault("mealType", capitalizeMeal(meal.getKey())));
                planDetailRepository.save(detail);
            }
        }
        return nutritionPlanResponse(dailyPlan.getPlanId(), true);
    }

    @Transactional
    public Map<String, Object> ratePlanItem(FoodRatingCommand command) {
        if (command.rating() == null || command.rating() < 0 || command.rating() > 5) {
            throw new IllegalArgumentException("Rating must be between 0-5");
        }
        PlanDetail detail = findOwnedFoodDetail(command.userId(), command.detailId(), command.foodId());
        if (detail.getRating() != null) {
            throw new IllegalArgumentException("Món ăn này đã được chấm điểm.");
        }

        Food food = foodRepository.findById(detail.getFoodId().longValue())
                .orElseThrow(() -> new IllegalArgumentException("Food not found"));
        LocalDateTime now = LocalDateTime.now();
        detail.setCompleted(true);
        detail.setRating(command.rating());
        detail.setCompletedAt(now);
        planDetailRepository.save(detail);

        InteractionLog saved = saveFoodSurveyLog(command.userId(), food, command.rating(),
                normalizeMealTime(detail.getWorkoutPhase()), now, detail.getDetailId());

        return Map.of(
                "logId", saved.getLogId(),
                "detailId", detail.getDetailId(),
                "foodId", food.getId(),
                "rating", command.rating(),
                "interactionType", saved.getInteractionType(),
                "isCompleted", true,
                "mealTime", saved.getMealTime(),
                "logDate", saved.getLogDate(),
                "message", "Rating saved successfully"
        );
    }

    @Transactional
    public Map<String, Object> saveFavoriteRatings(FavoriteFoodRatingCommand command) {
        if (command.rating() == null || command.rating() < 0 || command.rating() > 5) {
            throw new IllegalArgumentException("Rating must be between 0-5");
        }
        if (command.foodIds() == null || command.foodIds().isEmpty()) {
            throw new IllegalArgumentException("Cần chọn ít nhất một món ăn.");
        }

        Map<Long, Food> foodsById = foodRepository.findAllById(command.foodIds()).stream()
                .collect(Collectors.toMap(Food::getId, food -> food));
        LocalDateTime now = LocalDateTime.now();
        List<Long> savedLogIds = new ArrayList<>();
        for (Long foodId : command.foodIds()) {
            Food food = foodsById.get(foodId);
            if (food == null) {
                continue;
            }
            InteractionLog saved = saveFoodSurveyLog(
                    command.userId(), food, command.rating(), normalizeMealTime(food.getMealType()), now, null);
            savedLogIds.add(saved.getLogId());
        }

        return Map.of(
                "userId", command.userId(),
                "savedCount", savedLogIds.size(),
                "logIds", savedLogIds,
                "interactionType", "Food_Survey",
                "message", "Favorite ratings saved successfully"
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> history(Long userId, LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;

        List<Map<String, Object>> items = jdbcTemplate.query(
                "SELECT pd.DetailID, dp.UserID, pd.FoodID, f.Name, f.Calories, f.Category, f.Serving_Unit, "
                        + "pd.Rating, pd.Is_completed, pd.Workout_Phase AS Meal_Time, pd.Completed_At "
                        + "FROM Plan_Details pd "
                        + "JOIN Daily_Plans dp ON dp.PlanID = pd.PlanID "
                        + "JOIN Food_Library f ON f.FoodID = pd.FoodID "
                        + "WHERE dp.UserID = ? AND dp.PlanDate = ? "
                        + "AND pd.Item_type = 'FOOD' AND pd.Is_completed = 1 "
                        + "ORDER BY pd.Completed_At DESC, pd.Sort_Order",
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("detailId", rs.getLong("DetailID"));
                    item.put("userId", rs.getLong("UserID"));
                    item.put("foodId", rs.getLong("FoodID"));
                    item.put("name", rs.getString("Name"));
                    item.put("calories", rs.getDouble("Calories"));
                    item.put("category", rs.getString("Category"));
                    item.put("servingUnit", rs.getString("Serving_Unit"));
                    item.put("interactionType", "Food_Survey");
                    item.put("rating", rs.getObject("Rating"));
                    item.put("completed", rs.getBoolean("Is_completed"));
                    item.put("mealTime", rs.getString("Meal_Time"));
                    item.put("logDate", rs.getTimestamp("Completed_At"));
                    return item;
                },
                userId, targetDate
        );

        if (items.isEmpty()) {
            items = jdbcTemplate.query(
                    "SELECT l.LogID, l.UserID, l.FoodID, f.Name, f.Calories, f.Category, f.Serving_Unit, "
                            + "l.Interaction_type, l.Rating, l.Is_completed, l.Meal_Time, l.Log_date "
                            + "FROM Interaction_Logs l "
                            + "JOIN Food_Library f ON f.FoodID = l.FoodID "
                            + "WHERE l.UserID = ? AND l.FoodID IS NOT NULL "
                            + "AND CAST(l.Log_date AS date) = ? "
                            + "AND l.Interaction_type = 'Food_Survey' "
                            + "ORDER BY l.Log_date DESC",
                    (rs, rowNum) -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("logId", rs.getLong("LogID"));
                        item.put("userId", rs.getLong("UserID"));
                        item.put("foodId", rs.getLong("FoodID"));
                        item.put("name", rs.getString("Name"));
                        item.put("calories", rs.getDouble("Calories"));
                        item.put("category", rs.getString("Category"));
                        item.put("servingUnit", rs.getString("Serving_Unit"));
                        item.put("interactionType", rs.getString("Interaction_type"));
                        item.put("rating", rs.getObject("Rating"));
                        item.put("completed", rs.getBoolean("Is_completed"));
                        item.put("mealTime", rs.getString("Meal_Time"));
                        item.put("logDate", rs.getTimestamp("Log_date"));
                        return item;
                    },
                    userId, targetDate
            );
        }

        double totalCalories = items.stream()
                .mapToDouble(item -> ((Number) item.get("calories")).doubleValue())
                .sum();
        double averageRating = items.stream()
                .map(item -> item.get("rating"))
                .filter(Objects::nonNull)
                .mapToDouble(value -> ((Number) value).doubleValue())
                .average()
                .orElse(0.0);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("date", targetDate);
        response.put("completedCount", items.size());
        response.put("totalCalories", Math.round(totalCalories));
        response.put("averageRating", averageRating);
        response.put("data", items);
        return response;
    }

    private InteractionLog saveFoodSurveyLog(Long userId, Food food, Double rating, String mealTime,
                                             LocalDateTime logDate, Integer planDetailId) {
        InteractionLog log = new InteractionLog();
        log.setUserId(userId);
        log.setFoodId(food.getId());
        log.setInteractionType("Food_Survey");
        log.setRating(rating);
        log.setCompleted(true);
        log.setMealTime(mealTime);
        log.setLogDate(logDate);
        log.setPlanDetailId(planDetailId);
        return interactionLogRepository.save(log);
    }

    private DailyPlan findOrCreateDailyPlan(Long userId) {
        DailyPlan dailyPlan = findTodayPlan(userId)
                .orElseGet(() -> {
                    DailyPlan created = new DailyPlan();
                    created.setUserId(Math.toIntExact(userId));
                    created.setPlanDate(LocalDate.now());
                    return created;
                });
        return dailyPlanRepository.save(dailyPlan);
    }

    private Optional<DailyPlan> findTodayPlan(Long userId) {
        return dailyPlanRepository.findTopByUserIdAndPlanDateOrderByPlanIdDesc(
                Math.toIntExact(userId), LocalDate.now());
    }

    private Map<String, Object> nutritionPlanResponse(Integer planId, boolean created) {
        List<PlanDetail> details = planDetailRepository
                .findByDailyPlanPlanIdAndItemTypeOrderBySortOrder(planId, FOOD_ITEM_TYPE);
        Map<Long, Food> foodsById = foodRepository.findAllById(details.stream()
                        .map(PlanDetail::getFoodId)
                        .filter(Objects::nonNull)
                        .map(Integer::longValue)
                        .toList())
                .stream()
                .collect(Collectors.toMap(Food::getId, food -> food));

        Map<String, List<Map<String, Object>>> meals = new LinkedHashMap<>();
        meals.put("breakfast", new ArrayList<>());
        meals.put("lunch", new ArrayList<>());
        meals.put("dinner", new ArrayList<>());

        for (PlanDetail detail : details) {
            Food food = foodsById.get(detail.getFoodId().longValue());
            if (food == null) continue;
            Map<String, Object> payload = foodPlanItemPayload(detail, food);
            meals.computeIfAbsent(mealKey(detail.getWorkoutPhase()), ignored -> new ArrayList<>()).add(payload);
        }

        List<Map<String, Object>> flat = flattenMeals(meals);
        Map<String, Object> response = createResponse(
                flat, created ? "SQL_DAILY_FOOD_PLAN_CREATED" : "SQL_DAILY_FOOD_PLAN", flat.size());
        response.put("planId", planId);
        response.put("planDate", LocalDate.now());
        response.put("created", created);
        response.put("meals", meals);
        response.put("completedCount", flat.stream().filter(item -> Boolean.TRUE.equals(item.get("completed"))).count());
        response.put("totalCalories", Math.round(calculateTotalCalories(flat)));
        return response;
    }

    private Map<String, Object> foodPlanItemPayload(PlanDetail detail, Food food) {
        double portionMultiplier = detail.getQuantity() == null ? 1.0 : detail.getQuantity();
        double baseCalories = food.getCalories() == null ? 0.0 : food.getCalories();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("detailId", detail.getDetailId());
        payload.put("id", food.getId());
        payload.put("name", food.getName());
        payload.put("category", food.getCategory());
        payload.put("baseCalories", baseCalories);
        payload.put("portionMultiplier", portionMultiplier);
        payload.put("calories", roundToOneDecimal(baseCalories * portionMultiplier));
        payload.put("protein", food.getProtein());
        payload.put("carbs", food.getCarbs());
        payload.put("fat", food.getFat());
        payload.put("servingUnit", food.getServingUnit());
        payload.put("mealType", detail.getWorkoutPhase());
        payload.put("completed", Boolean.TRUE.equals(detail.getCompleted()));
        payload.put("rating", detail.getRating());
        payload.put("completedAt", detail.getCompletedAt());
        return payload;
    }

    private PlanDetail findOwnedFoodDetail(Long userId, Long detailId, Long foodId) {
        if (detailId != null) {
            return planDetailRepository.findOwnedFoodDetail(
                            Math.toIntExact(userId), LocalDate.now(), FOOD_ITEM_TYPE, Math.toIntExact(detailId))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Không tìm thấy món ăn trong thực đơn hôm nay."));
        }
        if (foodId != null) {
            return planDetailRepository.findOwnedFoodDetailsByFood(
                            Math.toIntExact(userId), LocalDate.now(), FOOD_ITEM_TYPE, Math.toIntExact(foodId))
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Không tìm thấy món ăn trong thực đơn hôm nay."));
        }
        throw new IllegalArgumentException("Thiếu detailId hoặc foodId.");
    }

    private String normalizeMealTime(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("sang") || normalized.contains("breakfast")) {
            return "Breakfast";
        }
        if (normalized.contains("trua") || normalized.contains("lunch")) {
            return "Lunch";
        }
        if (normalized.contains("toi") || normalized.contains("dinner")) {
            return "Dinner";
        }
        return value.trim();
    }

    private String mealKey(String mealTime) {
        String normalized = String.valueOf(mealTime).toLowerCase(Locale.ROOT);
        if (normalized.contains("breakfast") || normalized.contains("sang")) return "breakfast";
        if (normalized.contains("lunch") || normalized.contains("trua")) return "lunch";
        if (normalized.contains("dinner") || normalized.contains("toi")) return "dinner";
        return "other";
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

    private Map<String, Object> createResponse(List<?> items, String strategy, int topK) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", items);
        response.put("strategy", strategy);
        response.put("count", items.size());
        response.put("requested", topK);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    private double calculateTotalCalories(List<Map<String, Object>> foods) {
        return foods.stream()
                .mapToDouble(food -> ((Number) food.get("calories")).doubleValue())
                .sum();
    }

    private double numberOrDefault(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record FoodRatingCommand(Long userId, Long detailId, Long foodId, Double rating) {}

    public record FavoriteFoodRatingCommand(Long userId, List<Long> foodIds, Double rating) {}
}
