package com.ptit.rc_system.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

@Service
public class WorkoutCollaborativeFilteringService {
    private static final int MIN_USER_RATINGS = 5;
    private static final int MIN_EXERCISE_RATERS = 2;

    private final JdbcTemplate jdbcTemplate;

    public WorkoutCollaborativeFilteringService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean canUseForUser(long userId) {
        Integer userRatings = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT exerciseid) FROM interaction_logs "
                + "WHERE userid = ? AND exerciseid IS NOT NULL AND rating IS NOT NULL",
            Integer.class,
            userId
        );
        Integer exercisesWithEnoughRaters = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ("
                + "SELECT exerciseid FROM interaction_logs "
                + "WHERE exerciseid IS NOT NULL AND rating IS NOT NULL "
                + "GROUP BY exerciseid HAVING COUNT(DISTINCT userid) >= ?"
                + ") rated_exercises",
            Integer.class,
            MIN_EXERCISE_RATERS
        );
        return userRatings != null && userRatings >= MIN_USER_RATINGS
            && exercisesWithEnoughRaters != null && exercisesWithEnoughRaters > 0;
    }

    public Map<Long, Double> predictScores(long targetUserId) {
        Map<Long, Map<Long, Double>> matrix = buildUserExerciseMatrix();
        Map<Long, Double> targetRatings = matrix.getOrDefault(targetUserId, Map.of());
        if (targetRatings.size() < MIN_USER_RATINGS) {
            return Map.of();
        }

        Map<Long, Double> weightedRatings = new HashMap<>();
        Map<Long, Double> similarityWeights = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Double>> entry : matrix.entrySet()) {
            if (entry.getKey() == targetUserId) continue;
            double similarity = cosineSimilarity(targetRatings, entry.getValue());
            if (similarity <= 0) continue;

            for (Map.Entry<Long, Double> rating : entry.getValue().entrySet()) {
                if (targetRatings.containsKey(rating.getKey())) continue;
                weightedRatings.merge(rating.getKey(), rating.getValue() * similarity, Double::sum);
                similarityWeights.merge(rating.getKey(), similarity, Double::sum);
            }
        }

        Map<Long, Double> predictions = new HashMap<>();
        for (Map.Entry<Long, Double> entry : weightedRatings.entrySet()) {
            double weight = similarityWeights.getOrDefault(entry.getKey(), 0.0);
            if (weight > 0) {
                predictions.put(entry.getKey(), entry.getValue() / weight);
            }
        }
        return predictions;
    }

    public Map<Long, Double> popularityScores() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT exerciseid AS exerciseid, COUNT(*) AS ratingcount, AVG(rating) AS averagerating "
                + "FROM interaction_logs WHERE exerciseid IS NOT NULL AND rating IS NOT NULL "
                + "GROUP BY exerciseid"
        );
        double maximum = rows.stream()
            .mapToDouble(row -> number(row.get("ratingcount")) * number(row.get("averagerating")))
            .max()
            .orElse(0.0);
        if (maximum <= 0) return Map.of();

        Map<Long, Double> scores = new HashMap<>();
        for (Map<String, Object> row : rows) {
            long exerciseId = ((Number) row.get("exerciseid")).longValue();
            double score = number(row.get("ratingcount")) * number(row.get("averagerating"));
            scores.put(exerciseId, score / maximum * 100.0);
        }
        return scores;
    }

    public Map<Long, Double> userHighRatings(long userId) {
        Map<Long, Double> ratings = new HashMap<>();
        jdbcTemplate.query(
            "SELECT exerciseid, AVG(rating) AS averagerating FROM interaction_logs "
                + "WHERE userid = ? AND exerciseid IS NOT NULL AND rating IS NOT NULL "
                + "GROUP BY exerciseid",
            (RowCallbackHandler) rs ->
                ratings.put(rs.getLong("exerciseid"), rs.getDouble("averagerating")),
            userId
        );
        return ratings;
    }

    private Map<Long, Map<Long, Double>> buildUserExerciseMatrix() {
        Map<Long, Map<Long, Double>> matrix = new HashMap<>();
        jdbcTemplate.query(
            "SELECT userid, exerciseid, AVG(rating) AS averagerating FROM interaction_logs "
                + "WHERE exerciseid IS NOT NULL AND rating IS NOT NULL "
                + "GROUP BY userid, exerciseid",
            (RowCallbackHandler) rs -> matrix
                .computeIfAbsent(rs.getLong("userid"), ignored -> new HashMap<>())
                .put(rs.getLong("exerciseid"), rs.getDouble("averagerating"))
        );
        return matrix;
    }

    private double cosineSimilarity(Map<Long, Double> first, Map<Long, Double> second) {
        Set<Long> commonExercises = new HashSet<>(first.keySet());
        commonExercises.retainAll(second.keySet());
        if (commonExercises.isEmpty()) return 0.0;

        double dotProduct = 0.0;
        for (Long exerciseId : commonExercises) {
            dotProduct += first.get(exerciseId) * second.get(exerciseId);
        }

        double firstNorm = 0.0;
        for (double rating : first.values()) {
            firstNorm += rating * rating;
        }

        double secondNorm = 0.0;
        for (double rating : second.values()) {
            secondNorm += rating * rating;
        }

        return firstNorm == 0 || secondNorm == 0
            ? 0.0
            : dotProduct / (Math.sqrt(firstNorm) * Math.sqrt(secondNorm));
    }

    private double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }
}
