-- INV 演示数据（幂等：已存在则跳过）

-- ===================== 纳税主体 =====================
INSERT INTO inv_tax_entity (code, name, tax_no, address, phone, bank_name, bank_account, taxpayer_type, drawer, payee, reviewer, status, created_at, updated_at)
SELECT 'HQ', '云途科技股份有限公司', '91310000780000001A', '上海市浦东新区张江高科技园区博云路100号', '021-58880001', '中国工商银行上海张江支行', '1001200000000000001', 'GENERAL', '王开票', '李收款', '赵复核', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_tax_entity WHERE code = 'HQ');
INSERT INTO inv_tax_entity (code, name, tax_no, address, phone, bank_name, bank_account, taxpayer_type, drawer, payee, reviewer, status, created_at, updated_at)
SELECT 'SZ', '云途科技深圳分公司', '91440300780000002B', '深圳市南山区科技园南路50号', '0755-26660002', '招商银行深圳科技园支行', '2002300000000000002', 'GENERAL', '王开票', '李收款', '赵复核', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_tax_entity WHERE code = 'SZ');

-- 各票种单张开票限额
INSERT INTO inv_tax_entity_limit (tax_entity_id, invoice_type, max_amount)
SELECT (SELECT id FROM inv_tax_entity WHERE code = 'HQ'), 'SPECIAL', 1000000
WHERE NOT EXISTS (SELECT 1 FROM inv_tax_entity_limit WHERE tax_entity_id = (SELECT id FROM inv_tax_entity WHERE code = 'HQ') AND invoice_type = 'SPECIAL');
INSERT INTO inv_tax_entity_limit (tax_entity_id, invoice_type, max_amount)
SELECT (SELECT id FROM inv_tax_entity WHERE code = 'HQ'), 'NORMAL', 100000
WHERE NOT EXISTS (SELECT 1 FROM inv_tax_entity_limit WHERE tax_entity_id = (SELECT id FROM inv_tax_entity WHERE code = 'HQ') AND invoice_type = 'NORMAL');
INSERT INTO inv_tax_entity_limit (tax_entity_id, invoice_type, max_amount)
SELECT (SELECT id FROM inv_tax_entity WHERE code = 'HQ'), 'E_NORMAL', 100000
WHERE NOT EXISTS (SELECT 1 FROM inv_tax_entity_limit WHERE tax_entity_id = (SELECT id FROM inv_tax_entity WHERE code = 'HQ') AND invoice_type = 'E_NORMAL');
INSERT INTO inv_tax_entity_limit (tax_entity_id, invoice_type, max_amount)
SELECT (SELECT id FROM inv_tax_entity WHERE code = 'HQ'), 'E_SPECIAL', 1000000
WHERE NOT EXISTS (SELECT 1 FROM inv_tax_entity_limit WHERE tax_entity_id = (SELECT id FROM inv_tax_entity WHERE code = 'HQ') AND invoice_type = 'E_SPECIAL');
INSERT INTO inv_tax_entity_limit (tax_entity_id, invoice_type, max_amount)
SELECT (SELECT id FROM inv_tax_entity WHERE code = 'HQ'), 'ALL_ELECTRIC', 1000000
WHERE NOT EXISTS (SELECT 1 FROM inv_tax_entity_limit WHERE tax_entity_id = (SELECT id FROM inv_tax_entity WHERE code = 'HQ') AND invoice_type = 'ALL_ELECTRIC');
INSERT INTO inv_tax_entity_limit (tax_entity_id, invoice_type, max_amount)
SELECT (SELECT id FROM inv_tax_entity WHERE code = 'SZ'), 'E_NORMAL', 100000
WHERE NOT EXISTS (SELECT 1 FROM inv_tax_entity_limit WHERE tax_entity_id = (SELECT id FROM inv_tax_entity WHERE code = 'SZ') AND invoice_type = 'E_NORMAL');
INSERT INTO inv_tax_entity_limit (tax_entity_id, invoice_type, max_amount)
SELECT (SELECT id FROM inv_tax_entity WHERE code = 'SZ'), 'ALL_ELECTRIC', 500000
WHERE NOT EXISTS (SELECT 1 FROM inv_tax_entity_limit WHERE tax_entity_id = (SELECT id FROM inv_tax_entity WHERE code = 'SZ') AND invoice_type = 'ALL_ELECTRIC');

-- ===================== 往来单位 =====================
INSERT INTO inv_partner (type, name, tax_no, address, phone, bank_name, bank_account, email, mobile, status, created_at, updated_at)
SELECT 'CUSTOMER', '华东电商科技有限公司', '91310000MA1K00001X', '上海市静安区南京西路1000号', '021-62000001', '中国银行上海静安支行', '310100000000000001', 'ap@ec-hd.example.com', '13800000001', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_partner WHERE name = '华东电商科技有限公司');
INSERT INTO inv_partner (type, name, tax_no, address, phone, bank_name, bank_account, email, mobile, status, created_at, updated_at)
SELECT 'CUSTOMER', '北方快消品牌有限公司', '91110000MA1K00002X', '北京市朝阳区建国路88号', '010-65000002', '工商银行北京朝阳支行', '110100000000000002', 'fin@nk.example.com', '13800000002', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_partner WHERE name = '北方快消品牌有限公司');
INSERT INTO inv_partner (type, name, tax_no, address, phone, bank_name, bank_account, email, mobile, status, created_at, updated_at)
SELECT 'CUSTOMER', '南方家电连锁集团', '91440000MA1K00003X', '广州市天河区体育西路10号', '020-38000003', '建设银行广州天河支行', '440100000000000003', 'ap@nfjd.example.com', '13800000003', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_partner WHERE name = '南方家电连锁集团');
INSERT INTO inv_partner (type, name, tax_no, address, phone, bank_name, bank_account, email, mobile, status, created_at, updated_at)
SELECT 'CUSTOMER', '西部智造股份公司', '91510000MA1K00004X', '成都市高新区天府大道1号', '028-85000004', '交通银行成都高新支行', '510100000000000004', 'bill@xbzz.example.com', '13800000004', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_partner WHERE name = '西部智造股份公司');
INSERT INTO inv_partner (type, name, tax_no, address, phone, bank_name, bank_account, email, mobile, status, created_at, updated_at)
SELECT 'CUSTOMER', '个人消费者', NULL, NULL, NULL, NULL, NULL, NULL, '13900000005', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_partner WHERE name = '个人消费者');
INSERT INTO inv_partner (type, name, tax_no, address, phone, bank_name, bank_account, email, mobile, status, created_at, updated_at)
SELECT 'SUPPLIER', '恒信电子元件有限公司', '91320000MA5K00005X', '苏州市工业园区金鸡湖大道99号', '0512-67000005', '农业银行苏州园区支行', '320100000000000005', 'sales@hxdz.example.com', '13900000006', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_partner WHERE name = '恒信电子元件有限公司');
INSERT INTO inv_partner (type, name, tax_no, address, phone, bank_name, bank_account, email, mobile, status, created_at, updated_at)
SELECT 'SUPPLIER', '中诚办公用品有限公司', '91110100MA5K00006X', '北京市海淀区中关村大街20号', '010-82000006', '中国银行北京海淀支行', '110200000000000006', 'order@zc.example.com', '13900000007', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_partner WHERE name = '中诚办公用品有限公司');
INSERT INTO inv_partner (type, name, tax_no, address, phone, bank_name, bank_account, email, mobile, status, created_at, updated_at)
SELECT 'BOTH', '环球物流服务有限公司', '91440300MA5K00007X', '深圳市福田区福华路200号', '0755-83000007', '平安银行深圳福田支行', '440300000000000007', 'billing@hqwl.example.com', '13900000008', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_partner WHERE name = '环球物流服务有限公司');

-- ===================== 商品/服务 =====================
INSERT INTO inv_goods (code, name, tax_category_code, tax_category_name, spec, unit, price, tax_rate, preferential_policy, status, created_at, updated_at)
SELECT 'G-CPU', '服务器 CPU', '1090511010000000000', '计算机零部件', 'Xeon 8375C', '颗', 12000.00, 0.13, 'NONE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_goods WHERE code = 'G-CPU');
INSERT INTO inv_goods (code, name, tax_category_code, tax_category_name, spec, unit, price, tax_rate, preferential_policy, status, created_at, updated_at)
SELECT 'G-MEM', '内存条', '1090511020000000000', '计算机零部件', 'DDR5 64G', '条', 1500.00, 0.13, 'NONE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_goods WHERE code = 'G-MEM');
INSERT INTO inv_goods (code, name, tax_category_code, tax_category_name, spec, unit, price, tax_rate, preferential_policy, status, created_at, updated_at)
SELECT 'G-SSD', '固态硬盘', '1090511030000000000', '存储设备', 'NVMe 4T', '块', 2800.00, 0.13, 'NONE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_goods WHERE code = 'G-SSD');
INSERT INTO inv_goods (code, name, tax_category_code, tax_category_name, spec, unit, price, tax_rate, preferential_policy, status, created_at, updated_at)
SELECT 'G-NB', '笔记本电脑', '1090512010000000000', '计算机整机', 'Pro 14', '台', 6999.00, 0.13, 'NONE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_goods WHERE code = 'G-NB');
INSERT INTO inv_goods (code, name, tax_category_code, tax_category_name, spec, unit, price, tax_rate, preferential_policy, status, created_at, updated_at)
SELECT 'S-SOFT', '软件开发服务', '1060201030000000000', '信息技术服务', '人天', '人天', 3000.00, 0.06, 'NONE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_goods WHERE code = 'S-SOFT');
INSERT INTO inv_goods (code, name, tax_category_code, tax_category_name, spec, unit, price, tax_rate, preferential_policy, status, created_at, updated_at)
SELECT 'S-CONSULT', '技术咨询服务', '1060201040000000000', '鉴证咨询服务', '项', '项', 50000.00, 0.06, 'NONE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_goods WHERE code = 'S-CONSULT');
INSERT INTO inv_goods (code, name, tax_category_code, tax_category_name, spec, unit, price, tax_rate, preferential_policy, status, created_at, updated_at)
SELECT 'S-MAINT', '运维服务', '1060201050000000000', '信息技术服务', '年', '年', 120000.00, 0.06, 'NONE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_goods WHERE code = 'S-MAINT');
INSERT INTO inv_goods (code, name, tax_category_code, tax_category_name, spec, unit, price, tax_rate, preferential_policy, status, created_at, updated_at)
SELECT 'S-TRANS', '运输服务', '3010101000000000000', '运输服务', '批', '批', 5000.00, 0.09, 'NONE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_goods WHERE code = 'S-TRANS');
INSERT INTO inv_goods (code, name, tax_category_code, tax_category_name, spec, unit, price, tax_rate, preferential_policy, status, created_at, updated_at)
SELECT 'G-PAPER', '复印纸', '1060101000000000000', '纸制品', 'A4 70g', '箱', 120.00, 0.13, 'NONE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_goods WHERE code = 'G-PAPER');
INSERT INTO inv_goods (code, name, tax_category_code, tax_category_name, spec, unit, price, tax_rate, preferential_policy, status, created_at, updated_at)
SELECT 'G-PRINT', '激光打印机', '1090513010000000000', '办公设备', 'M404', '台', 1800.00, 0.13, 'NONE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_goods WHERE code = 'G-PRINT');
INSERT INTO inv_goods (code, name, tax_category_code, tax_category_name, spec, unit, price, tax_rate, preferential_policy, status, created_at, updated_at)
SELECT 'S-FREE', '技术转让（免税）', '1060201060000000000', '技术转让', '项', '项', 200000.00, 0, 'FREE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_goods WHERE code = 'S-FREE');
INSERT INTO inv_goods (code, name, tax_category_code, tax_category_name, spec, unit, price, tax_rate, preferential_policy, status, created_at, updated_at)
SELECT 'G-MOUSE', '无线鼠标', '1090514010000000000', '计算机外设', 'M330', '个', 89.00, 0.13, 'NONE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_goods WHERE code = 'G-MOUSE');

-- ===================== 发票号段 =====================
INSERT INTO inv_invoice_stock (tax_entity_id, invoice_type, invoice_code, start_no, end_no, current_no, remaining, status, created_at, updated_at)
SELECT (SELECT id FROM inv_tax_entity WHERE code = 'HQ'), 'SPECIAL', '031002300111', '00001001', '00001100', '00001001', 100, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_invoice_stock WHERE tax_entity_id = (SELECT id FROM inv_tax_entity WHERE code = 'HQ') AND invoice_type = 'SPECIAL');
INSERT INTO inv_invoice_stock (tax_entity_id, invoice_type, invoice_code, start_no, end_no, current_no, remaining, status, created_at, updated_at)
SELECT (SELECT id FROM inv_tax_entity WHERE code = 'HQ'), 'NORMAL', '031002300112', '00002001', '00002100', '00002001', 100, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_invoice_stock WHERE tax_entity_id = (SELECT id FROM inv_tax_entity WHERE code = 'HQ') AND invoice_type = 'NORMAL');
INSERT INTO inv_invoice_stock (tax_entity_id, invoice_type, invoice_code, start_no, end_no, current_no, remaining, status, created_at, updated_at)
SELECT (SELECT id FROM inv_tax_entity WHERE code = 'HQ'), 'E_NORMAL', '031002300113', '00003001', '00003100', '00003001', 100, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_invoice_stock WHERE tax_entity_id = (SELECT id FROM inv_tax_entity WHERE code = 'HQ') AND invoice_type = 'E_NORMAL');
INSERT INTO inv_invoice_stock (tax_entity_id, invoice_type, invoice_code, start_no, end_no, current_no, remaining, status, created_at, updated_at)
SELECT (SELECT id FROM inv_tax_entity WHERE code = 'HQ'), 'E_SPECIAL', '031002300114', '00004001', '00004040', '00004001', 40, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_invoice_stock WHERE tax_entity_id = (SELECT id FROM inv_tax_entity WHERE code = 'HQ') AND invoice_type = 'E_SPECIAL');
INSERT INTO inv_invoice_stock (tax_entity_id, invoice_type, invoice_code, start_no, end_no, current_no, remaining, status, created_at, updated_at)
SELECT (SELECT id FROM inv_tax_entity WHERE code = 'SZ'), 'E_NORMAL', '031002300211', '00005001', '00005100', '00005001', 100, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_invoice_stock WHERE tax_entity_id = (SELECT id FROM inv_tax_entity WHERE code = 'SZ') AND invoice_type = 'E_NORMAL');

-- ===================== 税务属期 =====================
INSERT INTO inv_tax_period (tax_entity_id, period, status, created_at, updated_at)
SELECT (SELECT id FROM inv_tax_entity WHERE code = 'HQ'), TO_CHAR(CURRENT_DATE, 'YYYY-MM'), 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_tax_period WHERE tax_entity_id = (SELECT id FROM inv_tax_entity WHERE code = 'HQ') AND period = TO_CHAR(CURRENT_DATE, 'YYYY-MM'));
INSERT INTO inv_tax_period (tax_entity_id, period, status, created_at, updated_at)
SELECT (SELECT id FROM inv_tax_entity WHERE code = 'HQ'), TO_CHAR(DATEADD('MONTH', -1, CURRENT_DATE), 'YYYY-MM'), 'CLOSED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_tax_period WHERE tax_entity_id = (SELECT id FROM inv_tax_entity WHERE code = 'HQ') AND period = TO_CHAR(DATEADD('MONTH', -1, CURRENT_DATE), 'YYYY-MM'));
INSERT INTO inv_tax_period (tax_entity_id, period, status, created_at, updated_at)
SELECT (SELECT id FROM inv_tax_entity WHERE code = 'SZ'), TO_CHAR(CURRENT_DATE, 'YYYY-MM'), 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_tax_period WHERE tax_entity_id = (SELECT id FROM inv_tax_entity WHERE code = 'SZ') AND period = TO_CHAR(CURRENT_DATE, 'YYYY-MM'));

-- ===================== 演示开票申请（APPROVED 待开） =====================
INSERT INTO inv_invoice_request (request_no, tax_entity_id, customer_id, invoice_type, buyer_name, buyer_tax_no, buyer_address_phone, buyer_bank, source, total_amount, total_tax, total_with_tax, status, created_at, updated_at)
SELECT 'REQ-DEMO-0001', (SELECT id FROM inv_tax_entity WHERE code = 'HQ'), (SELECT id FROM inv_partner WHERE name = '华东电商科技有限公司'),
       'E_NORMAL', '华东电商科技有限公司', '91310000MA1K00001X', '上海市静安区南京西路1000号 021-62000001', '中国银行上海静安支行 310100000000000001',
       'MANUAL', 10000.00, 1300.00, 11300.00, 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_invoice_request WHERE request_no = 'REQ-DEMO-0001');
INSERT INTO inv_invoice_request_line (request_id, goods_id, goods_name, tax_category_code, spec, unit, quantity, unit_price, amount, tax_rate, tax_amount, created_at)
SELECT (SELECT id FROM inv_invoice_request WHERE request_no = 'REQ-DEMO-0001'), (SELECT id FROM inv_goods WHERE code = 'G-NB'),
       '笔记本电脑', '1090512010000000000', 'Pro 14', '台', 2, 5000.00, 10000.00, 0.13, 1300.00, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_invoice_request_line WHERE request_id = (SELECT id FROM inv_invoice_request WHERE request_no = 'REQ-DEMO-0001'));

-- ===================== 演示已开发票 =====================
INSERT INTO inv_invoice (invoice_code, invoice_no, invoice_type, tax_entity_id, buyer_name, buyer_tax_no, buyer_address_phone, buyer_bank,
    seller_name, seller_tax_no, issue_date, total_amount, total_tax, total_with_tax, check_code, status, machine_no, drawer, payee, reviewer, created_at, updated_at)
SELECT '031002300112', '00002000', 'NORMAL', (SELECT id FROM inv_tax_entity WHERE code = 'HQ'),
       '北方快消品牌有限公司', '91110000MA1K00002X', '北京市朝阳区建国路88号 010-65000002', '工商银行北京朝阳支行 110100000000000002',
       '云途科技股份有限公司', '91310000780000001A', CURRENT_DATE, 5000.00, 650.00, 5650.00, '12345678901234567890', 'ISSUED', 'MACH-001', '王开票', '李收款', '赵复核', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_invoice WHERE invoice_code = '031002300112' AND invoice_no = '00002000');
INSERT INTO inv_invoice_line (invoice_id, goods_name, tax_category_code, spec, unit, quantity, unit_price, amount, tax_rate, tax_amount, created_at)
SELECT (SELECT id FROM inv_invoice WHERE invoice_code = '031002300112' AND invoice_no = '00002000'),
       '运输服务', '3010101000000000000', '批', '批', 1, 5000.00, 5000.00, 0.13, 650.00, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_invoice_line WHERE invoice_id = (SELECT id FROM inv_invoice WHERE invoice_code = '031002300112' AND invoice_no = '00002000'));

-- ===================== 演示进项发票（各状态） =====================
INSERT INTO inv_input_invoice (source, invoice_type, invoice_code, invoice_no, issue_date, seller_name, seller_tax_no, buyer_name, buyer_tax_no,
    total_amount, total_tax, total_with_tax, check_code, tax_entity_id, verify_status, status, deduct_status, created_at, updated_at)
SELECT 'MANUAL', 'SPECIAL', '032002300001', '10001001', CURRENT_DATE, '恒信电子元件有限公司', '91320000MA5K00005X',
       '云途科技股份有限公司', '91310000780000001A', 20000.00, 2600.00, 22600.00, '88888888888888888888',
       (SELECT id FROM inv_tax_entity WHERE code = 'HQ'), 'UNVERIFIED', 'NORMAL', 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_input_invoice WHERE invoice_code = '032002300001' AND invoice_no = '10001001');
INSERT INTO inv_input_invoice (source, invoice_type, invoice_code, invoice_no, issue_date, seller_name, seller_tax_no, buyer_name, buyer_tax_no,
    total_amount, total_tax, total_with_tax, check_code, tax_entity_id, verify_status, status, deduct_status, created_at, updated_at)
SELECT 'IMPORT', 'E_SPECIAL', '032002300002', '10001002', CURRENT_DATE, '中诚办公用品有限公司', '91110100MA5K00006X',
       '云途科技股份有限公司', '91310000780000001A', 3000.00, 390.00, 3390.00, '77777777777777777777',
       (SELECT id FROM inv_tax_entity WHERE code = 'HQ'), 'UNVERIFIED', 'NORMAL', 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_input_invoice WHERE invoice_code = '032002300002' AND invoice_no = '10001002');
INSERT INTO inv_input_invoice (source, invoice_type, invoice_code, invoice_no, issue_date, seller_name, seller_tax_no, buyer_name, buyer_tax_no,
    total_amount, total_tax, total_with_tax, check_code, tax_entity_id, verify_status, status, deduct_status, created_at, updated_at)
SELECT 'SCAN', 'NORMAL', '032002300003', '10001003', CURRENT_DATE, '环球物流服务有限公司', '91440300MA5K00007X',
       '云途科技股份有限公司', '91310000780000001A', 800.00, 72.00, 872.00, '66666666666666666666',
       (SELECT id FROM inv_tax_entity WHERE code = 'HQ'), 'UNVERIFIED', 'NORMAL', 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_input_invoice WHERE invoice_code = '032002300003' AND invoice_no = '10001003');

-- ===================== 演示费用发票 =====================
INSERT INTO inv_expense_invoice (employee_name, employee_no, department, upload_time, invoice_type, invoice_code, invoice_no, issue_date,
    seller_name, seller_tax_no, buyer_name, buyer_tax_no, total_amount, total_tax, total_with_tax, status, created_at, updated_at)
SELECT '陈出差', 'E1001', '销售部', CURRENT_TIMESTAMP, 'OTHER', NULL, 'EXP' || TO_CHAR(CURRENT_TIMESTAMP, 'YYYYMMDDHH24MISS'), CURRENT_DATE,
       '中国铁路上海局', '91310000132200001X', '云途科技股份有限公司', '91310000780000001A', 553.00, 0, 553.00, 'UPLOADED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_expense_invoice WHERE employee_no = 'E1001');
INSERT INTO inv_expense_invoice (employee_name, employee_no, department, upload_time, invoice_type, invoice_no, issue_date,
    seller_name, seller_tax_no, buyer_name, buyer_tax_no, total_amount, total_tax, total_with_tax, status, risk_items, created_at, updated_at)
SELECT '刘市场', 'E1002', '市场部', CURRENT_TIMESTAMP, 'NORMAL', 'EXP-OLD-001', DATEADD('DAY', -200, CURRENT_DATE),
       '某会议会展公司', '91500000MA5K00008X', '云途科技股份有限公司', '91310000780000001A', 60000.00, 3600.00, 63600.00, 'RISK', '["EXPIRED","AMOUNT_LIMIT"]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM inv_expense_invoice WHERE employee_no = 'E1002');
