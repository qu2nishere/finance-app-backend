# Finance App — Backend

Bốn service, mỗi service một database riêng, giao tiếp qua REST (đồng bộ) và
RabbitMQ (bất đồng bộ). Android app chỉ nói chuyện với gateway ở cổng `8080`.

## Vì sao chia như vậy

| Service | Cổng | Sở hữu dữ liệu | Màn hình tương ứng |
|---|---|---|---|
| `user-service` | 8081 | user, credential, session, settings (tiền tệ, thông báo, bảo mật) | Login, Register 1-3, VerifyEmail, Account, Security, Currency, NotificationSettings |
| `ledger-service` | 8082 | **wallet, transaction, category, recurring** | Home, AllTransactions, TransactionDetail, Wallets, AddWallet, AdjustBalance, Categories, Recurring |
| `budget-service` | 8083 | budget, category limit, savings goal, auto-save rule, `spending_snapshot` | Budget, EditBudget, CategoryLimit, SavingsGoals, EditGoal, AutoSave |
| `report-service` | 8084 | read model (bảng denormalized) | Report, StatsCashflow, StatsIncome, Export |
| `gateway` (Nginx) | 8080 | — | entry point duy nhất |

**`ledger-service` gộp wallet + transaction + category có chủ đích.** Mỗi lần thêm
giao dịch phải trừ số dư ví trong *cùng một* DB transaction. Tách wallet ra service
riêng là tự tạo distributed transaction / saga cho bài toán không cần đến nó.

**`report-service` sở hữu read model riêng, không gọi fan-out.** Màn Report có tab
"theo danh mục", "theo ví", "nguồn thu" — nếu mỗi lần mở màn phải gọi 2-3 service rồi
join trong RAM thì vừa chậm vừa giòn. Nó nghe event và tự dựng bảng phẳng của mình.
Đây là CQRS, và là phần đáng học nhất trong repo này.

## Chạy

```bash
cp .env.example .env
# Đặt JWT_SECRET (>= 32 ký tự): openssl rand -base64 48
docker compose up --build
```

- Gateway: http://localhost:8080/health
- RabbitMQ UI: http://localhost:15672
- Từ Android emulator gọi `http://10.0.2.2:8080`; từ máy thật cùng WiFi dùng IP LAN.

Chạy lẻ một service trong IntelliJ: default trong `application.properties` đã trỏ về
`localhost`, chỉ cần `docker compose up postgres rabbitmq` trước.

## Quy tắc bắt buộc

1. **Không service nào đọc bảng của service khác.** Mỗi role Postgres chỉ connect được
   vào đúng DB của mình (xem `infra/postgres/init-databases.sql`). Cần dữ liệu chéo thì
   gọi API hoặc nghe event.
2. **Flyway sở hữu schema.** `spring.jpa.hibernate.ddl-auto=validate` — Hibernate không
   được tự sửa bảng. Migration đặt ở `src/main/resources/db/migration`.
3. **Tiền lưu `BIGINT`, đơn vị đồng.** Không bao giờ dùng `float`/`double`. Frontend đã
   dùng `Long` — giữ nguyên suốt cả stack.

## Còn thiếu

Chưa có entity, repository, controller, migration nào — mới chỉ là khung. Bước tiếp
theo là thiết kế schema từng service + event contract (`TransactionCreated`,
`TransactionDeleted`, `WalletBalanceChanged`, `UserRegistered`).
