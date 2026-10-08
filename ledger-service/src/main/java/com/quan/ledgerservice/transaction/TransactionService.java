package com.quan.ledgerservice.transaction;

import com.quan.ledgerservice.category.Category;
import com.quan.ledgerservice.category.CategoryFlow;
import com.quan.ledgerservice.category.CategoryRepository;
import com.quan.ledgerservice.common.exception.InvalidRequestException;
import com.quan.ledgerservice.common.exception.ResourceNotFoundException;
import com.quan.ledgerservice.transaction.dto.CreateTransactionRequest;
import com.quan.ledgerservice.transaction.dto.MonthlySummaryResponse;
import com.quan.ledgerservice.transaction.dto.TransactionResponse;
import com.quan.ledgerservice.wallet.Wallet;
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
        // 1. Vi phai ton tai va thuoc ve user
        Wallet wallet = walletRepository.findByIdAndUserIdAndDeletedAtIsNull(request.walletId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ví hợp lệ"));

        // 2. Danh muc (neu co) phai nhin thay duoc va dung chieu thu/chi
        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findByIdAndDeletedAtIsNull(request.categoryId())
                    .filter(c -> c.getUserId() == null || c.getUserId().equals(userId))
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục hợp lệ"));

            if (request.type() == TransactionType.EXPENSE && category.getFlow() != CategoryFlow.EXPENSE) {
                throw new InvalidRequestException("Danh mục này không dành cho khoản chi");
            }
            if (request.type() == TransactionType.INCOME && category.getFlow() != CategoryFlow.INCOME) {
                throw new InvalidRequestException("Danh mục này không dành cho khoản thu");
            }
        }

        // 3. Tinh signed_amount va luu giao dich
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

        // 4. Cong/tru so du vi trong cung mot database transaction
        wallet.setCurrentBalance(wallet.getCurrentBalance() + signedAmount);

        // 5. Tang luot dung danh muc de uu tien hien thi
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

        // Hoan lai so du vi
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
