-- ===========================================================================
-- Danh muc he thong (user_id IS NULL) — moi user dung chung.
--
-- KHONG copy sang tung user luc dang ky. Query danh muc cua mot user la:
--   WHERE (user_id = :userId OR user_id IS NULL) AND deleted_at IS NULL
--
-- UUID dat co dinh (khong dung gen_random_uuid()) de moi moi truong -
-- may ban, EC2, test - deu co cung id. Report-service map category_id
-- sang ten qua event, id lech nhau la du lieu lech nhau.
-- ===========================================================================

-- --- Danh muc CHI cap 1 -----------------------------------------------------
INSERT INTO categories (id, user_id, parent_id, name, flow, icon, color, is_system, display_order) VALUES
 ('11111111-0000-4000-8000-000000000001', NULL, NULL, 'Ăn uống',               'EXPENSE', 'Storefront',          '#EF4444', true,  1),
 ('11111111-0000-4000-8000-000000000002', NULL, NULL, 'Di chuyển',             'EXPENSE', 'LocalShipping',       '#F97316', true,  2),
 ('11111111-0000-4000-8000-000000000003', NULL, NULL, 'Mua sắm',               'EXPENSE', 'ShoppingBag',         '#EAB308', true,  3),
 ('11111111-0000-4000-8000-000000000004', NULL, NULL, 'Giải trí',              'EXPENSE', 'LocalFireDepartment', '#16A34A', true,  4),
 ('11111111-0000-4000-8000-000000000005', NULL, NULL, 'Sức khoẻ',              'EXPENSE', 'FavoriteBorder',      '#F97316', true,  5),
 ('11111111-0000-4000-8000-000000000006', NULL, NULL, 'Hoá đơn & nhà ở',       'EXPENSE', 'ReceiptLong',         '#64748B', true,  6),
 ('11111111-0000-4000-8000-000000000007', NULL, NULL, 'Điện thoại & internet', 'EXPENSE', 'PhoneIphone',         '#0EA5E9', true,  7),
 ('11111111-0000-4000-8000-000000000008', NULL, NULL, 'Quà tặng',              'EXPENSE', 'CardGiftcard',        '#EC4899', true,  8),
 ('11111111-0000-4000-8000-000000000009', NULL, NULL, 'Thú cưng',              'EXPENSE', 'Inventory2',          '#A855F7', true,  9),
 ('11111111-0000-4000-8000-00000000000a', NULL, NULL, 'Giáo dục',              'EXPENSE', 'Category',            '#2563EB', true, 10),
 ('11111111-0000-4000-8000-00000000000b', NULL, NULL, 'Khác',                  'EXPENSE', 'Category',            '#94A3B8', true, 99);

-- --- Danh muc CHI cap 2 (con cua "Ăn uống") ---------------------------------
-- EditBudgetScreen: "Gồm cả danh mục con · Cà phê · Đi ăn ngoài"
INSERT INTO categories (id, user_id, parent_id, name, flow, icon, color, is_system, display_order) VALUES
 ('11111111-0000-4000-8000-000000000101', NULL, '11111111-0000-4000-8000-000000000001', 'Cà phê',      'EXPENSE', 'LocalCafe',  '#EF4444', true, 1),
 ('11111111-0000-4000-8000-000000000102', NULL, '11111111-0000-4000-8000-000000000001', 'Đi ăn ngoài', 'EXPENSE', 'Restaurant', '#EF4444', true, 2);

-- --- Danh muc THU -----------------------------------------------------------
INSERT INTO categories (id, user_id, parent_id, name, flow, icon, color, is_system, display_order) VALUES
 ('22222222-0000-4000-8000-000000000001', NULL, NULL, 'Lương',     'INCOME', 'Payments',     '#16A34A', true,  1),
 ('22222222-0000-4000-8000-000000000002', NULL, NULL, 'Thưởng',    'INCOME', 'CardGiftcard', '#16A34A', true,  2),
 ('22222222-0000-4000-8000-000000000003', NULL, NULL, 'Freelance', 'INCOME', 'Storefront',   '#16A34A', true,  3),
 ('22222222-0000-4000-8000-000000000004', NULL, NULL, 'Đầu tư',    'INCOME', 'TrendingUp',   '#16A34A', true,  4),
 ('22222222-0000-4000-8000-000000000005', NULL, NULL, 'Được tặng', 'INCOME', 'Redeem',       '#16A34A', true,  5),
 ('22222222-0000-4000-8000-000000000006', NULL, NULL, 'Khác',      'INCOME', 'Category',     '#94A3B8', true, 99);
