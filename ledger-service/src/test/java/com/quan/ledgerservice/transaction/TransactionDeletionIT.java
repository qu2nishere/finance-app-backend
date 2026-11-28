package com.quan.ledgerservice.transaction;

import com.quan.ledgerservice.common.event.OutboxEventRepository;
import com.quan.ledgerservice.common.exception.ResourceNotFoundException;
import com.quan.ledgerservice.transaction.dto.CreateTransactionRequest;
import com.quan.ledgerservice.transaction.dto.TransactionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class TransactionDeletionIT {
    @Autowired TransactionService service;
    @Autowired JdbcTemplate db;
    @MockitoSpyBean OutboxEventRepository outbox;
    private final UUID user = UUID.randomUUID();

    private TransactionResponse create(UUID wallet, TransactionType type) {
        return service.create(user, new CreateTransactionRequest(wallet, null, type, 1000,
                Instant.now(), null, "Concurrency QA"));
    }

    private boolean delete(UUID id) {
        try {
            service.delete(user, id);
            return true;
        } catch (ResourceNotFoundException alreadyRemoved) {
            return false;
        }
    }

    private long balance(UUID wallet) {
        return db.queryForObject("select current_balance from wallets where id=? and user_id=?", Long.class, wallet, user);
    }

    private long events(UUID id) {
        return db.queryForObject("""
                select count(*) from outbox_events where aggregate_id=? and event_type='TransactionDeleted'
                """, Long.class, id);
    }

    private int race(Callable<Boolean> first, Callable<Boolean> second) throws Exception {
        var barrier = new CyclicBarrier(2);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var a = workers.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return first.call(); });
            var b = workers.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return second.call(); });
            return (a.get(15, TimeUnit.SECONDS) ? 1 : 0) + (b.get(15, TimeUnit.SECONDS) ? 1 : 0);
        } finally {
            workers.shutdownNow();
        }
    }

    @Test void duplicateDeleteReversesOnce() throws Exception {
        var row = create(null, TransactionType.EXPENSE);
        assertThat(race(() -> delete(row.id()), () -> delete(row.id()))).isOne();
        assertThat(balance(row.walletId())).isZero();
        assertThat(events(row.id())).isOne();
    }

    @Test void createAndDeleteOnSameWalletKeepBothDeltas() throws Exception {
        var row = create(null, TransactionType.INCOME);
        assertThat(race(() -> delete(row.id()), () -> create(row.walletId(), TransactionType.EXPENSE) != null)).isEqualTo(2);
        assertThat(balance(row.walletId())).isEqualTo(-1000);
        assertThat(events(row.id())).isOne();
    }

    @Test void concurrentFirstTransactionsShareDefaultWallet() throws Exception {
        race(() -> create(null, TransactionType.INCOME) != null, () -> create(null, TransactionType.INCOME) != null);
        assertThat(db.queryForList("select current_balance from wallets where user_id=?", Long.class, user))
                .containsExactly(2000L);
    }

    @Test void failedOutboxWriteRollsBackWalletAndSoftDelete() {
        var row = create(null, TransactionType.EXPENSE);
        doThrow(new IllegalStateException("Test outbox failure")).when(outbox).save(argThat(
                event -> event != null && row.id().equals(event.getAggregateId()) && "TransactionDeleted".equals(event.getEventType())));
        assertThatThrownBy(() -> service.delete(user, row.id())).isInstanceOf(RuntimeException.class);
        assertThat(service.getTransaction(user, row.id()).id()).isEqualTo(row.id());
        assertThat(balance(row.walletId())).isEqualTo(-1000);
        assertThat(events(row.id())).isZero();
    }
}
