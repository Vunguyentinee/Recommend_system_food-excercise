package com.ptit.rc_system.dto;
import com.ptit.rc_system.entity.Food;

public class FoodPortion {
    private Food food;
    private double multiplier;
    private double totalCalories;

    public FoodPortion(Food food,double multiplier, double totalCalories){
        this.food = food;
        this.multiplier = Math.round(multiplier*10.0) /10.0;
        this.totalCalories = Math.round(totalCalories);
    }

    public Food getFood() {
        return food;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public double getTotalCalories() {
        return totalCalories;
    }
}
