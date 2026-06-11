package com.ptit.rc_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "Plan_Details")
public class PlanDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DetailID")
    private Integer detailId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PlanID", nullable = false)
    private DailyPlan dailyPlan;

    @Column(name = "Item_type")
    private String itemType;

    @Column(name = "FoodID")
    private Integer foodId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ExerciseID")
    private Exercise exercise;

    @Column(name = "Quantity")
    private Double quantity;

    @Column(name = "Is_completed")
    private Boolean completed;

    @Column(name = "Sets")
    private Integer sets;

    @Column(name = "Reps", columnDefinition = "nvarchar(255)")
    private String reps;

    @Column(name = "Rest_Seconds")
    private Integer restSeconds;

    @Column(name = "Sort_Order")
    private Integer sortOrder;

    @Column(name = "Workout_Phase", columnDefinition = "nvarchar(255)")
    private String workoutPhase;

    @Column(name = "Duration_Minutes")
    private Integer durationMinutes;

    @Column(name = "Calories_Burned")
    private Double caloriesBurned;

    @Column(name = "Completed_At")
    private LocalDateTime completedAt;

    @Column(name = "Rating")
    private Double rating;

    public Integer getDetailId() { return detailId; }
    public void setDetailId(Integer detailId) { this.detailId = detailId; }
    public DailyPlan getDailyPlan() { return dailyPlan; }
    public void setDailyPlan(DailyPlan dailyPlan) { this.dailyPlan = dailyPlan; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public Integer getFoodId() { return foodId; }
    public void setFoodId(Integer foodId) { this.foodId = foodId; }
    public Exercise getExercise() { return exercise; }
    public void setExercise(Exercise exercise) { this.exercise = exercise; }
    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }
    public Boolean getCompleted() { return completed; }
    public void setCompleted(Boolean completed) { this.completed = completed; }
    public Integer getSets() { return sets; }
    public void setSets(Integer sets) { this.sets = sets; }
    public String getReps() { return reps; }
    public void setReps(String reps) { this.reps = reps; }
    public Integer getRestSeconds() { return restSeconds; }
    public void setRestSeconds(Integer restSeconds) { this.restSeconds = restSeconds; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getWorkoutPhase() { return workoutPhase; }
    public void setWorkoutPhase(String workoutPhase) { this.workoutPhase = workoutPhase; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public Double getCaloriesBurned() { return caloriesBurned; }
    public void setCaloriesBurned(Double caloriesBurned) { this.caloriesBurned = caloriesBurned; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
}
