-- ===========================================================================
-- Bo sung category_icon va category_color vao fact_transactions de doc danh
-- sach giao dich tren man Thong ke khong phai JOIN dim_category (D4).
-- ===========================================================================

ALTER TABLE fact_transactions
    ADD COLUMN category_icon  VARCHAR(50),
    ADD COLUMN category_color CHAR(7);
