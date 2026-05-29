package com.ptit.rc_system.entity;
import jakarta.persistence.*;

@Entity
@Table(name = "Food_Library") // Ánh xạ chính xác vào bảng SQL của Vũ
public class Food {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FoodID")
    private Long id;

    @Column(name = "Name", columnDefinition = "nvarchar(255)")
    private String name;

    @Column(name = "Calories")
    private Double calories;

    @Column(name = "Protein")
    private Double protein;

    @Column(name = "Carbs")
    private Double carbs;

    @Column(name = "Fat")
    private Double fat;

    @Column(name = "Serving_Unit", columnDefinition = "nvarchar(50)")
    private String servingUnit;

    @Column(name = "Meal_type", columnDefinition = "nvarchar(100)")
    private String mealType;

    @Column(name = "Category", columnDefinition = "nvarchar(50)")
    private String category;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Double getCalories() {
        return calories;
    }

    public Double getProtein() {
        return protein;
    }

    public Double getCarbs() {
        return carbs;
    }

    public Double getFat() {
        return fat;
    }

    public String getServingUnit() {
        return servingUnit;
    }

    public String getMealType() {
        return mealType;
    }

    public String getCategory() {
        return category;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCalories(Double calories) {
        this.calories = calories;
    }

    public void setProtein(Double protein) {
        this.protein = protein;
    }

    public void setCarbs(Double carbs) {
        this.carbs = carbs;
    }

    public void setFat(Double fat) {
        this.fat = fat;
    }

    public void setServingUnit(String servingUnit) {
        this.servingUnit = servingUnit;
    }

    public void setMealType(String mealType) {
        this.mealType = mealType;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
