package com.ptit.rc_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "Health_Profiles")
public class HealthProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ProfileID", columnDefinition = "int")
    private Long profileId;

    @Column(name = "UserID", nullable = false, columnDefinition = "int")
    private Long userId;

    @Column(name = "Height")
    private Double height;

    @Column(name = "Weight")
    private Double weight;

    @Column(name = "Age")
    private Integer age;

    @Column(name = "Gender", columnDefinition = "nvarchar(50)")
    private String gender;

    @Column(name = "Activity_Level")
    private Double activityLevel;

    @Column(name = "Health_goal", columnDefinition = "nvarchar(100)")
    private String healthGoal;

    @Column(name = "BMI")
    private Double bmi;

    @Column(name = "TDEE")
    private Double tdee;

    @Column(name = "Target_Calories")
    private Double targetCalories;

    @Column(name = "Last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "Fitness_Level", columnDefinition = "nvarchar(255)")
    private String fitnessLevel;

    public Long getProfileId() {
        return profileId;
    }

    public void setProfileId(Long profileId) {
        this.profileId = profileId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Double getHeight() {
        return height;
    }

    public void setHeight(Double height) {
        this.height = height;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public Double getActivityLevel() {
        return activityLevel;
    }

    public void setActivityLevel(Double activityLevel) {
        this.activityLevel = activityLevel;
    }

    public String getHealthGoal() {
        return healthGoal;
    }

    public void setHealthGoal(String healthGoal) {
        this.healthGoal = healthGoal;
    }

    public Double getBmi() {
        return bmi;
    }

    public void setBmi(Double bmi) {
        this.bmi = bmi;
    }

    public Double getTdee() {
        return tdee;
    }

    public void setTdee(Double tdee) {
        this.tdee = tdee;
    }

    public Double getTargetCalories() {
        return targetCalories;
    }

    public void setTargetCalories(Double targetCalories) {
        this.targetCalories = targetCalories;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getFitnessLevel() {
        return fitnessLevel;
    }

    public void setFitnessLevel(String fitnessLevel) {
        this.fitnessLevel = fitnessLevel;
    }

}

