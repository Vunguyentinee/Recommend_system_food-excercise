package com.ptit.rc_system.repository;

import com.ptit.rc_system.entity.InteractionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InteractionLogRepository extends JpaRepository<InteractionLog, Long> {
    // Lấy tất cả tương tác để nạp vào ma trận tính toán
    List<InteractionLog> findAllByFoodIdIsNotNull();

    List<InteractionLog> findByUserId (Long userID);
    boolean existsByUserId (Long userId);
}
