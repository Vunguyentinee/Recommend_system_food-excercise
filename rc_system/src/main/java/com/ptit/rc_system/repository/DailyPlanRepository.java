package com.ptit.rc_system.repository;

import com.ptit.rc_system.entity.DailyPlan;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DailyPlanRepository extends JpaRepository<DailyPlan, Integer> {
    Optional<DailyPlan> findTopByUserIdAndPlanDateOrderByPlanIdDesc(Integer userId, LocalDate planDate);
}
