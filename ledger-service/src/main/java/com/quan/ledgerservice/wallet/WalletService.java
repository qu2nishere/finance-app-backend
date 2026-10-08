package com.quan.ledgerservice.wallet;

import com.quan.ledgerservice.wallet.dto.CreateWalletRequest;
import com.quan.ledgerservice.wallet.dto.WalletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;

    @Transactional
    public List<WalletResponse> getWallets(UUID userId) {
        List<Wallet> wallets = walletRepository.findByUserIdAndDeletedAtIsNullOrderByDisplayOrderAsc(userId);
        if (wallets.isEmpty()) {
            // Khởi tạo ví mặc định "Tiền mặt" cho user mới
            Wallet defaultWallet = new Wallet();
            defaultWallet.setUserId(userId);
            defaultWallet.setName("Tiền mặt");
            defaultWallet.setKind(WalletKind.CASH);
            defaultWallet.setCurrencyCode("VND");
            defaultWallet.setInitialBalance(0);
            defaultWallet.setCurrentBalance(0);
            defaultWallet.setIcon("AccountBalanceWallet");
            defaultWallet.setColor("#2563EB");
            defaultWallet.setIncludeInTotal(true);
            defaultWallet.setDisplayOrder((short) 1);
            wallets = List.of(walletRepository.save(defaultWallet));
        }
        return wallets.stream().map(WalletResponse::from).toList();
    }

    @Transactional
    public WalletResponse createWallet(UUID userId, CreateWalletRequest request) {
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setName(request.name().trim());
        wallet.setKind(request.kind());
        wallet.setInitialBalance(request.initialBalance());
        wallet.setCurrentBalance(request.initialBalance());
        if (request.icon() != null && !request.icon().isBlank()) {
            wallet.setIcon(request.icon());
        }
        if (request.color() != null && !request.color().isBlank()) {
            wallet.setColor(request.color());
        }
        wallet.setNote(request.note());
        return WalletResponse.from(walletRepository.save(wallet));
    }
}
