package com.quan.ledgerservice.transaction;

import com.quan.ledgerservice.common.security.CurrentUser;
import com.quan.ledgerservice.transaction.dto.CreateTransactionRequest;
import com.quan.ledgerservice.transaction.dto.MonthlySummaryResponse;
import com.quan.ledgerservice.transaction.dto.TransactionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.time.LocalDate;
import com.quan.ledgerservice.transaction.dto.TransactionHistoryPage;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    private final TransactionHistory history;

    @GetMapping("/history")
    public TransactionHistoryPage getHistory(@CurrentUser UUID userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) UUID walletId, @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) TransactionType type, @RequestParam(required = false) String q) {
        return history.search(userId, page, size, from, to, walletId, categoryId, type, q);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createTransaction(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateTransactionRequest request
    ) {
        return transactionService.create(userId, request);
    }

    @GetMapping
    public Page<TransactionResponse> getTransactions(
            @CurrentUser UUID userId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return transactionService.getTransactions(userId, pageable);
    }

    @GetMapping("/summary")
    public MonthlySummaryResponse getMonthlySummary(@CurrentUser UUID userId) {
        return transactionService.getMonthlySummary(userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTransaction(
            @CurrentUser UUID userId,
            @PathVariable UUID id
    ) {
        transactionService.delete(userId, id);
    }
}
