package com.quan.ledgerservice.wallet;

import com.quan.ledgerservice.common.security.CurrentUser;
import com.quan.ledgerservice.wallet.dto.CreateWalletRequest;
import com.quan.ledgerservice.wallet.dto.WalletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public List<WalletResponse> getWallets(@CurrentUser UUID userId) {
        return walletService.getWallets(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse createWallet(@CurrentUser UUID userId, @Valid @RequestBody CreateWalletRequest request) {
        return walletService.createWallet(userId, request);
    }
}
