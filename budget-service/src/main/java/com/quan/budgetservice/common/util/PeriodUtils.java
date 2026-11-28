package com.quan.budgetservice.common.util;

import com.quan.budgetservice.common.security.AccountZone;

import com.quan.budgetservice.common.exception.InvalidRequestException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

public final class PeriodUtils {


    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Pattern PERIOD_PATTERN = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    private PeriodUtils() {
    }

    public record DateRange(LocalDate start, LocalDate end) {
    }

    public static String periodKey(LocalDate date) {
        if (date == null) {
            throw new InvalidRequestException("INVALID_PERIOD", "Ngày không hợp lệ");
        }
        return YearMonth.from(date).format(PERIOD_FORMATTER);
    }

    public static String currentPeriodKey() {
        return periodKey(LocalDate.now(AccountZone.current()));
    }

    public static DateRange range(String periodKey) {
        YearMonth ym = parsePeriod(periodKey);
        return new DateRange(ym.atDay(1), ym.atEndOfMonth());
    }

    public static YearMonth parsePeriod(String periodKey) {
        if (periodKey == null || !PERIOD_PATTERN.matcher(periodKey).matches()) {
            throw new InvalidRequestException("INVALID_PERIOD", "Kỳ ngân sách phải có định dạng yyyy-MM");
        }
        try {
            return YearMonth.parse(periodKey, PERIOD_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new InvalidRequestException("INVALID_PERIOD", "Kỳ ngân sách không hợp lệ");
        }
    }

    public static int daysLeft(DateRange range, LocalDate today) {
        if (today.isAfter(range.end())) {
            return 0;
        }
        LocalDate effectiveStart = today.isBefore(range.start()) ? range.start() : today;
        return (int) ChronoUnit.DAYS.between(effectiveStart, range.end()) + 1;
    }
}
