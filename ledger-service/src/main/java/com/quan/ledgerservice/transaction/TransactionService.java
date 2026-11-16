package com.quan.ledgerservice.transaction;

import com.quan.ledgerservice.category.Category;
import com.quan.ledgerservice.category.CategoryFlow;
import com.quan.ledgerservice.category.CategoryRepository;
import com.quan.ledgerservice.common.exception.ResourceNotFoundException;
import com.quan.ledgerservice.transaction.dto.CreateTransactionRequest;
import com.quan.ledgerservice.transaction.dto.MonthlySummaryResponse;
import com.quan.ledgerservice.transaction.dto.TransactionResponse;
import com.quan.ledgerservice.wallet.Wallet;
import com.quan.ledgerservice.wallet.WalletKind;
import com.quan.ledgerservice.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;

    private static final ZoneId USER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Transactional
    public TransactionResponse create(UUID userId, CreateTransactionRequest request) {
        // 1. Ví: tìm theo walletId của user, nếu chưa có thì lấy ví đầu tiên hoặc tự tạo ví mặc định "Tiền mặt"
        Wallet wallet = null;
        if (request.walletId() != null) {
            wallet = walletRepository.findByIdAndUserIdAndDeletedAtIsNull(request.walletId(), userId)
                    .orElse(null);
        }

        if (wallet == null) {
            List<Wallet> userWallets = walletRepository.findByUserIdAndDeletedAtIsNullOrderByDisplayOrderAsc(userId);
            if (!userWallets.isEmpty()) {
                wallet = userWallets.get(0);
            } else {
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
                wallet = walletRepository.save(defaultWallet);
            }
        }

        // 2. Danh mục: tìm theo categoryId, nếu không khớp thì tự lấy danh mục mặc định phù hợp với loại thu/chi
        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findByIdAndDeletedAtIsNull(request.categoryId())
                    .filter(c -> c.getUserId() == null || c.getUserId().equals(userId))
                    .orElse(null);
        }

        if (category == null) {
            CategoryFlow expectedFlow = request.type() == TransactionType.EXPENSE ? CategoryFlow.EXPENSE : CategoryFlow.INCOME;
            category = categoryRepository.findVisible(userId)
                    .stream()
                    .filter(c -> c.getFlow() == expectedFlow)
                    .findFirst()
                    .orElse(null);
        }

        // 3. Tính signed_amount và lưu giao dịch
        long signedAmount = request.amount() * request.type().sign();

        Transaction txn = new Transaction();
        txn.setUserId(userId);
        txn.setWalletId(wallet.getId());
        if (category != null) {
            txn.setCategoryId(category.getId());
        }
        txn.setType(request.type());
        txn.setAmount(request.amount());
        txn.setSignedAmount(signedAmount);
        txn.setCurrencyCode(wallet.getCurrencyCode());
        txn.setOccurredAt(request.occurredAt());
        txn.setOccurredDate(LocalDate.ofInstant(request.occurredAt(), USER_ZONE));
        txn.setNote(request.note());
        txn.setMerchant(request.merchant());

        Transaction saved = transactionRepository.save(txn);

        // 4. Cộng/trừ số dư ví trong cùng một database transaction
        wallet.setCurrentBalance(wallet.getCurrentBalance() + signedAmount);

        // 5. Tăng lượt dùng danh mục để ưu tiên hiển thị
        if (category != null) {
            category.setUsageCount(category.getUsageCount() + 1);
        }

        return TransactionResponse.from(saved, wallet, category);
    }

    @Transactional
    public void delete(UUID userId, UUID txnId) {
        Transaction txn = transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(txnId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch"));

        Wallet wallet = walletRepository.findByIdAndUserIdAndDeletedAtIsNull(txn.getWalletId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ví tương ứng"));

        // Hoàn lại số dư ví
        wallet.setCurrentBalance(wallet.getCurrentBalance() - txn.getSignedAmount());
        txn.setDeletedAt(Instant.now());
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(UUID userId, Pageable pageable) {
        Page<Transaction> page = transactionRepository.findByUserIdAndDeletedAtIsNullOrderByOccurredAtDesc(userId, pageable);

        Map<UUID, Wallet> walletMap = walletRepository.findByUserIdAndDeletedAtIsNullOrderByDisplayOrderAsc(userId)
                .stream().collect(Collectors.toMap(Wallet::getId, w -> w));

        Map<UUID, Category> categoryMap = categoryRepository.findVisible(userId)
                .stream().collect(Collectors.toMap(Category::getId, c -> c));

        return page.map(txn -> TransactionResponse.from(
                txn,
                walletMap.get(txn.getWalletId()),
                txn.getCategoryId() != null ? categoryMap.get(txn.getCategoryId()) : null
        ));
    }

    @Transactional(readOnly = true)
    public MonthlySummaryResponse getMonthlySummary(UUID userId) {
        YearMonth currentMonth = YearMonth.now(USER_ZONE);
        LocalDate start = currentMonth.atDay(1);
        LocalDate end = currentMonth.atEndOfMonth();

        long income = transactionRepository.sumAmountByTypeAndPeriod(userId, TransactionType.INCOME, start, end);
        long expense = transactionRepository.sumAmountByTypeAndPeriod(userId, TransactionType.EXPENSE, start, end);

        return new MonthlySummaryResponse(income, expense, income - expense);
    }
}
