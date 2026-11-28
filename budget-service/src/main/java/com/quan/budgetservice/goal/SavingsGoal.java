package com.quan.budgetservice.goal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "savings_goals")
@Getter
@Setter
@NoArgsConstructor
public class SavingsGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "icon", nullable = false, length = 50)
    private String icon = "CardGiftcard";

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "color", nullable = false, length = 7)
    private String color = "#16A34A";

    @Column(name = "target_amount", nullable = false)
    private long targetAmount;

    @Column(name = "saved_amount", nullable = false)
    private long savedAmount = 0L;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "wallet_id")
    private UUID walletId;

    @Column(name = "wallet_name", length = 100)
    private String walletName;

    @Column(name = "priority", nullable = false)
    private short priority = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private GoalStatus status = GoalStatus.ACTIVE;

    @Column(name = "show_on_home", nullable = false)
    private boolean showOnHome = true;

    @Column(name = "remind_if_behind", nullable = false)
    private boolean remindIfBehind = true;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "note", length = 500)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
