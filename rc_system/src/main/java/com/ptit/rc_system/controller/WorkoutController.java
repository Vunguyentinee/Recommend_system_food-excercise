package com.ptit.rc_system.controller;

import com.ptit.rc_system.service.WorkoutRecommendationService;
import com.ptit.rc_system.service.WorkoutRecommendationService.WorkoutCompletion;
import com.ptit.rc_system.service.WorkoutRecommendationService.WorkoutRating;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workouts")
public class WorkoutController {
    private final WorkoutRecommendationService workoutRecommendationService;

    public WorkoutController(WorkoutRecommendationService workoutRecommendationService) {
        this.workoutRecommendationService = workoutRecommendationService;
    }

    @GetMapping("/recommend")
    public Map<String, Object> recommend(@RequestParam long userId,
                                         @RequestParam(defaultValue = "false") boolean regenerate) {
        return workoutRecommendationService.getOrCreateTodayPlan(userId, regenerate);
    }

    @PostMapping("/complete")
    public Map<String, Object> complete(@RequestBody WorkoutCompletion payload) {
        return workoutRecommendationService.complete(payload);
    }

    @PostMapping("/rate")
    public Map<String, Object> rate(@RequestBody WorkoutRating payload) {
        return workoutRecommendationService.rate(payload);
    }

    @GetMapping("/history")
    public Map<String, Object> history(@RequestParam long userId,
                                       @RequestParam(required = false) LocalDate date) {
        return workoutRecommendationService.history(userId, date == null ? LocalDate.now() : date);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("message", exception.getMessage()));
    }
}
