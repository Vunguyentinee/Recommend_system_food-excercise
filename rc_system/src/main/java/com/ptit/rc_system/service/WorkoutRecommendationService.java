package com.ptit.rc_system.service;

import com.ptit.rc_system.entity.DailyPlan;
import com.ptit.rc_system.entity.Exercise;
import com.ptit.rc_system.entity.HealthProfile;
import com.ptit.rc_system.entity.InteractionLog;
import com.ptit.rc_system.entity.PlanDetail;
import com.ptit.rc_system.repository.DailyPlanRepository;
import com.ptit.rc_system.repository.ExerciseRepository;
import com.ptit.rc_system.repository.HealthProfileRepository;
import com.ptit.rc_system.repository.InteractionLogRepository;
import com.ptit.rc_system.repository.PlanDetailRepository;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkoutRecommendationService {
    private static final String COOL_DOWN_NOTE = "Giãn cơ nhẹ 5-10 phút sau buổi tập.";
    private static final String EXERCISE_ITEM_TYPE = "EXERCISE";

    private final JdbcTemplate jdbcTemplate;
    private final WorkoutCollaborativeFilteringService collaborativeFilteringService;
    private final ExerciseRepository exerciseRepository;
    private final DailyPlanRepository dailyPlanRepository;
    private final PlanDetailRepository planDetailRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final HealthProfileRepository healthProfileRepository;

    public WorkoutRecommendationService(JdbcTemplate jdbcTemplate,
                                        WorkoutCollaborativeFilteringService collaborativeFilteringService,
                                        ExerciseRepository exerciseRepository,
                                        DailyPlanRepository dailyPlanRepository,
                                        PlanDetailRepository planDetailRepository,
                                        InteractionLogRepository interactionLogRepository,
                                        HealthProfileRepository healthProfileRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.collaborativeFilteringService = collaborativeFilteringService;
        this.exerciseRepository = exerciseRepository;
        this.dailyPlanRepository = dailyPlanRepository;
        this.planDetailRepository = planDetailRepository;
        this.interactionLogRepository = interactionLogRepository;
        this.healthProfileRepository = healthProfileRepository;
    }

    @Transactional
    @Caching(
        cacheable = {
            @Cacheable(value = "workoutPlans", key = "#userId", condition = "!#regenerate")
        },
        put = {
            @CachePut(value = "workoutPlans", key = "#userId", condition = "#regenerate")
        }
    )
    public Map<String, Object> getOrCreateTodayPlan(long userId, boolean regenerate) {
        Profile profile = findProfile(userId);
        GoalConfig goalConfig = goalConfig(profile.healthGoal());
        PlanConfig planConfig = planConfig(profile.fitnessLevel(), LocalDate.now());
        DailyPlan dailyPlan = findTodayPlan(userId);
        Set<Long> previousExerciseIds = dailyPlan == null || !regenerate
            ? Set.of()
            : findPlanExerciseIds(dailyPlan.getPlanId());

        if (dailyPlan != null && !regenerate && hasWorkoutDetails(dailyPlan.getPlanId())) {
            return planResponse(dailyPlan.getPlanId(), profile, goalConfig, planConfig, false);
        }

        if (dailyPlan == null) {
            dailyPlan = new DailyPlan();
            dailyPlan.setUserId(Math.toIntExact(userId));
            dailyPlan.setPlanDate(LocalDate.now());
        }
        dailyPlan.setWorkoutSplit(planConfig.split());
        dailyPlan.setWorkoutDaysPerWeek(planConfig.daysPerWeek());
        dailyPlan.setCardioNote(goalConfig.cardioNote());
        dailyPlan.setTotalTargetCalories(profile.targetCalories());
        dailyPlan = dailyPlanRepository.save(dailyPlan);

        List<WorkoutCandidate> selected = generateExercises(
            userId, profile, goalConfig, planConfig, previousExerciseIds
        );
        if (selected.size() < planConfig.exerciseCount()) {
            selected = generateExercises(userId, profile, goalConfig, planConfig, Set.of());
        }
        if (regenerate && hasWorkoutDetails(dailyPlan.getPlanId())) {
            if (planDetailRepository.existsByDailyPlanPlanIdAndItemTypeAndCompletedTrue(
                    dailyPlan.getPlanId(), EXERCISE_ITEM_TYPE)) {
                throw new IllegalArgumentException("Không thể đổi lịch sau khi đã hoàn thành bài tập.");
            }
            planDetailRepository.deleteByDailyPlanPlanIdAndItemType(dailyPlan.getPlanId(), EXERCISE_ITEM_TYPE);
        }
        int sortOrder = 1;
        for (WorkoutCandidate exercise : selected) {
            PlanDetail detail = new PlanDetail();
            detail.setDailyPlan(dailyPlan);
            detail.setItemType(EXERCISE_ITEM_TYPE);
            detail.setExercise(exerciseRepository.getReferenceById(Math.toIntExact(exercise.id())));
            detail.setQuantity((double) exercise.sets());
            detail.setCompleted(false);
            detail.setSets(exercise.sets());
            detail.setReps(exercise.reps());
            detail.setRestSeconds(exercise.restSeconds());
            detail.setSortOrder(sortOrder++);
            detail.setWorkoutPhase(exercise.phase());
            exercise.detailId = planDetailRepository.save(detail).getDetailId();
        }
        return planResponse(dailyPlan.getPlanId(), profile, goalConfig, planConfig, true);
    }

    @Transactional
    public Map<String, Object> complete(WorkoutCompletion command) {
        PlanDetail detail = findOwnedDetail(command.userId(), command.detailId(), command.exerciseId());
        if (Boolean.TRUE.equals(detail.getCompleted())) {
            return Map.of("message", "Bài tập đã được ghi nhận trước đó.", "detailId", detail.getDetailId());
        }

        int durationMinutes = command.durationMinutes() == null || command.durationMinutes() <= 0
            ? defaultDuration(detail.getExercise().getType(), detail.getWorkoutPhase())
            : Math.min(command.durationMinutes(), 180);
        double caloriesBurned = calculateCaloriesBurned(
            findProfile(command.userId()).weight(), detail.getExercise().getType(), durationMinutes
        );

        detail.setCompleted(true);
        detail.setDurationMinutes(durationMinutes);
        detail.setCaloriesBurned(caloriesBurned);
        detail.setCompletedAt(LocalDateTime.now());
        planDetailRepository.save(detail);

        InteractionLog log = new InteractionLog();
        log.setUserId(command.userId());
        log.setExerciseId(detail.getExercise().getExerciseId());
        log.setInteractionType("COMPLETED");
        log.setCompleted(true);
        log.setLogDate(LocalDateTime.now());
        log.setPlanDetailId(detail.getDetailId());
        log.setDurationMinutes(durationMinutes);
        log.setCaloriesBurned(caloriesBurned);
        Long logId = interactionLogRepository.save(log).getLogId();
        return Map.of(
            "message", "Đã ghi nhận bài tập hoàn thành.",
            "logId", logId,
            "detailId", detail.getDetailId(),
            "durationMinutes", durationMinutes,
            "caloriesBurned", round(caloriesBurned)
        );
    }

    @Transactional
    public Map<String, Object> rate(WorkoutRating command) {
        if (command.rating() == null || command.rating() < 1 || command.rating() > 5) {
            throw new IllegalArgumentException("Điểm đánh giá phải nằm trong khoảng 1-5.");
        }
        PlanDetail detail = findOwnedDetail(command.userId(), command.detailId(), null);
        if (!Boolean.TRUE.equals(detail.getCompleted())) {
            throw new IllegalArgumentException("Chỉ có thể đánh giá bài tập đã hoàn thành.");
        }
        if (detail.getRating() != null) {
            throw new IllegalArgumentException("Bài tập này đã được đánh giá.");
        }

        detail.setRating(command.rating());
        planDetailRepository.save(detail);

        InteractionLog log = new InteractionLog();
        log.setUserId(command.userId());
        log.setExerciseId(detail.getExercise().getExerciseId());
        log.setInteractionType("Excercise_Survey");
        log.setRating(command.rating());
        log.setCompleted(true);
        log.setLogDate(LocalDateTime.now());
        log.setPlanDetailId(detail.getDetailId());
        Long logId = interactionLogRepository.save(log).getLogId();
        return Map.of(
            "message", "Đã lưu đánh giá bài tập.",
            "logId", logId,
            "detailId", detail.getDetailId(),
            "rating", command.rating()
        );
    }

    public Map<String, Object> history(long userId, LocalDate date) {
        List<Map<String, Object>> completed = jdbcTemplate.query(
            "SELECT pd.DetailID, pd.ExerciseID, e.Name, e.Target_muscle, pd.Workout_Phase, "
                + "pd.Duration_Minutes, pd.Calories_Burned, pd.Completed_At, pd.Rating "
                + "FROM Plan_Details pd "
                + "JOIN Daily_Plans dp ON dp.PlanID = pd.PlanID "
                + "JOIN Exercise_Library e ON e.ExerciseID = pd.ExerciseID "
                + "WHERE dp.UserID = ? AND dp.PlanDate = ? AND pd.Item_type = 'EXERCISE' "
                + "AND pd.Is_completed = 1 ORDER BY pd.Completed_At, pd.Sort_Order",
            (rs, rowNum) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("detailId", rs.getLong("DetailID"));
                item.put("exerciseId", rs.getLong("ExerciseID"));
                item.put("name", rs.getString("Name"));
                item.put("targetMuscle", rs.getString("Target_muscle"));
                item.put("phase", rs.getString("Workout_Phase"));
                item.put("durationMinutes", nullableInteger(rs.getObject("Duration_Minutes")));
                item.put("caloriesBurned", round(nullableDouble(rs.getObject("Calories_Burned"))));
                item.put("completedAt", rs.getTimestamp("Completed_At"));
                item.put("rating", nullableDoubleObject(rs.getObject("Rating")));
                return item;
            },
            userId, date
        );

        LocalDate weekStart = date.minusDays(6);
        List<Map<String, Object>> weeklyRows = jdbcTemplate.queryForList(
            "SELECT pd.PlanID, pd.Duration_Minutes, pd.Calories_Burned, e.Target_muscle "
                + "FROM Plan_Details pd "
                + "JOIN Daily_Plans dp ON dp.PlanID = pd.PlanID "
                + "JOIN Exercise_Library e ON e.ExerciseID = pd.ExerciseID "
                + "WHERE dp.UserID = ? AND dp.PlanDate BETWEEN ? AND ? "
                + "AND pd.Item_type = 'EXERCISE' AND pd.Is_completed = 1",
            userId, weekStart, date
        );

        Set<Object> completedPlans = weeklyRows.stream()
            .map(row -> row.get("PlanID"))
            .collect(Collectors.toSet());
        Set<String> trainedMuscles = weeklyRows.stream()
            .map(row -> Objects.toString(row.get("Target_muscle"), ""))
            .filter(value -> !value.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("date", date);
        response.put("completedCount", completed.size());
        response.put("totalMinutes", completed.stream().mapToInt(item -> integer(item.get("durationMinutes"))).sum());
        response.put("totalCalories", round(completed.stream()
            .mapToDouble(item -> number(item.get("caloriesBurned"))).sum()));
        response.put("trainedMuscles", completed.stream()
            .map(item -> Objects.toString(item.get("targetMuscle"), ""))
            .filter(value -> !value.isBlank())
            .distinct()
            .toList());
        response.put("data", completed);
        response.put("weekly", Map.of(
            "from", weekStart,
            "to", date,
            "completedSessions", completedPlans.size(),
            "totalMinutes", weeklyRows.stream().mapToInt(row -> integer(row.get("Duration_Minutes"))).sum(),
            "totalCalories", round(weeklyRows.stream().mapToDouble(row -> number(row.get("Calories_Burned"))).sum()),
            "trainedMuscles", trainedMuscles
        ));
        return response;
    }

    private List<WorkoutCandidate> generateExercises(long userId, Profile profile, GoalConfig goalConfig,
                                                      PlanConfig planConfig, Set<Long> excludedExerciseIds) {
        List<WorkoutCandidate> eligible = loadExercises().stream()
            .filter(exercise -> levelRank(exercise.level()) <= levelRank(profile.fitnessLevel()))
            .toList();
        Map<Long, Double> highRatings = collaborativeFilteringService.userHighRatings(userId);
        boolean cfEnabled = collaborativeFilteringService.canUseForUser(userId);
        Map<Long, Double> cfScores = cfEnabled
            ? collaborativeFilteringService.predictScores(userId)
            : Map.of();
        Map<Long, Double> popularityScores = cfEnabled
            ? collaborativeFilteringService.popularityScores()
            : Map.of();

        List<WorkoutCandidate> selected = new ArrayList<>();
        Set<Long> usedIds = new HashSet<>(excludedExerciseIds);
        Map<String, Integer> muscleCounts = new HashMap<>();

        WorkoutCandidate warmup = bestCandidate(
            eligible.stream().filter(this::isCardio).toList(),
            usedIds, muscleCounts, profile, goalConfig, planConfig, highRatings, cfScores, popularityScores
        );
        if (warmup != null) {
            selected.add(prepareExercise(warmup, "KHỞI ĐỘNG", goalConfig, 5));
            usedIds.add(warmup.id());
        }

        int finalCardioSlots = isLoseGoal(profile.healthGoal()) ? 1 : 0;
        int strengthSlots = Math.max(1, planConfig.exerciseCount() - selected.size() - finalCardioSlots);
        for (int index = 0; index < strengthSlots; index++) {
            List<WorkoutCandidate> strengthCandidates = eligible.stream()
                .filter(exercise -> !isCardio(exercise))
                .filter(exercise -> matchesSplit(exercise, planConfig.split()))
                .toList();
            if ("FULL BODY".equals(planConfig.split()) && index < 2) {
                boolean requireUpperBody = index == 0;
                strengthCandidates = strengthCandidates.stream()
                    .filter(exercise -> requireUpperBody
                        ? isUpperBody(normalize(exercise.targetMuscle()))
                        : isLowerBody(normalize(exercise.targetMuscle())))
                    .toList();
            }
            WorkoutCandidate next = bestCandidate(
                strengthCandidates, usedIds, muscleCounts, profile, goalConfig, planConfig,
                highRatings, cfScores, popularityScores
            );
            if (next == null) {
                next = bestCandidate(
                    eligible.stream().filter(exercise -> !isCardio(exercise)).toList(),
                    usedIds, muscleCounts, profile, goalConfig, planConfig,
                    highRatings, cfScores, popularityScores
                );
            }
            if (next == null) break;

            String phase = index < Math.min(3, strengthSlots) ? "BÀI CHÍNH" : "BÀI PHỤ";
            selected.add(prepareExercise(next, phase, goalConfig, defaultDuration(next.type(), phase)));
            usedIds.add(next.id());
            muscleCounts.merge(normalize(next.targetMuscle()), 1, Integer::sum);
        }

        if (finalCardioSlots > 0) {
            WorkoutCandidate cardio = bestCandidate(
                eligible.stream().filter(this::isCardio).toList(),
                usedIds, muscleCounts, profile, goalConfig, planConfig,
                highRatings, cfScores, popularityScores
            );
            if (cardio != null) {
                selected.add(prepareExercise(cardio, "CARDIO", goalConfig, 20));
            }
        }
        return selected;
    }

    private WorkoutCandidate bestCandidate(List<WorkoutCandidate> candidates, Set<Long> usedIds,
                                           Map<String, Integer> muscleCounts, Profile profile,
                                           GoalConfig goalConfig, PlanConfig planConfig,
                                           Map<Long, Double> highRatings, Map<Long, Double> cfScores,
                                           Map<Long, Double> popularityScores) {
        return candidates.stream()
            .filter(exercise -> !usedIds.contains(exercise.id()))
            .map(exercise -> score(exercise, muscleCounts, profile, goalConfig, planConfig,
                highRatings, cfScores, popularityScores))
            .max(Comparator.comparingDouble(WorkoutCandidate::finalScore)
                .thenComparingLong(exercise -> -exercise.id()))
            .orElse(null);
    }

    private WorkoutCandidate score(WorkoutCandidate exercise, Map<String, Integer> muscleCounts, Profile profile,
                                   GoalConfig goalConfig, PlanConfig planConfig, Map<Long, Double> highRatings,
                                   Map<Long, Double> cfScores, Map<Long, Double> popularityScores) {
        double contentScore = 0;
        List<String> reasons = new ArrayList<>();
        if (normalizeLevel(exercise.level()).equals(normalizeLevel(profile.fitnessLevel()))) {
            contentScore += 40;
            reasons.add("+40 trình độ phù hợp");
        }
        if (goalMatches(profile.healthGoal(), exercise.goalTag())) {
            contentScore += 30;
            reasons.add("+30 mục tiêu phù hợp");
        } else if ("all".equals(normalize(exercise.goalTag()))) {
            contentScore += 10;
            reasons.add("+10 dùng được cho mọi mục tiêu");
        }
        if (muscleCounts.getOrDefault(normalize(exercise.targetMuscle()), 0) == 0) {
            contentScore += 20;
            reasons.add("+20 bổ sung nhóm cơ còn thiếu");
        } else {
            contentScore -= 100;
            reasons.add("-100 hạn chế lặp nhóm cơ");
        }
        if (highRatings.getOrDefault(exercise.id(), 0.0) >= 4.0) {
            contentScore += 10;
            reasons.add("+10 từng được đánh giá cao");
        }
        if (genderPriority(profile.gender(), exercise.targetMuscle())) {
            contentScore += 15;
            reasons.add("+15 ưu tiên nhóm cơ theo hồ sơ");
        }
        if (isLoseGoal(profile.healthGoal()) && isCardio(exercise)) {
            contentScore += 15;
            reasons.add("+15 cardio hỗ trợ giảm cân");
        }
        if (isGainGoal(profile.healthGoal()) && !isCardio(exercise)) {
            contentScore += 15;
            reasons.add("+15 strength hỗ trợ tăng cơ");
        }
        if (matchesSplit(exercise, planConfig.split())) {
            contentScore += 10;
            reasons.add("+10 phù hợp split hôm nay");
        }

        Double predictedRating = cfScores.get(exercise.id());
        double collaborativeScore = predictedRating == null ? 0.0 : predictedRating / 5.0 * 100.0;
        double popularityScore = popularityScores.getOrDefault(exercise.id(), 0.0);
        double finalScore;
        if (cfScores.isEmpty()) {
            finalScore = contentScore;
        } else if (popularityScores.size() >= 10) {
            finalScore = contentScore * 0.5 + collaborativeScore * 0.4 + popularityScore * 0.1;
        } else {
            finalScore = contentScore * 0.7 + collaborativeScore * 0.3;
        }
        return exercise.withScores(contentScore, collaborativeScore, popularityScore, finalScore, reasons);
    }

    private Map<String, Object> planResponse(int planId, Profile profile, GoalConfig goalConfig,
                                             PlanConfig planConfig, boolean created) {
        List<Map<String, Object>> items = planDetailRepository
            .findPlanItems(planId, EXERCISE_ITEM_TYPE)
            .stream()
            .map(detail -> {
                Exercise exercise = detail.getExercise();
                Map<String, Object> item = new LinkedHashMap<>();
                long exerciseId = exercise.getExerciseId();
                item.put("detailId", detail.getDetailId());
                item.put("id", exerciseId);
                item.put("name", exercise.getName());
                item.put("targetMuscle", exercise.getTargetMuscle());
                item.put("type", exercise.getType());
                item.put("level", exercise.getLevel());
                item.put("goalTag", exercise.getGoalTag());
                item.put("suitability", describeSuitability(profile.healthGoal(), exercise.getType()));
                item.put("equipment", exercise.getEquipment());
                item.put("sets", detail.getSets());
                item.put("reps", detail.getReps());
                item.put("restSeconds", detail.getRestSeconds());
                item.put("phase", detail.getWorkoutPhase());
                item.put("instructions", exercise.getInstructions());
                item.put("videoUrl", "/images/excercises/" + exerciseId + ".mp4");
                item.put("completed", Boolean.TRUE.equals(detail.getCompleted()));
                item.put("durationMinutes", detail.getDurationMinutes());
                item.put("caloriesBurned", round(detail.getCaloriesBurned() == null ? 0.0 : detail.getCaloriesBurned()));
                item.put("rating", detail.getRating());
                return item;
            })
            .toList();
        boolean cfEnabled = collaborativeFilteringService.canUseForUser(profile.userId());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("planId", planId);
        response.put("planDate", LocalDate.now());
        response.put("created", created);
        response.put("fitnessLevel", profile.fitnessLevel());
        response.put("healthGoal", profile.healthGoal());
        response.put("split", planConfig.split());
        response.put("workoutDaysPerWeek", planConfig.daysPerWeek());
        response.put("cardioNote", goalConfig.cardioNote());
        response.put("intensity", goalConfig.intensity());
        response.put("coolDownNote", COOL_DOWN_NOTE);
        response.put("strategy", cfEnabled ? "CONTENT_BASED_70_CF_30" : "CONTENT_BASED_FALLBACK");
        response.put("cfEnabled", cfEnabled);
        response.put("scoringRules", List.of(
            "+40 trình độ phù hợp",
            "+30 Goal_Tag khớp mục tiêu",
            "+20 bổ sung nhóm cơ còn thiếu",
            "+10 bài từng được đánh giá cao",
            "-100 hạn chế lặp nhóm cơ quá nhiều"
        ));
        response.put("completedCount", items.stream().filter(item -> Boolean.TRUE.equals(item.get("completed"))).count());
        response.put("count", items.size());
        response.put("data", items);
        return response;
    }

    private WorkoutCandidate prepareExercise(WorkoutCandidate exercise, String phase, GoalConfig config,
                                             int duration) {
        int sets = isCardio(exercise) ? 1 : config.sets();
        String reps = isCardio(exercise) ? duration + " phút" : config.reps();
        int rest = isCardio(exercise) ? 0 : config.restSeconds();
        return exercise.withPrescription(sets, reps, rest, phase);
    }

    private List<WorkoutCandidate> loadExercises() {
        return exerciseRepository.findAllByOrderByExerciseIdAsc().stream()
            .map(exercise -> new WorkoutCandidate(
                exercise.getExerciseId(),
                exercise.getName(),
                exercise.getTargetMuscle(),
                exercise.getType(),
                exercise.getLevel(),
                exercise.getGoalTag(),
                exercise.getEquipment(),
                exercise.getInstructions()
            ))
            .toList();
    }

    private PlanDetail findOwnedDetail(long userId, Long detailId, Long exerciseId) {
        if (detailId == null && exerciseId == null) {
            throw new IllegalArgumentException("Thiếu detailId của bài tập.");
        }
        if (detailId != null) {
            return planDetailRepository.findOwnedDetail(
                    Math.toIntExact(userId), LocalDate.now(), EXERCISE_ITEM_TYPE, Math.toIntExact(detailId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài tập trong lịch hôm nay."));
        }
        return planDetailRepository.findOwnedDetailsByExercise(
                Math.toIntExact(userId), LocalDate.now(), EXERCISE_ITEM_TYPE, Math.toIntExact(exerciseId))
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài tập trong lịch hôm nay."));
    }

    private Profile findProfile(long userId) {
        HealthProfile healthProfile = healthProfileRepository
            .findTopByUserIdOrderByLastUpdatedDescProfileIdDesc(userId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Hãy cập nhật hồ sơ sức khỏe trước khi tạo lịch tập."
            ));
        return new Profile(
            healthProfile.getUserId(),
            healthProfile.getGender(),
            healthProfile.getHealthGoal(),
            normalizeLevel(healthProfile.getFitnessLevel()),
            healthProfile.getWeight() == null ? 0.0 : healthProfile.getWeight(),
            healthProfile.getTargetCalories()
        );
    }

    private DailyPlan findTodayPlan(long userId) {
        return dailyPlanRepository.findTopByUserIdAndPlanDateOrderByPlanIdDesc(Math.toIntExact(userId), LocalDate.now())
            .orElse(null);
    }

    private boolean hasWorkoutDetails(int planId) {
        return planDetailRepository.countByDailyPlanPlanIdAndItemType(planId, EXERCISE_ITEM_TYPE) > 0;
    }

    private Set<Long> findPlanExerciseIds(int planId) {
        return planDetailRepository.findExerciseIds(planId, EXERCISE_ITEM_TYPE)
            .stream()
            .map(Integer::longValue)
            .collect(Collectors.toSet());
    }

    private PlanConfig planConfig(String fitnessLevel, LocalDate date) {
        return switch (normalizeLevel(fitnessLevel)) {
            case "Advanced" -> new PlanConfig(5, advancedSplit(date), 8);
            case "Intermediate" -> new PlanConfig(4, date.getDayOfMonth() % 2 == 0 ? "UPPER" : "LOWER", 7);
            default -> new PlanConfig(3, "FULL BODY", 6);
        };
    }

    private String advancedSplit(LocalDate date) {
        return switch (date.getDayOfMonth() % 3) {
            case 0 -> "PUSH";
            case 1 -> "PULL";
            default -> "LEGS";
        };
    }

    private GoalConfig goalConfig(String healthGoal) {
        if (isGainGoal(healthGoal)) {
            return new GoalConfig(4, "8-12", 75, "Nặng vừa (70-80% sức tối đa)",
                "1-2 buổi/tuần, tập nhẹ (LISS) 15-20 phút để bảo vệ tim.");
        }
        if (isLoseGoal(healthGoal)) {
            return new GoalConfig(4, "12-15+", 45, "Vừa phải (50-65% sức tối đa)",
                "3-5 buổi/tuần, kết hợp HIIT 20 phút hoặc đi bộ nhanh 45 phút.");
        }
        return new GoalConfig(3, "10-15", 60, "Vừa phải (60-70% sức tối đa)",
            "2-3 buổi/tuần, chạy bộ hoặc đạp xe 30 phút.");
    }

    private boolean matchesSplit(WorkoutCandidate exercise, String split) {
        if (isCardio(exercise) || "full body".equals(normalize(split))) return true;
        String muscle = normalize(exercise.targetMuscle());
        return switch (normalize(split)) {
            case "upper" -> isUpperBody(muscle);
            case "lower", "legs" -> isLowerBody(muscle);
            case "push" -> containsAny(muscle, "nguc", "vai", "tay sau");
            case "pull" -> containsAny(muscle, "lung", "tay truoc", "cang tay");
            default -> true;
        };
    }

    private boolean genderPriority(String gender, String targetMuscle) {
        String normalizedGender = normalize(gender);
        String muscle = normalize(targetMuscle);
        if (normalizedGender.contains("female") || normalizedGender.contains("nu")) {
            return isLowerBody(muscle) || muscle.contains("bung");
        }
        if (normalizedGender.contains("male") || normalizedGender.contains("nam")) {
            return isUpperBody(muscle);
        }
        return false;
    }

    private boolean isUpperBody(String muscle) {
        return containsAny(muscle, "nguc", "vai", "lung", "tay", "cang tay");
    }

    private boolean isLowerBody(String muscle) {
        return containsAny(muscle, "mong", "dui", "bap chan", "chan");
    }

    private boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) return true;
        }
        return false;
    }

    private boolean goalMatches(String healthGoal, String goalTag) {
        String normalizedGoal = normalize(healthGoal);
        String normalizedTag = normalize(goalTag);
        return !normalizedGoal.isBlank() && !normalizedTag.equals("all")
            && (normalizedTag.contains(normalizedGoal) || normalizedGoal.contains(normalizedTag));
    }

    private boolean isCardio(WorkoutCandidate exercise) {
        return normalize(exercise.type()).contains("cardio");
    }

    private boolean isGainGoal(String healthGoal) {
        return containsAny(normalize(healthGoal), "gain", "tang");
    }

    private boolean isLoseGoal(String healthGoal) {
        return containsAny(normalize(healthGoal), "lose", "giam");
    }

    private String describeSuitability(String healthGoal, String type) {
        boolean cardio = normalize(type).contains("cardio");
        if (isGainGoal(healthGoal)) {
            return cardio ? "Khởi động hoặc cardio nhẹ để bảo vệ tim" : "Ưu tiên strength để hỗ trợ tăng cơ";
        }
        if (isLoseGoal(healthGoal)) {
            return cardio ? "Ưu tiên cardio để tăng tiêu hao năng lượng" : "Strength giúp duy trì cơ khi giảm cân";
        }
        return cardio ? "Cardio hỗ trợ duy trì thể lực" : "Strength hỗ trợ duy trì vóc dáng";
    }

    private String normalizeLevel(String level) {
        if ("Advanced".equalsIgnoreCase(level)) return "Advanced";
        if ("Intermediate".equalsIgnoreCase(level)) return "Intermediate";
        return "Beginner";
    }

    private int levelRank(String level) {
        return switch (normalizeLevel(level)) {
            case "Advanced" -> 3;
            case "Intermediate" -> 2;
            default -> 1;
        };
    }

    private String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
        return normalized.replace('đ', 'd').trim();
    }

    private int defaultDuration(String type, String phase) {
        if (normalize(type).contains("cardio")) return "CARDIO".equals(phase) ? 20 : 5;
        return 8;
    }

    private double calculateCaloriesBurned(double weight, String type, int durationMinutes) {
        double safeWeight = weight > 0 ? weight : 60.0;
        double met = normalize(type).contains("cardio") ? 6.0 : 4.5;
        return met * 3.5 * safeWeight / 200.0 * durationMinutes;
    }

    private double nullableDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private Double nullableDoubleObject(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private Integer nullableInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private int integer(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record WorkoutCompletion(long userId, Long detailId, Long exerciseId, Integer durationMinutes) {}

    public record WorkoutRating(long userId, Long detailId, Double rating) {}

    private record Profile(long userId, String gender, String healthGoal, String fitnessLevel,
                           double weight, Double targetCalories) {}

    private record PlanConfig(int daysPerWeek, String split, int exerciseCount) {}

    private record GoalConfig(int sets, String reps, int restSeconds, String intensity, String cardioNote) {}

    private static final class WorkoutCandidate {
        private final long id;
        private final String name;
        private final String targetMuscle;
        private final String type;
        private final String level;
        private final String goalTag;
        private final String equipment;
        private final String instructions;
        private int sets;
        private String reps;
        private int restSeconds;
        private String phase;
        private double contentScore;
        private double collaborativeScore;
        private double popularityScore;
        private double finalScore;
        private List<String> scoreReasons = List.of();
        private Integer detailId;

        private WorkoutCandidate(long id, String name, String targetMuscle, String type, String level,
                                 String goalTag, String equipment, String instructions) {
            this.id = id;
            this.name = name;
            this.targetMuscle = targetMuscle;
            this.type = type;
            this.level = level;
            this.goalTag = goalTag;
            this.equipment = equipment;
            this.instructions = instructions;
        }

        private WorkoutCandidate withScores(double contentScore, double collaborativeScore,
                                            double popularityScore, double finalScore,
                                            List<String> scoreReasons) {
            this.contentScore = contentScore;
            this.collaborativeScore = collaborativeScore;
            this.popularityScore = popularityScore;
            this.finalScore = finalScore;
            this.scoreReasons = List.copyOf(scoreReasons);
            return this;
        }

        private WorkoutCandidate withPrescription(int sets, String reps, int restSeconds, String phase) {
            this.sets = sets;
            this.reps = reps;
            this.restSeconds = restSeconds;
            this.phase = phase;
            return this;
        }

        private long id() { return id; }
        private String name() { return name; }
        private String targetMuscle() { return targetMuscle; }
        private String type() { return type; }
        private String level() { return level; }
        private String goalTag() { return goalTag; }
        private String equipment() { return equipment; }
        private String instructions() { return instructions; }
        private int sets() { return sets; }
        private String reps() { return reps; }
        private int restSeconds() { return restSeconds; }
        private String phase() { return phase; }
        private double finalScore() { return finalScore; }
    }
}
