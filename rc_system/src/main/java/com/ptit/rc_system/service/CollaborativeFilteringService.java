package com.ptit.rc_system.service;

import org.springframework.stereotype.Service;
import com.ptit.rc_system.entity.InteractionLog;
import com.ptit.rc_system.repository.InteractionLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;

@Service
public class CollaborativeFilteringService {

    @Autowired
    private InteractionLogRepository logRepository;

    // 1. Nhóm dữ liệu thành Ma Trận: User -> (Food -> Rating)
    public Map<Long, Map<Long, Double>> buildUserItemMatrix() {
        List<InteractionLog> logs = logRepository.findAllByFoodIdIsNotNull();
        Map<Long, Map<Long, Double>> matrix = new HashMap<>();

        for (InteractionLog log : logs) {
            matrix.putIfAbsent(log.getUserId(), new HashMap<>());
            matrix.get(log.getUserId()).put(log.getFoodId(), log.getRating());
        }
        return matrix;
    }

    // 2. Tính độ tương đồng (Cosine Similarity) giữa 2 User
    public double calculateSimilarity(Map<Long, Double> user1Ratings, Map<Long, Double> user2Ratings) {
        Set<Long> commonFoods = new HashSet<>(user1Ratings.keySet());
        commonFoods.retainAll(user2Ratings.keySet());

        if (commonFoods.isEmpty()) return 0.0;

        double dotProduct = 0.0, norm1 = 0.0, norm2 = 0.0;
        for (Long foodId : commonFoods) {
            dotProduct += user1Ratings.get(foodId) * user2Ratings.get(foodId);
        }
        for (double rating : user1Ratings.values()) norm1 += Math.pow(rating, 2);
        for (double rating : user2Ratings.values()) norm2 += Math.pow(rating, 2);

        if (norm1 == 0 || norm2 == 0) return 0.0;
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    // 3. User-Based CF
    public List<Long> recommendFoodsForUser(Long targetUserId, int topK) {
        Map<Long, Map<Long, Double>> matrix = buildUserItemMatrix();
        Map<Long, Double> targetUserRatings = matrix.getOrDefault(targetUserId, new HashMap<>());

        Map<Long, Double> similarities = new HashMap<>();
        for (Long otherUserId : matrix.keySet()) {
            if (!otherUserId.equals(targetUserId)) {
                double sim = calculateSimilarity(targetUserRatings, matrix.get(otherUserId));
                if (sim > 0) similarities.put(otherUserId, sim);
            }
        }

        Map<Long, Double> predictedRatings = new HashMap<>();
        Map<Long, Double> similarityWeights = new HashMap<>();

        for (Map.Entry<Long, Double> simEntry : similarities.entrySet()) {
            Long otherUserId = simEntry.getKey();
            Double simScore = simEntry.getValue();
            Map<Long, Double> otherRatings = matrix.get(otherUserId);

            for (Map.Entry<Long, Double> foodEntry : otherRatings.entrySet()) {
                Long foodId = foodEntry.getKey();
                if (!targetUserRatings.containsKey(foodId)) {
                    predictedRatings.put(foodId,
                            predictedRatings.getOrDefault(foodId, 0.0) + foodEntry.getValue() * simScore);
                    similarityWeights.put(foodId,
                            similarityWeights.getOrDefault(foodId, 0.0) + simScore);
                }
            }
        }

        List<Map.Entry<Long, Double>> finalScores = new ArrayList<>();
        for (Long foodId : predictedRatings.keySet()) {
            double finalScore = predictedRatings.get(foodId) / similarityWeights.get(foodId);
            finalScores.add(new AbstractMap.SimpleEntry<>(foodId, finalScore));
        }

        finalScores.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        List<Long> recommendedFoodIds = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, finalScores.size()); i++) {
            recommendedFoodIds.add(finalScores.get(i).getKey());
        }

        return recommendedFoodIds;
    }

    // ✅ NEW: 4. Item-Based CF
    public List<Long> recommendFoodsByItemBased(Long userId, int topK) {
        Map<Long, Map<Long, Double>> matrix = buildUserItemMatrix();
        Map<Long, Double> userRatings = matrix.getOrDefault(userId, new HashMap<>());

        if (userRatings.isEmpty()) return new ArrayList<>();

        Map<Long, Double> scores = new HashMap<>();

        for (Map.Entry<Long, Double> entry : userRatings.entrySet()) {
            Long ratedFoodId = entry.getKey();
            Double ratingScore = entry.getValue();

            if (ratingScore < 3.0) continue;

            Set<Long> allFoodIds = new HashSet<>();
            for (Map.Entry<Long, Map<Long, Double>> userEntry : matrix.entrySet()) {
                allFoodIds.addAll(userEntry.getValue().keySet());
            }

            for (Long foodId : allFoodIds) {
                if (!foodId.equals(ratedFoodId) && !userRatings.containsKey(foodId)) {
                    Map<Long, Double> food1Users = new HashMap<>();
                    Map<Long, Double> food2Users = new HashMap<>();

                    for (Map.Entry<Long, Map<Long, Double>> userEntry : matrix.entrySet()) {
                        Map<Long, Double> foods = userEntry.getValue();
                        if (foods.containsKey(ratedFoodId))
                            food1Users.put(userEntry.getKey(), foods.get(ratedFoodId));
                        if (foods.containsKey(foodId))
                            food2Users.put(userEntry.getKey(), foods.get(foodId));
                    }

                    double similarity = calculateSimilarity(food1Users, food2Users);
                    scores.put(foodId, scores.getOrDefault(foodId, 0.0) + similarity * ratingScore);
                }
            }
        }

        return scores.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(topK)
                .map(Map.Entry::getKey)
                .toList();
    }

    // ✅ NEW: 5. Hybrid CF (User-Based + Item-Based)
    public List<Long> recommendFoodsHybrid(Long userId, int topK, double userWeight) {
        List<Long> userBasedRecs = recommendFoodsForUser(userId, topK * 2);
        List<Long> itemBasedRecs = recommendFoodsByItemBased(userId, topK * 2);

        Map<Long, Double> combinedScores = new HashMap<>();

        for (int i = 0; i < userBasedRecs.size(); i++) {
            double score = (topK - i) * userWeight;
            combinedScores.put(userBasedRecs.get(i),
                    combinedScores.getOrDefault(userBasedRecs.get(i), 0.0) + score);
        }

        for (int i = 0; i < itemBasedRecs.size(); i++) {
            double score = (topK - i) * (1 - userWeight);
            combinedScores.put(itemBasedRecs.get(i),
                    combinedScores.getOrDefault(itemBasedRecs.get(i), 0.0) + score);
        }

        return combinedScores.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(topK)
                .map(Map.Entry::getKey)
                .toList();
    }

    // ✅ NEW: 6. Fallback khi user đã đánh giá tất cả
    public List<Long> getTopRatedFoodsByUser(Long userId, int topK) {
        Map<Long, Map<Long, Double>> matrix = buildUserItemMatrix();
        Map<Long, Double> targetUserRatings = matrix.getOrDefault(userId, new HashMap<>());

        if (targetUserRatings.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, Double> similarities = new HashMap<>();
        for (Long otherUserId : matrix.keySet()) {
            if (!otherUserId.equals(userId)) {
                double sim = calculateSimilarity(targetUserRatings, matrix.get(otherUserId));
                if (sim > 0) similarities.put(otherUserId, sim);
            }
        }

        Map<Long, Double> scores = new HashMap<>();
        for (Map.Entry<Long, Double> simEntry : similarities.entrySet()) {
            Map<Long, Double> otherRatings = matrix.get(simEntry.getKey());
            for (Map.Entry<Long, Double> rating : otherRatings.entrySet()) {
                Long foodId = rating.getKey();
                if (!targetUserRatings.containsKey(foodId)) {
                    scores.put(foodId,
                            scores.getOrDefault(foodId, 0.0) + rating.getValue() * simEntry.getValue());
                }
            }
        }

        return scores.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(topK)
                .map(Map.Entry::getKey)
                .toList();
    }

    // ===== ✅ NEW: Popularity-Based Recommendation =====
    public List<Long> getPopularFoods(int topK) {
        List<InteractionLog> allLogs = logRepository.findAllByFoodIdIsNotNull();

        if (allLogs.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, Integer> foodCount = new HashMap<>();
        Map<Long, Double> foodAvgRating = new HashMap<>();

        for (InteractionLog log : allLogs) {
            Long foodId = log.getFoodId();
            foodCount.put(foodId, foodCount.getOrDefault(foodId, 0) + 1);
            foodAvgRating.put(foodId,
                    foodAvgRating.getOrDefault(foodId, 0.0) + log.getRating());
        }

        // Calculate average rating
        foodAvgRating.forEach((foodId, totalRating) ->
                foodAvgRating.put(foodId, totalRating / foodCount.get(foodId)));

        // Sort by popularity score (count * avg_rating)
        return foodCount.keySet().stream()
                .sorted((a, b) -> {
                    double scoreA = foodCount.get(a) * foodAvgRating.get(a);
                    double scoreB = foodCount.get(b) * foodAvgRating.get(b);
                    return Double.compare(scoreB, scoreA);
                })
                .limit(topK)
                .collect(java.util.stream.Collectors.toList());
    }
}