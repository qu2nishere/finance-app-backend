package com.quan.budgetservice.common.util;

import com.quan.budgetservice.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeriodUtilsTest {

    @Test
    void periodKey_formatsCalendarMonth() {
        assertThat(PeriodUtils.periodKey(LocalDate.of(2026, 8, 5))).isEqualTo("2026-08");
        assertThat(PeriodUtils.periodKey(LocalDate.of(2026, 11, 22))).isEqualTo("2026-11");
    }

    @Test
    void range_returnsFirstAndLastDayOfMonth() {
        PeriodUtils.DateRange nov = PeriodUtils.range("2026-11");
        assertThat(nov.start()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(nov.end()).isEqualTo(LocalDate.of(2026, 11, 30));

        PeriodUtils.DateRange leapFeb = PeriodUtils.range("2028-02");
        assertThat(leapFeb.start()).isEqualTo(LocalDate.of(2028, 2, 1));
        assertThat(leapFeb.end()).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void range_withInvalidFormat_throwsInvalidRequest() {
        assertThatThrownBy(() -> PeriodUtils.range("2026-13"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code")
                .isEqualTo("INVALID_PERIOD");

        assertThatThrownBy(() -> PeriodUtils.range("2026-8"))
                .isInstanceOf(InvalidRequestException.class);

        assertThatThrownBy(() -> PeriodUtils.range(null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void daysLeft_handlesCurrentPastAndFutureMonths() {
        PeriodUtils.DateRange nov = PeriodUtils.range("2026-11");
        assertThat(PeriodUtils.daysLeft(nov, LocalDate.of(2026, 11, 22))).isEqualTo(9);
        assertThat(PeriodUtils.daysLeft(nov, LocalDate.of(2026, 11, 30))).isEqualTo(1);
        assertThat(PeriodUtils.daysLeft(nov, LocalDate.of(2026, 12, 1))).isEqualTo(0);
        assertThat(PeriodUtils.daysLeft(nov, LocalDate.of(2026, 10, 15))).isEqualTo(30);
    }
}
