package com.quan.ledgerservice.transaction;

import com.quan.ledgerservice.transaction.dto.CreateTransactionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class TransactionHistoryIT {
    @Autowired TransactionService service;
    @Autowired TransactionHistory history;

    @Test
    void filtersBeforePagingAndTotalsAndScopesRowsToTheOwner() {
        UUID owner = UUID.randomUUID();
        var request = new CreateTransactionRequest(null, null, TransactionType.EXPENSE, 1000L,
                Instant.parse("2026-11-28T00:00:00Z"), "100%_match", "Trùng tên");
        var first = service.create(owner, request);
        service.create(owner, request);
        var removed = service.create(owner, request);
        service.delete(owner, removed.id());
        service.create(UUID.randomUUID(), request);
        var page = history.search(owner, 1, 1, first.occurredDate(), first.occurredDate(),
                first.walletId(), first.categoryId(), TransactionType.EXPENSE, " %_MATCH ");
        assertThat(page.items()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.totalsByCurrency().get(0).totalExpense()).isEqualTo(2000);
        assertThat(history.search(owner, 0, 1, null, null, null, null, null, "absent").items()).isEmpty();
    }
}
