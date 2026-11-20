package com.quan.reportservice.ingest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DimWalletRepository extends JpaRepository<DimWallet, UUID> {

    List<DimWallet> findByUserIdAndDeletedFalseOrderByNameAsc(UUID userId);
}
