package com.quan.ledgerservice.wallet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select w from Wallet w where w.id = :id and w.userId = :userId and w.deletedAt is null")
    Optional<Wallet> lockActive(UUID id, UUID userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<Wallet> findFirstByUserIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(UUID userId);

    // A missing default wallet has no row to lock yet. Both lazy creation paths use this key.
    @org.springframework.data.jpa.repository.Query(value = """
            select 1 from pg_advisory_xact_lock(hashtextextended(cast(:userId as text), 0))
            """, nativeQuery = true)
    int lockDefaultWallet(UUID userId);

    List<Wallet> findByUserIdAndDeletedAtIsNullOrderByDisplayOrderAsc(UUID userId);

    Optional<Wallet> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
