package com.ptit.rc_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "Exercise_Library")
public class Exercise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ExerciseID")
    private Integer exerciseId;

    @Column(name = "Name", nullable = false, columnDefinition = "nvarchar(255)")
    private String name;

    @Column(name = "Target_muscle", columnDefinition = "nvarchar(255)")
    private String targetMuscle;

    @Column(name = "Type", columnDefinition = "nvarchar(255)")
    private String type;

    @Column(name = "Level", columnDefinition = "nvarchar(255)")
    private String level;

    @Column(name = "Goal_Tag", columnDefinition = "nvarchar(255)")
    private String goalTag;

    @Column(name = "Equipment", columnDefinition = "nvarchar(255)")
    private String equipment;

    @Column(name = "Min_Sets")
    private Integer minSets;

    @Column(name = "Min_Reps", columnDefinition = "nvarchar(20)")
    private String minReps;

    @Column(name = "Max_Sets")
    private Integer maxSets;

    @Column(name = "Max_Reps")
    private Integer maxReps;

    @Column(name = "Default_Rest_Seconds")
    private Integer defaultRestSeconds;

    @Column(name = "Instructions", columnDefinition = "nvarchar(max)")
    private String instructions;

    @Column(name = "Met_Value")
    private Double metValue;

    public Integer getExerciseId() { return exerciseId; }
    public void setExerciseId(Integer exerciseId) { this.exerciseId = exerciseId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTargetMuscle() { return targetMuscle; }
    public void setTargetMuscle(String targetMuscle) { this.targetMuscle = targetMuscle; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getGoalTag() { return goalTag; }
    public void setGoalTag(String goalTag) { this.goalTag = goalTag; }
    public String getEquipment() { return equipment; }
    public void setEquipment(String equipment) { this.equipment = equipment; }
    public Integer getMinSets() { return minSets; }
    public void setMinSets(Integer minSets) { this.minSets = minSets; }
    public String getMinReps() { return minReps; }
    public void setMinReps(String minReps) { this.minReps = minReps; }
    public Integer getMaxSets() { return maxSets; }
    public void setMaxSets(Integer maxSets) { this.maxSets = maxSets; }
    public Integer getMaxReps() { return maxReps; }
    public void setMaxReps(Integer maxReps) { this.maxReps = maxReps; }
    public Integer getDefaultRestSeconds() { return defaultRestSeconds; }
    public void setDefaultRestSeconds(Integer defaultRestSeconds) { this.defaultRestSeconds = defaultRestSeconds; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public Double getMetValue() { return metValue; }
    public void setMetValue(Double metValue) { this.metValue = metValue; }
}
