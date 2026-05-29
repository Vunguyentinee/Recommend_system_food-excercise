package com.ptit.rc_system.controller;

import com.ptit.rc_system.entity.Food;
import com.ptit.rc_system.repository.FoodRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TestController {

    @Autowired
    private FoodRepository foodRepository;

    @GetMapping("/api/test-foods")
    public List<Food> testGetAllFoods() {
        // Đúng 1 dòng lệnh để lấy toàn bộ danh sách 30 món ăn!
        return foodRepository.findAll();
    }
}
