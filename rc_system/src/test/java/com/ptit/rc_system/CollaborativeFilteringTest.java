package com.ptit.rc_system;

import com.ptit.rc_system.entity.InteractionLog;
import com.ptit.rc_system.repository.InteractionLogRepository;
import com.ptit.rc_system.service.CollaborativeFilteringService;
import org.junit.jupiter.api.BeforeEach; // CẦN THÊM CÁI NÀY
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations; // CẦN THÊM CÁI NÀY
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class CollaborativeFilteringTest {

    @Mock
    private InteractionLogRepository logRepository;

    @InjectMocks
    private CollaborativeFilteringService collaborativeFilteringService;

    // --- THÊM KHỐI LỆNH NÀY ---
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }
    // --------------------------

    @Test
    void testRecommendFoodsForUser() {
        // ... (phần code thân hàm test của bạn giữ nguyên) ...

        // 1. Giả lập dữ liệu (Mock Data)
        InteractionLog log1 = new InteractionLog();
        log1.setUserId(1L);
        log1.setFoodId(101L);
        log1.setRating(5.0);

        InteractionLog log2 = new InteractionLog();
        log2.setUserId(1L);
        log2.setFoodId(102L);
        log2.setRating(4.0);

        InteractionLog log3 = new InteractionLog();
        log3.setUserId(2L);
        log3.setFoodId(101L);
        log3.setRating(4.0);

        InteractionLog log4 = new InteractionLog();
        log4.setUserId(2L);
        log4.setFoodId(103L);
        log4.setRating(5.0);

        Mockito.when(logRepository.findAllByFoodIdIsNotNull())
                .thenReturn(Arrays.asList(log1, log2, log3, log4));

        List<Long> recommendedFoods = collaborativeFilteringService.recommendFoodsForUser(1L, 3);
        System.out.println("Danh sách món gợi ý cho User 1: " + recommendedFoods);
        assertFalse(recommendedFoods.isEmpty(), "Danh sách không được trống");
    }
}
