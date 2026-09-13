package com.quan.userservice.settings;

import com.quan.userservice.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "notification_settings")
@Getter
@Setter
@NoArgsConstructor
public class NotificationSettings{
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    // Công tắc tổng: tắt cái này là tắt hết push
    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = true;

    // --- Nhắc nhở ---
    @Column(name = "daily_reminder_enabled", nullable = false)
    private boolean dailyReminderEnabled = true;

    @Column(name = "daily_reminder_time", nullable = false)
    private LocalTime dailyReminderTime = LocalTime.of(21, 0);

    @Column(name = "recurring_due_enabled", nullable = false)
    private boolean recurringDueEnabled = true;

    @Column(name = "recurring_due_lead_days", nullable = false)
    private short recurringDueLeadDays = 3;

    // --- Ngân sách ---
    @Column(name = "budget_threshold_enabled", nullable = false)
    private boolean budgetThresholdEnabled = true;

    @Column(name = "budget_exceeded_enabled", nullable = false)
    private boolean budgetExceededEnabled = true;

    @Column(name = "uncategorized_enabled", nullable = false)
    private boolean uncategorizedEnabled = true;

    // --- Thu nhập & mục tiêu ---
    @Column(name = "income_received_enabled", nullable = false)
    private boolean incomeReceivedEnabled = true;

    @Column(name = "autosave_done_enabled", nullable = false)
    private boolean autosaveDoneEnabled = true;

    @Column(name = "goal_behind_enabled", nullable = false)
    private boolean goalBehindEnabled = true;

    // --- Báo cáo ---
    @Column(name = "weekly_report_enabled", nullable = false)
    private boolean weeklyReportEnabled = true;

    // 1 = thứ Hai (ISO-8601), khớp start_of_week của user_settings
    @Column(name = "weekly_report_dow", nullable = false)
    private short weeklyReportDow = 1;

    @Column(name = "monthly_summary_enabled", nullable = false)
    private boolean monthlySummaryEnabled = true;

    @Column(name = "monthly_summary_day", nullable = false)
    private short monthlySummaryDay = 1;

    // --- Không làm phiền ---
    @Column(name = "quiet_hours_enabled", nullable = false)
    private boolean quietHoursEnabled = true;

    @Column(name = "quiet_hours_start", nullable = false)
    private LocalTime quietHoursStart = LocalTime.of(22, 0);

    @Column(name = "quiet_hours_end", nullable = false)
    private LocalTime quietHoursEnd = LocalTime.of(7, 0);

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Settings khong the ton tai neu khong co user -> bat buoc truyen vao
    public NotificationSettings(User user) {
        this.user = user;
    }
}
