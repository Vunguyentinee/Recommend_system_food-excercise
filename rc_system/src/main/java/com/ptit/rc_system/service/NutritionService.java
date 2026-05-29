package com.ptit.rc_system.service;

import com.ptit.rc_system.dto.FoodPortion;
import org.springframework.stereotype.Service;
import com.ptit.rc_system.entity.Food;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

@Service // Gắn mác này để Spring Boot biết đây là "Não bộ"
public class NutritionService {

    // 1. Hàm tính BMR (Năng lượng nền để duy trì sự sống)
    public double calculateBMR(double weight, double height, int age, String gender) {
        // weight (kg), height (cm), age (tuổi)
        double bmr = (10 * weight) + (6.25 * height) - (5 * age);

        if (gender.equalsIgnoreCase("MALE") || gender.equalsIgnoreCase("NAM")) {
            return bmr + 5; // Chỉ số của Nam
        } else {
            return bmr - 161; // Chỉ số của Nữ
        }
    }

    // 2. Hàm tính TDEE (Tổng Calo cần trong ngày)
    public double calculateTDEE(double bmr, double activityMultiplier) {
        /* Bảng hệ số vận động (activityMultiplier):
           - Ít vận động: 1.2
           - Vận động nhẹ: 1.375
           - Vận động vừa: 1.55
           - Vận động nhiều: 1.725
        */
        return bmr * activityMultiplier;
    }

    public double caculateTargetCalories(double tdee, String HealthGoal, String gender){
        double target = tdee;
        if (HealthGoal.equalsIgnoreCase("Giảm cân")){
            target= tdee - 500;
        }
        else if (HealthGoal.equalsIgnoreCase("Tăng cân")){
            target = tdee + 500;
        }

        // Lưới an toàn
        if (gender.equalsIgnoreCase("Nam") || gender.equalsIgnoreCase("Male")){
            return Math.max(target, 1500.0);
        }
        else{
            return Math.max(target,1200.0);
        }
    }

    public double calculationCosineSimilarity (double[] targetMacro,double[] foodMacro){
        double dotProduct = 0.0;
        double normTarget = 0.0;
        double normFood = 0.0;

        for (int i =0;i <targetMacro.length;i++){
            dotProduct += targetMacro[i] * foodMacro[i];
            normTarget += Math.pow(targetMacro[i],2);
            normFood += Math.pow(foodMacro[i],2);
        }
        if (normTarget == 0 || normFood == 0) return 0.0;
        return dotProduct / (Math.sqrt(normTarget) *Math.sqrt(normFood));
    }

    public FoodPortion findBestFoodMatch(double targetCalories, double[] targetMacro, List<Food> candidateFoods, double maxPortionLimit) {

        FoodPortion bestMatch = null;
        double highestScore = -1.0;

        // CHIÊU TRÒ: Đổ danh sách cũ sang một rổ mới (ArrayList) để chắc chắn xáo trộn được
        List<Food> roThucAnTronDeu = new ArrayList<>(candidateFoods);
        Collections.shuffle(roThucAnTronDeu);

        // Chạy vòng lặp trên cái rổ đã trộn
        for (Food food : roThucAnTronDeu) {

            // 1. Tính điểm Cosine
            double[] foodMacro = {food.getProtein(), food.getCarbs(), food.getFat()};
            double cosineScore = calculationCosineSimilarity(targetMacro, foodMacro);

            // 2. Tính Hệ số lượng
            double rawMultiplier = targetCalories / food.getCalories();

            // LÀM TRÒN THEO BẬC 0.5 (Mẹo Toán học)
            double multiplier = Math.round(rawMultiplier * 2) / 2.0;

            // 3. Màng lọc Y khoa
            if (multiplier < 0.5) continue;
            if (multiplier > maxPortionLimit) multiplier = maxPortionLimit;

            double actualCalories = food.getCalories() * multiplier;
            FoodPortion currentPortion = new FoodPortion(food, multiplier, actualCalories);

            // 4. LUẬT CHỐNG NHÀM CHÁN: Món nào > 85% là nhặt luôn!
            if (cosineScore > 0.85) {
                return currentPortion;
            }

            // 5. Dự phòng
            if (cosineScore > highestScore) {
                highestScore = cosineScore;
                bestMatch = currentPortion;
            }
        }

        return bestMatch;
    }

    public List<FoodPortion> generateLunchCombo (double targetDailyCalories, List<Food> allFoods,boolean isTraditional){
        List<FoodPortion> lunchCombo = new ArrayList<>();

        double lunchCalories = targetDailyCalories * 0.35;
        double[] targetMacro = {30.0, 50.0, 20.0};

        if (isTraditional){
            double tinhBotCal = lunchCalories *0.45;
            double damCal = lunchCalories * 0.35;
            double rauOrHoaQuaCal = lunchCalories * 0.20;
            List<Food> dsTinhBot = allFoods.stream().filter(f -> f.getCategory().equalsIgnoreCase("Món tinh bột")).toList();
            List<Food> dsDam = allFoods.stream().filter(f -> f.getCategory().equalsIgnoreCase("Món đạm")).toList();
            List<Food> dsRau = allFoods.stream().filter(f -> f.getCategory().equalsIgnoreCase("Món rau")).toList();
            List<Food> dsHoaQua = allFoods.stream().filter(f -> f.getCategory().equalsIgnoreCase("Món hoa quả")).toList();

            FoodPortion monRauOrHoaQua = findBestFoodMatch(rauOrHoaQuaCal, targetMacro, dsRau, 1.5);
            if (monRauOrHoaQua == null) {
                monRauOrHoaQua = findBestFoodMatch(rauOrHoaQuaCal, targetMacro, dsHoaQua, 1.5);
            }
            double caloHutTuRauOrHoaQua = 0;
            if (monRauOrHoaQua != null) {
                lunchCombo.add(monRauOrHoaQua);
                caloHutTuRauOrHoaQua = rauOrHoaQuaCal - monRauOrHoaQua.getTotalCalories();
            }

            FoodPortion monTinhBot = findBestFoodMatch(tinhBotCal, targetMacro, dsTinhBot, 2.0);
            if (monTinhBot != null) lunchCombo.add(monTinhBot);

            // Nhặt Món Đạm (Max 1.5 đĩa) - Nhận phần Calo hụt từ Rau/Hoa quả
            double tienDamThucTe = damCal + caloHutTuRauOrHoaQua;
            FoodPortion monDam = findBestFoodMatch(tienDamThucTe, targetMacro, dsDam, 1.5);
            if (monDam != null) lunchCombo.add(monDam);
        }

        else {
            List<Food> dsHonHop = allFoods.stream().filter(f -> f.getCategory().equalsIgnoreCase("Món hỗn hợp")).toList();
            List<Food> dsNuoc = allFoods.stream().filter(f -> f.getCategory().equalsIgnoreCase("Món nước")).toList();
            if (dsHonHop.isEmpty() && dsNuoc.isEmpty()) {
                return generateLunchCombo(targetDailyCalories, allFoods, true);
            }

            double honHopCal = lunchCalories * 0.85; // 85% tiền đập vào Bún/Phở/Pizza
            double nuocCal = lunchCalories * 0.15;

            FoodPortion monHonHop = findBestFoodMatch(honHopCal, targetMacro, dsHonHop, 2.0);
            if (monHonHop != null) lunchCombo.add(monHonHop);

            FoodPortion monNuoc = findBestFoodMatch(nuocCal, targetMacro, dsNuoc, 1.0);
            if (monNuoc != null) lunchCombo.add(monNuoc);
        }
        return lunchCombo;
    }
}
