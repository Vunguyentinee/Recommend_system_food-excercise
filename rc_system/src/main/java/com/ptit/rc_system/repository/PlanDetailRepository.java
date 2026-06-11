package com.ptit.rc_system.repository;

import com.ptit.rc_system.entity.PlanDetail;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanDetailRepository extends JpaRepository<PlanDetail, Integer> {
    long countByDailyPlanPlanIdAndItemType(Integer planId, String itemType);

    boolean existsByDailyPlanPlanIdAndItemTypeAndCompletedTrue(Integer planId, String itemType);

    void deleteByDailyPlanPlanIdAndItemType(Integer planId, String itemType);

    List<PlanDetail> findByDailyPlanPlanIdAndItemTypeOrderBySortOrder(Integer planId, String itemType);

    @Query("""
        SELECT pd FROM PlanDetail pd
        JOIN FETCH pd.exercise
        WHERE pd.dailyPlan.planId = :planId AND pd.itemType = :itemType
        ORDER BY pd.sortOrder
        """)
    List<PlanDetail> findPlanItems(@Param("planId") Integer planId, @Param("itemType") String itemType);

    @Query("""
        SELECT pd.exercise.exerciseId FROM PlanDetail pd
        WHERE pd.dailyPlan.planId = :planId AND pd.itemType = :itemType
        """)
    List<Integer> findExerciseIds(@Param("planId") Integer planId, @Param("itemType") String itemType);

    @Query("""
        SELECT pd FROM PlanDetail pd
        JOIN FETCH pd.exercise
        WHERE pd.detailId = :detailId
          AND pd.dailyPlan.userId = :userId
          AND pd.dailyPlan.planDate = :planDate
          AND pd.itemType = :itemType
        """)
    Optional<PlanDetail> findOwnedDetail(@Param("userId") Integer userId,
                                         @Param("planDate") LocalDate planDate,
                                         @Param("itemType") String itemType,
                                         @Param("detailId") Integer detailId);

    @Query("""
        SELECT pd FROM PlanDetail pd
        JOIN FETCH pd.exercise
        WHERE pd.exercise.exerciseId = :exerciseId
          AND pd.dailyPlan.userId = :userId
          AND pd.dailyPlan.planDate = :planDate
          AND pd.itemType = :itemType
        ORDER BY pd.detailId DESC
        """)
    List<PlanDetail> findOwnedDetailsByExercise(@Param("userId") Integer userId,
                                                @Param("planDate") LocalDate planDate,
                                                @Param("itemType") String itemType,
                                                @Param("exerciseId") Integer exerciseId);

    @Query("""
        SELECT pd FROM PlanDetail pd
        WHERE pd.detailId = :detailId
          AND pd.dailyPlan.userId = :userId
          AND pd.dailyPlan.planDate = :planDate
          AND pd.itemType = :itemType
        """)
    Optional<PlanDetail> findOwnedFoodDetail(@Param("userId") Integer userId,
                                             @Param("planDate") LocalDate planDate,
                                             @Param("itemType") String itemType,
                                             @Param("detailId") Integer detailId);

    @Query("""
        SELECT pd FROM PlanDetail pd
        WHERE pd.foodId = :foodId
          AND pd.dailyPlan.userId = :userId
          AND pd.dailyPlan.planDate = :planDate
          AND pd.itemType = :itemType
        ORDER BY pd.detailId DESC
        """)
    List<PlanDetail> findOwnedFoodDetailsByFood(@Param("userId") Integer userId,
                                                @Param("planDate") LocalDate planDate,
                                                @Param("itemType") String itemType,
                                                @Param("foodId") Integer foodId);
}
