package com.ptit.rc_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Interaction_Logs")
public class InteractionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LogID", columnDefinition = "int")
    private Long logId;

    @Column(name = "UserID", columnDefinition = "int")
    private Long userId;

    @Column(name = "FoodID", columnDefinition = "int")
    private Long foodId;

    @Column(name = "ExerciseID", columnDefinition = "int")
    private Integer exerciseId;

    @Column(name = "Interaction_type")
    private String interactionType;

    @Column(name = "Rating")
    private Double rating;

    @Column(name = "Is_completed")
    private Boolean completed;

    @Column(name = "Meal_Time")
    private String mealTime;

    @Column(name = "Log_date")
    private LocalDateTime logDate;

    @Column(name = "PlanDetailID", columnDefinition = "int")
    private Integer planDetailId;

    @Column(name = "Duration_Minutes")
    private Integer durationMinutes;

    @Column(name = "Calories_Burned")
    private Double caloriesBurned;

    public Long getLogId() { return logId; }
    public Long getUserId() { return userId; }
    public Long getFoodId() { return foodId; }
    public Integer getExerciseId() { return exerciseId; }
    public String getInteractionType() { return interactionType; }
    public Double getRating() { return rating; }
    public Boolean getCompleted() { return completed; }
    public String getMealTime() { return mealTime; }
    public LocalDateTime getLogDate() { return logDate; }
    public Integer getPlanDetailId() { return planDetailId; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public Double getCaloriesBurned() { return caloriesBurned; }

    public void setLogId(Long logId) { this.logId = logId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setFoodId(Long foodId) { this.foodId = foodId; }
    public void setExerciseId(Integer exerciseId) { this.exerciseId = exerciseId; }
    public void setInteractionType(String interactionType) { this.interactionType = interactionType; }
    public void setRating(Double rating) { this.rating = rating; }
    public void setCompleted(Boolean completed) { this.completed = completed; }
    public void setMealTime(String mealTime) { this.mealTime = mealTime; }
    public void setLogDate(LocalDateTime logDate) { this.logDate = logDate; }
    public void setPlanDetailId(Integer planDetailId) { this.planDetailId = planDetailId; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public void setCaloriesBurned(Double caloriesBurned) { this.caloriesBurned = caloriesBurned; }
}
