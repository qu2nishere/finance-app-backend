package com.quan.budgetservice.budget;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "category_limits")
@Getter
@Setter
@NoArgsConstructor
public class CategoryLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "budget_id", nullable = false)
    private UUID budgetId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(name = "category_icon", length = 50)
    private String categoryIcon;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "category_color", length = 7)
    private String categoryColor;

    @Column(name = "limit_amount", nullable = false)
    private long limitAmount;

    @Column(name = "alert_threshold", nullable = false, precision = 3, scale = 2)
    private BigDecimal alertThreshold = new BigDecimal("0.80");

    @Column(name = "include_subcategories", nullable = false)
    private boolean includeSubcategories = true;

    @Column(name = "rollover_enabled", nullable = false)
    private boolean rolloverEnabled = false;

    @Column(name = "rolled_over_amount", nullable = false)
    private long rolledOverAmount = 0L;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
