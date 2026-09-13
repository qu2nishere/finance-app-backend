package com.quan.userservice.settings;

import com.quan.userservice.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
@NoArgsConstructor
public class UserSettings{
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    //Cài đặt thường
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "VND";

    @Column(name = "currency_decimals", nullable = false)
    private short currencyDecimals = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "decimal_separator", nullable = false, length = 10)
    private DecimalSeparator decimalSeparator = DecimalSeparator.DOT;

    @Enumerated(EnumType.STRING)
    @Column(name = "symbol_position", nullable = false, length = 10)
    private SymbolPosition symbolPosition = SymbolPosition.SUFFIX;

    @Column(name = "compact_numbers", nullable = false)
    private boolean compactNumbers = true;

    @Column(name = "convert_legacy_data", nullable = false)
    private boolean convertLegacyData;

    // Giao diện
    @Enumerated(EnumType.STRING)
    @Column(name = "theme_mode", nullable = false, length = 10)
    private ThemeMode themeMode = ThemeMode.SYSTEM;

    @Column(nullable = false, length = 10)
    private String language = "vi";

    // --- Ky tai chinh ---
    @Column(name = "start_of_week", nullable = false)
    private short startOfWeek = 1;

    @Column(name = "month_start_day", nullable = false)
    private short monthStartDay = 1;

    // --- Bao mat ---
    @Column(name = "biometric_enabled", nullable = false)
    private boolean biometricEnabled;

    @Column(name = "pin_hash")
    private String pinHash;

    @Column(name = "pin_enabled", nullable = false)
    private boolean pinEnabled;

    @Column(name = "auto_lock_seconds", nullable = false)
    private int autoLockSeconds = 60;

    // --- Sao luu ---
    @Column(name = "auto_backup_enabled", nullable = false)
    private boolean autoBackupEnabled;

    @Column(name = "last_backup_at")
    private Instant lastBackupAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Settings khong the ton tai neu khong co user -> bat buoc truyen vao
    public UserSettings(User user) {
        this.user = user;
    }

    public enum DecimalSeparator { DOT, COMMA, SPACE }
    public enum SymbolPosition { PREFIX, SUFFIX }
    public enum ThemeMode { LIGHT, DARK, SYSTEM }
}