package com.quan.ledgerservice.wallet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    List<Wallet> findByUserIdAndDeletedAtIsNullOrderByDisplayOrderAsc(UUID userId);

    Optional<Wallet> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
