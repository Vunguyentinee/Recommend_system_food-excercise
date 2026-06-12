package com.ptit.rc_system;

import com.ptit.rc_system.entity.InteractionLog;
import com.ptit.rc_system.repository.InteractionLogRepository;
import com.ptit.rc_system.service.FoodCollaborativeFilteringService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(MockitoExtension.class)
class FoodCollaborativeFilteringServiceTest {

    @Mock
    private InteractionLogRepository logRepository;

    @InjectMocks
    private FoodCollaborativeFilteringService collaborativeFilteringService;

    @Test
    void testRecommendFoodsForUser() {
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

        Mockito.when(logRepository.findAllByFoodIdIsNotNullAndRatingIsNotNull())
                .thenReturn(Arrays.asList(log1, log2, log3, log4));

        List<Long> recommendedFoods = collaborativeFilteringService.recommendFoodsForUser(1L, 3);
        System.out.println("Danh sách món gợi ý cho User 1: " + recommendedFoods);
        assertFalse(recommendedFoods.isEmpty(), "Danh sách không được trống");
    }
}
