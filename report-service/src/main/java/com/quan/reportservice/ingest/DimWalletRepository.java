package com.quan.reportservice.ingest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DimWalletRepository extends JpaRepository<DimWallet, UUID> {

    List<DimWallet> findByUserIdAndDeletedFalseOrderByNameAsc(UUID userId);

    /** Vi da xoa: co trong dim_wallet nhung khong hien, KHONG coi la "thieu du lieu vi". */
    @Query("select w.walletId from DimWallet w where w.userId = :userId and w.deleted = true")
    List<UUID> findDeletedWalletIds(@Param("userId") UUID userId);
}
