package com.ptit.rc_system.controller;

import com.ptit.rc_system.entity.HealthProfile;
import com.ptit.rc_system.repository.HealthProfileRepository;
import com.ptit.rc_system.service.NutritionCalculationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@CrossOrigin(origins = "*")
@RestController
public class HealthProfileController {

    @Autowired
    private HealthProfileRepository healthProfileRepository;

    @Autowired
    private NutritionCalculationService nutritionService;

    @GetMapping("/api/health-profiles/{userId}")
    public ResponseEntity<Map<String, Object>> getHealthProfile(@PathVariable Long userId) {
        Optional<HealthProfile> profileOpt = healthProfileRepository.findTopByUserIdOrderByLastUpdatedDescProfileIdDesc(userId);
        if (profileOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        
        HealthProfile p = profileOpt.get();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("profileId", p.getProfileId());
        map.put("userId", p.getUserId());
        map.put("height", p.getHeight());
        map.put("weight", p.getWeight());
        map.put("age", p.getAge());
        map.put("gender", p.getGender());
        map.put("activityLevel", p.getActivityLevel());
        map.put("healthGoal", p.getHealthGoal());
        map.put("bmi", p.getBmi());
        map.put("tdee", p.getTdee());
        map.put("targetCalories", p.getTargetCalories());
        map.put("lastUpdated", p.getLastUpdated());
        map.put("fitnessLevel", p.getFitnessLevel());
        
        return ResponseEntity.ok(map);
    }

    @PostMapping("/api/health-profiles")
    public ResponseEntity<Map<String, Object>> upsertHealthProfile(@RequestBody HealthProfilePayload payload) {
        if (payload.userId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        double bmr = nutritionService.calculateBMR(
                safeDouble(payload.weight),
                safeDouble(payload.height),
                safeInt(payload.age),
                payload.gender == null ? "male" : payload.gender
        );
        double tdee = nutritionService.calculateTDEE(bmr, safeDouble(payload.activityLevel));
        double targetCalories = nutritionService.caculateTargetCalories(tdee, payload.healthGoal, payload.gender);
        Double bmi = calculateBmi(payload.weight, payload.height);
        String fitnessLevel = normalizeFitnessLevel(payload.fitnessLevel);

        HealthProfile profile = healthProfileRepository.findTopByUserIdOrderByLastUpdatedDescProfileIdDesc(payload.userId)
                .orElse(new HealthProfile());

        profile.setUserId(payload.userId);
        profile.setHeight(payload.height);
        profile.setWeight(payload.weight);
        profile.setAge(payload.age);
        profile.setGender(payload.gender);
        profile.setActivityLevel(payload.activityLevel);
        profile.setHealthGoal(payload.healthGoal);
        profile.setBmi(bmi);
        profile.setTdee(tdee);
        profile.setTargetCalories(targetCalories);
        profile.setLastUpdated(LocalDateTime.now());
        profile.setFitnessLevel(fitnessLevel);

        healthProfileRepository.save(profile);

        return getHealthProfile(payload.userId);
    }

    private Double calculateBmi(Double weight, Double height) {
        if (weight == null || height == null || height <= 0) {
            return null;
        }
        double heightMeters = height / 100.0;
        return weight / (heightMeters * heightMeters);
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalizeFitnessLevel(String fitnessLevel) {
        if (fitnessLevel == null || fitnessLevel.isBlank()) {
            return "Beginner";
        }
        if (fitnessLevel.equalsIgnoreCase("Intermediate")) {
            return "Intermediate";
        }
        if (fitnessLevel.equalsIgnoreCase("Advanced")) {
            return "Advanced";
        }
        return "Beginner";
    }

    public static class HealthProfilePayload {
        public Long userId;
        public Double height;
        public Double weight;
        public java.lang.Integer age;
        public String gender;
        public Double activityLevel;
        public String healthGoal;
        public String fitnessLevel;
    }
}
