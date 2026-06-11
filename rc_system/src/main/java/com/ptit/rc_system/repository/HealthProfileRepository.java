package com.ptit.rc_system.repository;

import com.ptit.rc_system.entity.HealthProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HealthProfileRepository extends JpaRepository<HealthProfile, Long> {
    Optional<HealthProfile> findTopByUserIdOrderByLastUpdatedDescProfileIdDesc(Long userId);
}
