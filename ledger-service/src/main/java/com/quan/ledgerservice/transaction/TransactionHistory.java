package com.quan.ledgerservice.transaction;

import com.quan.ledgerservice.category.Category;
import com.quan.ledgerservice.common.exception.InvalidRequestException;
import com.quan.ledgerservice.transaction.dto.TransactionHistoryPage;
import com.quan.ledgerservice.transaction.dto.TransactionResponse;
import com.quan.ledgerservice.wallet.Wallet;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionHistory {
    private final EntityManager entities;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TransactionHistoryPage search(UUID userId, int page, int size, LocalDate from, LocalDate to,
            UUID walletId, UUID categoryId, TransactionType type, String q) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new InvalidRequestException("Trang hoặc kích thước trang không hợp lệ");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc");
        }

        StringBuilder filter = new StringBuilder("""
                from Transaction t
                left join Wallet w on w.id = t.walletId and w.userId = :userId and w.deletedAt is null
                left join Category c on c.id = t.categoryId and (c.userId is null or c.userId = :userId)
                    and c.deletedAt is null
                where t.userId = :userId and t.deletedAt is null
                """);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("userId", userId);
        add(filter, params, "t.occurredDate >= :fromDate", "fromDate", from);
        add(filter, params, "t.occurredDate <= :toDate", "toDate", to);
        add(filter, params, "t.walletId = :walletId", "walletId", walletId);
        add(filter, params, "t.categoryId = :categoryId", "categoryId", categoryId);
        add(filter, params, "t.type = :type", "type", type);
        if (q != null && !q.isBlank()) {
            add(filter, params, """
                    (locate(lower(:q), lower(t.merchant)) > 0 or locate(lower(:q), lower(t.note)) > 0
                     or locate(lower(:q), lower(coalesce(c.name, 'Chưa phân loại'))) > 0
                     or locate(:q, cast(t.amount as string)) > 0)
                    """, "q", q.strip());
        }

        var items = query("select t, w, c " + filter + " order by t.occurredAt desc, t.id desc", params)
                .setFirstResult(page * size).setMaxResults(size).getResultList().stream()
                .map(row -> TransactionResponse.from((Transaction) row[0], (Wallet) row[1], (Category) row[2]))
                .toList();
        var groups = query("""
                select t.currencyCode, count(t),
                    sum(case when t.type = INCOME then t.amount else 0L end),
                    sum(case when t.type = EXPENSE then t.amount else 0L end)
                """ + filter + " group by t.currencyCode order by t.currencyCode", params).getResultList();
        long count = groups.stream().mapToLong(row -> (Long) row[1]).sum();
        var totals = groups.stream().map(row -> new TransactionHistoryPage.CurrencyTotals(
                ((String) row[0]).trim(), (Long) row[2], (Long) row[3])).toList();
        return new TransactionHistoryPage(items, page, size, count, (count + size - 1) / size, totals);
    }

    private static void add(StringBuilder filter, Map<String, Object> params,
            String condition, String name, Object value) {
        if (value != null) {
            filter.append(" and ").append(condition);
            params.put(name, value);
        }
    }

    private TypedQuery<Object[]> query(String hql, Map<String, Object> params) {
        var query = entities.createQuery(hql, Object[].class);
        params.forEach(query::setParameter);
        return query;
    }
}
