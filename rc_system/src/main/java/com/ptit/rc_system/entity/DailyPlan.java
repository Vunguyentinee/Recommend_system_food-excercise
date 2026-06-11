package com.ptit.rc_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "Daily_Plans")
public class DailyPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PlanID")
    private Integer planId;

    @Column(name = "UserID", nullable = false)
    private Integer userId;

    @Column(name = "PlanDate", nullable = false)
    private LocalDate planDate;

    @Column(name = "Total_target_calories")
    private Double totalTargetCalories;

    @Column(name = "Workout_Split")
    private String workoutSplit;

    @Column(name = "Workout_Days_Per_Week")
    private Integer workoutDaysPerWeek;

    @Column(name = "Cardio_Note", columnDefinition = "nvarchar(255)")
    private String cardioNote;

    public Integer getPlanId() { return planId; }
    public void setPlanId(Integer planId) { this.planId = planId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public LocalDate getPlanDate() { return planDate; }
    public void setPlanDate(LocalDate planDate) { this.planDate = planDate; }
    public Double getTotalTargetCalories() { return totalTargetCalories; }
    public void setTotalTargetCalories(Double totalTargetCalories) { this.totalTargetCalories = totalTargetCalories; }
    public String getWorkoutSplit() { return workoutSplit; }
    public void setWorkoutSplit(String workoutSplit) { this.workoutSplit = workoutSplit; }
    public Integer getWorkoutDaysPerWeek() { return workoutDaysPerWeek; }
    public void setWorkoutDaysPerWeek(Integer workoutDaysPerWeek) { this.workoutDaysPerWeek = workoutDaysPerWeek; }
    public String getCardioNote() { return cardioNote; }
    public void setCardioNote(String cardioNote) { this.cardioNote = cardioNote; }
}
