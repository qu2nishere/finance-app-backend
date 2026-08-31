-- Moi service mot database + mot role rieng, khong role nao doc duoc DB cua role khac.
--
-- Dung CHUNG mot container Postgres thay vi 4 container: nhe hon nhieu tren may ca nhan
-- va tren EC2 t3.micro, nhung ranh gioi du lieu van duoc enforce o tang credential -
-- day moi la phan quan trong khi hoc microservice.
--
-- Muon tach han 4 container ve sau: tao 4 service postgres trong docker-compose.yml
-- va doi DB_URL cua tung service, khong phai sua code.

CREATE USER user_db   WITH PASSWORD 'user_db';
CREATE USER ledger_db WITH PASSWORD 'ledger_db';
CREATE USER budget_db WITH PASSWORD 'budget_db';
CREATE USER report_db WITH PASSWORD 'report_db';

CREATE DATABASE user_db   OWNER user_db;
CREATE DATABASE ledger_db OWNER ledger_db;
CREATE DATABASE budget_db OWNER budget_db;
CREATE DATABASE report_db OWNER report_db;

-- Chan cross-service access: khong cho role khac ket noi vao DB khong phai cua minh.
REVOKE CONNECT ON DATABASE user_db   FROM PUBLIC;
REVOKE CONNECT ON DATABASE ledger_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE budget_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE report_db FROM PUBLIC;

GRANT CONNECT ON DATABASE user_db   TO user_db;
GRANT CONNECT ON DATABASE ledger_db TO ledger_db;
GRANT CONNECT ON DATABASE budget_db TO budget_db;
GRANT CONNECT ON DATABASE report_db TO report_db;
