# Cấu trúc thư mục — Backend

> Quy ước áp dụng cho **cả 4 service**. Mở service nào ra cũng thấy đúng một hình dạng.

---

## 1. Nguyên tắc: chia theo tính năng, không chia theo tầng

Cách chia phổ biến trong sách vở là **theo tầng** — `controller/`, `service/`, `repository/`,
`entity/`. Repo này **không** dùng cách đó. Lý do rất thực tế:

Sửa một tính năng (thêm trường vào ví chẳng hạn) mà chia theo tầng thì bạn phải mở 5 thư mục
khác nhau, mỗi thư mục lấy đúng 1 file. Chia theo tính năng thì toàn bộ nằm trong `wallet/`.
Cái gì thay đổi cùng nhau thì để cạnh nhau.

Quan trọng hơn với microservice: khi một tính năng phình to tới mức phải tách thành service
riêng, bạn kéo nguyên **một thư mục** ra là xong. Chia theo tầng thì phải đi nhặt từng file
trong 5 chỗ.

```
com.quan.<service>/
├── <Service>Application.java     ← điểm khởi động, để nguyên ở gốc package
├── common/                       ← hạ tầng dùng chung TRONG service này
└── <feature>/                    ← mỗi bounded context một thư mục
```

## 2. Bên trong một feature

Để **phẳng**. Không tạo thêm `domain/`, `service/`, `web/` bên trong — service này chỉ có
vài chục file, lồng sâu chỉ tổ phải bấm nhiều.

```
wallet/
├── Wallet.java                   ← @Entity
├── WalletKind.java               ← enum, map @Enumerated(STRING)
├── WalletRepository.java         ← extends JpaRepository
├── WalletService.java            ← @Service, chứa @Transactional
├── WalletController.java         ← @RestController, chỉ nhận/trả DTO
└── dto/
    ├── CreateWalletRequest.java  ← record + @Valid
    └── WalletResponse.java       ← record
```

**Controller không bao giờ trả `@Entity` ra ngoài.** Trả entity là rò rỉ cột nội bộ ra API,
và sẽ vỡ ngay khi bạn đổi tên cột. Luôn map sang `record` trong `dto/`.

## 3. `common/` có gì

```
common/
├── config/       SecurityConfig, RabbitConfig, JacksonConfig, OpenApiConfig
├── security/     JwtAuthFilter, CurrentUserArgumentResolver, @CurrentUser
├── exception/    GlobalExceptionHandler (@RestControllerAdvice), ApiException, ErrorResponse
├── event/        EventEnvelope, OutboxEvent, OutboxPublisher, EventPublisher
└── util/         MoneyUtils, PeriodUtils (tính period_key '2026-08')
```

`common/` là hạ tầng **của riêng service đó**. Bốn service có 4 bản `JwtAuthFilter` gần giống
nhau — trùng lặp đó là **cố ý**. Gom thành thư viện chung nghĩa là 4 service phải deploy lại
cùng lúc mỗi khi sửa nó, tức là mất luôn tính chất "deploy độc lập" của microservice.

## 4. Bản đồ feature từng service

| Service | Feature | Bảng sở hữu |
|---|---|---|
| **user-service** | `user/` | `users` |
| | `auth/` | `credentials`, `verification_tokens`, `sessions`, `devices` |
| | `identity/` | `user_identities` (Google/Apple Sign-In) |
| | `settings/` | `user_settings`, `notification_settings` |
| | `notification/` | `notifications` |
| **ledger-service** | `wallet/` | `wallets`, `balance_adjustments` |
| | `category/` | `categories` |
| | `transaction/` | `transactions`, `transaction_attachments` |
| | `recurring/` | `recurring_rules`, `recurring_runs` |
| | `receipt/` | `receipt_drafts`, `receipt_draft_lines` |
| | `parsing/` | `notification_parse_rules`, `notification_parse_log` |
| **budget-service** | `budget/` | `budgets`, `category_limits`, `spending_snapshot`, `budget_alerts` |
| | `goal/` | `savings_goals`, `goal_deposits` |
| | `autosave/` | `auto_save_rules`, `auto_save_targets`, `auto_save_runs` |
| **report-service** | `ingest/` | consumer dựng `dim_*`, `fact_transactions`, `agg_*` |
| | `report/` | endpoint đọc cho ReportScreen |
| | `stats/` | StatsCashflow, StatsIncome |
| | `export/` | `export_jobs` |

**`wallet/` giữ cả `balance_adjustments`** vì chỉnh số dư là hành vi của ví, không phải một
khái niệm riêng. **`transaction/` giữ `transaction_attachments`** vì ảnh hoá đơn không sống
độc lập được — xoá giao dịch là xoá ảnh.

## 5. Cây thư mục đầy đủ

```
backend/
├── docker-compose.yml
├── .env.example
├── README.md
├── DATABASE.md                    ← thiết kế schema (nguồn sự thật)
├── STRUCTURE.md                   ← file này
│
├── infra/postgres/
│   └── init-databases.sql         ← 4 role + 4 DB + extension (chạy 1 lần duy nhất)
│
├── gateway/
│   ├── nginx.conf
│   └── proxy_common.conf
│
└── <service>/
    ├── build.gradle
    ├── Dockerfile
    └── src/
        ├── main/
        │   ├── java/com/quan/<pkg>/
        │   │   ├── <Service>Application.java
        │   │   ├── common/{config,security,exception,event,util}/
        │   │   └── <feature>/{*.java, dto/}
        │   └── resources/
        │       ├── application.properties
        │       └── db/migration/
        │           ├── V1__init_schema.sql
        │           └── V2__...sql
        └── test/java/com/quan/<pkg>/<feature>/
```

## 6. Quy ước đặt tên file

| Loại | Mẫu | Ví dụ |
|---|---|---|
| Entity | danh từ số ít | `Wallet.java` |
| Repository | `<Entity>Repository` | `WalletRepository.java` |
| Service | `<Entity>Service` | `WalletService.java` |
| Controller | `<Entity>Controller` | `WalletController.java` |
| Request DTO | `<Động từ><Entity>Request` | `CreateWalletRequest.java` |
| Response DTO | `<Entity>Response` | `WalletResponse.java` |
| Event | thì quá khứ | `TransactionCreated.java` |
| Consumer | `<Event>Consumer` | `TransactionCreatedConsumer.java` |
| Test | `<Class>Test` | `WalletServiceTest.java` |

## 7. Thư mục nào KHÔNG nên tạo

- **`model/` hoặc `entity/` ở gốc** — entity thuộc về feature của nó.
- **`impl/`** — `WalletServiceImpl` implement `WalletService` chỉ có một bản là interface thừa.
  Tạo interface khi thật sự có 2 cài đặt, không phải theo thói quen.
- **`helper/`, `manager/`, `misc/`** — tên không nói lên điều gì thì file sẽ trôi vào đó rồi
  không ai dọn nữa.
- **Thư viện chung giữa 4 service** — xem lý do ở mục 3.

---

## Trạng thái hiện tại

Thư mục đã dựng xong, giữ chỗ bằng `.gitkeep`. Migration đã viết đầy đủ. Chưa có file `.java`
nào ngoài `<Service>Application.java` — bước tiếp theo là entity + repository, xoá `.gitkeep`
khi thư mục có file thật.
