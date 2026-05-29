package com.ptit.rc_system.repository;

import com.ptit.rc_system.entity.Food;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FoodRepository extends JpaRepository<Food, Long> {
    // Để trống hoàn toàn! Spring Boot sẽ tự hiểu và tự lôi data lên.
}
