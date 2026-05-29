package com.ptit.rc_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Interaction_Logs")
public class InteractionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LogID")
    private Long logId;

    @Column(name = "UserID")
    private Long userId;

    @Column(name = "FoodID")
    private Long foodId;

    @Column(name = "Rating")
    private Double rating;

    // Các field khác có thể bỏ qua nếu thuật toán chưa cần

    // Getters
    public Long getLogId() { return logId; }
    public Long getUserId() { return userId; }
    public Long getFoodId() { return foodId; }
    public Double getRating() { return rating; }

    // Setters
    public void setLogId(Long logId) { this.logId = logId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setFoodId(Long foodId) { this.foodId = foodId; }
    public void setRating(Double rating) { this.rating = rating; }
}
