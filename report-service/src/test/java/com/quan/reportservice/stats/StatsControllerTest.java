package com.quan.reportservice.stats;

import com.quan.reportservice.common.config.SecurityConfig;
import com.quan.reportservice.common.config.WebMvcConfig;
import com.quan.reportservice.common.exception.GlobalExceptionHandler;
import com.quan.reportservice.common.exception.InvalidRequestException;
import com.quan.reportservice.common.security.CurrentUserArgumentResolver;
import com.quan.reportservice.common.security.JwtAuthFilter;
import com.quan.reportservice.common.security.JwtService;
import com.quan.reportservice.stats.dto.MonthlyBarPointResponse;
import com.quan.reportservice.stats.dto.MonthlyTransactionsResponse;
import com.quan.reportservice.stats.dto.MonthlyTrendResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatsController.class)
@Import({
        SecurityConfig.class,
        WebMvcConfig.class,
        CurrentUserArgumentResolver.class,
        JwtAuthFilter.class,
        JwtService.class,
        GlobalExceptionHandler.class
})
@TestPropertySource(properties = "app.jwt.secret=test-only-dummy-secret-key-at-least-32-bytes!!")
class StatsControllerTest {

    private static final String TEST_SECRET = "test-only-dummy-secret-key-at-least-32-bytes!!";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatsService statsService;

    @Test
    void getMonthlyTrend_withoutToken_returns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/stats/monthly-trend")
                        .param("period", "2026-08"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void getMonthlyTrend_withValidJwt_returns200AndPoints() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = signToken(userId);

        MonthlyTrendResponse mockResp = new MonthlyTrendResponse(
                "2026-08",
                "INCOME",
                null,
                "VND",
                18_200_000L,
                17_300_000L,
                900_000L,
                new BigDecimal("5.20"),
                List.of(new MonthlyBarPointResponse("2026-08", 2026, 8, 18_200_000L, 2, true)),
                List.of()
        );
        when(statsService.getMonthlyTrend(userId, "2026-08", 5, "INCOME", null)).thenReturn(mockResp);

        mockMvc.perform(get("/api/stats/monthly-trend")
                        .header("Authorization", "Bearer " + token)
                        .param("period", "2026-08")
                        .param("months", "5")
                        .param("type", "INCOME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("2026-08"))
                .andExpect(jsonPath("$.selectedAmount").value(18_200_000L))
                .andExpect(jsonPath("$.momChangePercent").value(5.2))
                .andExpect(jsonPath("$.points[0].isSelected").value(true));
    }

    @Test
    void getMonthlyTrend_withInvalidPeriod_returns400ProblemDetailWithCode() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = signToken(userId);

        when(statsService.getMonthlyTrend(userId, "2026-13", 5, "INCOME", null))
                .thenThrow(new InvalidRequestException("INVALID_PERIOD", "Kỳ thống kê không đúng định dạng YYYY-MM"));

        mockMvc.perform(get("/api/stats/monthly-trend")
                        .header("Authorization", "Bearer " + token)
                        .param("period", "2026-13")
                        .param("months", "5")
                        .param("type", "INCOME"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"))
                .andExpect(jsonPath("$.detail").value("Kỳ thống kê không đúng định dạng YYYY-MM"));
    }

    @Test
    void getTransactions_withInvalidDateParam_returns400ProblemDetail() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = signToken(userId);

        mockMvc.perform(get("/api/stats/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("period", "2026-08")
                        .param("date", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void getTransactions_withValidDateFilter_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = signToken(userId);
        LocalDate filterDate = LocalDate.of(2026, 8, 22);

        MonthlyTransactionsResponse mockResp = new MonthlyTransactionsResponse(
                "2026-08",
                filterDate,
                "VND",
                8_055_000L,
                23,
                285_000L,
                3,
                List.of()
        );
        when(statsService.getTransactions(userId, "2026-08", filterDate, "EXPENSE", null))
                .thenReturn(mockResp);

        mockMvc.perform(get("/api/stats/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("period", "2026-08")
                        .param("date", "2026-08-22")
                        .param("type", "EXPENSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedDate").value("2026-08-22"))
                .andExpect(jsonPath("$.monthTotalAmount").value(8_055_000L))
                .andExpect(jsonPath("$.filteredTotalAmount").value(285_000L));
    }

    private static String signToken(UUID userId) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(key)
                .compact();
    }
}
