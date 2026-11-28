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
    void detailsRespectIdOwnershipAndSoftDeletion() {
        UUID owner = UUID.randomUUID();
        var req = new CreateTransactionRequest(null, null, TransactionType.ADJUSTMENT, 1000,
                Instant.parse("2026-11-27T17:15:00Z"), null, "Trùng tên");
        var first = service.create(owner, req);
        var second = service.create(owner, req);
        assertThat(service.getTransaction(owner, first.id())).isEqualTo(first);
        assertThat(service.getTransaction(owner, second.id())).isEqualTo(second);
        assertThat(first.categoryId()).isNull();
        assertThat(first.id()).isNotEqualTo(second.id());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getTransaction(UUID.randomUUID(), first.id()))
                .isInstanceOf(com.quan.ledgerservice.common.exception.ResourceNotFoundException.class);
        service.delete(owner, first.id());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getTransaction(owner, first.id()))
                .isInstanceOf(com.quan.ledgerservice.common.exception.ResourceNotFoundException.class);
    }

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
