package com.ptit.rc_system.repository;

import com.ptit.rc_system.entity.Exercise;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExerciseRepository extends JpaRepository<Exercise, Integer> {
    List<Exercise> findAllByOrderByExerciseIdAsc();
}
