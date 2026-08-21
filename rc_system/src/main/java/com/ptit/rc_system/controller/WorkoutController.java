package com.ptit.rc_system.controller;

import com.ptit.rc_system.security.OwnershipGuard;
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
    private final OwnershipGuard ownershipGuard;

    public WorkoutController(WorkoutRecommendationService workoutRecommendationService,
                             OwnershipGuard ownershipGuard) {
        this.workoutRecommendationService = workoutRecommendationService;
        this.ownershipGuard = ownershipGuard;
    }

    @GetMapping("/recommend")
    public Map<String, Object> recommend(@RequestParam long userId,
                                         @RequestParam(defaultValue = "false") boolean regenerate) {
        ownershipGuard.checkSelfOrAdmin(userId);
        return workoutRecommendationService.getOrCreateTodayPlan(userId, regenerate);
    }

    @PostMapping("/complete")
    public Map<String, Object> complete(@RequestBody WorkoutCompletion payload) {
        ownershipGuard.checkSelfOrAdmin(payload.userId());
        return workoutRecommendationService.complete(payload);
    }

    @PostMapping("/rate")
    public Map<String, Object> rate(@RequestBody WorkoutRating payload) {
        ownershipGuard.checkSelfOrAdmin(payload.userId());
        return workoutRecommendationService.rate(payload);
    }

    @GetMapping("/history")
    public Map<String, Object> history(@RequestParam long userId,
                                       @RequestParam(required = false) LocalDate date) {
        ownershipGuard.checkSelfOrAdmin(userId);
        return workoutRecommendationService.history(userId, date == null ? LocalDate.now() : date);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("message", exception.getMessage()));
    }
}
