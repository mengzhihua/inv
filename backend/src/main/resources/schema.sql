-- INV 发票管理系统数据库结构（H2 / MySQL 兼容，幂等）

CREATE TABLE IF NOT EXISTS inv_sequence (
    prefix    VARCHAR(64) NOT NULL,
    day_key   VARCHAR(8)  NOT NULL,
    seq_value INT         NOT NULL,
    PRIMARY KEY (prefix, day_key)
);

CREATE TABLE IF NOT EXISTS inv_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64) NOT NULL,
    password      VARCHAR(255) NOT NULL,
    real_name     VARCHAR(64),
    role          VARCHAR(16) NOT NULL,
    status        INT DEFAULT 1,
    last_login_at TIMESTAMP,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    CONSTRAINT uk_inv_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS inv_op_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64),
    method      VARCHAR(8),
    path        VARCHAR(255),
    query       VARCHAR(255),
    http_status INT,
    cost_ms     INT,
    client_ip   VARCHAR(64),
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS inv_integration_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    direction     VARCHAR(8),
    target        VARCHAR(64),
    action        VARCHAR(64),
    ref_no        VARCHAR(128),
    request_body  TEXT,
    response_body TEXT,
    success       INT,
    error_msg     VARCHAR(512),
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP
);

-- ============ 基础数据 ============
CREATE TABLE IF NOT EXISTS inv_tax_entity (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    code          VARCHAR(32) NOT NULL,
    name          VARCHAR(128) NOT NULL,
    tax_no        VARCHAR(20) NOT NULL,
    address       VARCHAR(255),
    phone         VARCHAR(32),
    bank_name     VARCHAR(128),
    bank_account  VARCHAR(64),
    taxpayer_type VARCHAR(16) DEFAULT 'GENERAL',
    drawer        VARCHAR(64),
    payee         VARCHAR(64),
    reviewer      VARCHAR(64),
    status        INT DEFAULT 1,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    CONSTRAINT uk_inv_tax_entity_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS inv_tax_entity_limit (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    tax_entity_id BIGINT NOT NULL,
    invoice_type  VARCHAR(20) NOT NULL,
    max_amount    DECIMAL(18,2) NOT NULL,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    KEY idx_inv_te_limit (tax_entity_id, invoice_type)
);

CREATE TABLE IF NOT EXISTS inv_partner (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    type         VARCHAR(16) NOT NULL,
    name         VARCHAR(128) NOT NULL,
    tax_no       VARCHAR(20),
    address      VARCHAR(255),
    phone        VARCHAR(32),
    bank_name    VARCHAR(128),
    bank_account VARCHAR(64),
    email        VARCHAR(128),
    mobile       VARCHAR(32),
    status       INT DEFAULT 1,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP
);

CREATE TABLE IF NOT EXISTS inv_goods (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    code               VARCHAR(32) NOT NULL,
    name               VARCHAR(128) NOT NULL,
    tax_category_code  VARCHAR(19),
    tax_category_name  VARCHAR(128),
    spec               VARCHAR(64),
    unit               VARCHAR(16),
    price              DECIMAL(18,2),
    tax_rate           DECIMAL(5,4) DEFAULT 0.13,
    preferential_policy VARCHAR(16) DEFAULT 'NONE',
    status             INT DEFAULT 1,
    created_at         TIMESTAMP,
    updated_at         TIMESTAMP,
    CONSTRAINT uk_inv_goods_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS inv_invoice_stock (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    tax_entity_id BIGINT NOT NULL,
    invoice_type  VARCHAR(20) NOT NULL,
    invoice_code  VARCHAR(12),
    start_no      VARCHAR(20) NOT NULL,
    end_no        VARCHAR(20) NOT NULL,
    current_no    VARCHAR(20),
    remaining     INT NOT NULL,
    status        INT DEFAULT 1,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    KEY idx_inv_stock (tax_entity_id, invoice_type, status)
);

-- ============ 销项 ============
CREATE TABLE IF NOT EXISTS inv_invoice_request (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_no          VARCHAR(40) NOT NULL,
    tax_entity_id       BIGINT NOT NULL,
    customer_id         BIGINT,
    invoice_type        VARCHAR(20) NOT NULL,
    buyer_name          VARCHAR(128),
    buyer_tax_no        VARCHAR(20),
    buyer_address_phone VARCHAR(255),
    buyer_bank          VARCHAR(255),
    remark              VARCHAR(512),
    source              VARCHAR(16) DEFAULT 'MANUAL',
    ext_ref             VARCHAR(64),
    total_amount        DECIMAL(18,2),
    total_tax           DECIMAL(18,2),
    total_with_tax      DECIMAL(18,2),
    status              VARCHAR(16) DEFAULT 'DRAFT',
    reject_reason       VARCHAR(255),
    invoice_id          BIGINT,
    with_list           INT DEFAULT 0,
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP,
    CONSTRAINT uk_inv_request_no UNIQUE (request_no),
    UNIQUE KEY uk_inv_request_extref (source, ext_ref),
    KEY idx_inv_request_status (status)
);

CREATE TABLE IF NOT EXISTS inv_invoice_request_line (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id        BIGINT NOT NULL,
    goods_id          BIGINT,
    goods_name        VARCHAR(128),
    tax_category_code VARCHAR(19),
    spec              VARCHAR(64),
    unit              VARCHAR(16),
    quantity          DECIMAL(18,4),
    unit_price        DECIMAL(18,6),
    amount            DECIMAL(18,2),
    tax_rate          DECIMAL(5,4),
    tax_amount        DECIMAL(18,2),
    price_include_tax INT DEFAULT 0,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    KEY idx_inv_request_line (request_id)
);

CREATE TABLE IF NOT EXISTS inv_invoice (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_code         VARCHAR(12),
    invoice_no           VARCHAR(20) NOT NULL,
    invoice_type         VARCHAR(20) NOT NULL,
    tax_entity_id        BIGINT NOT NULL,
    buyer_name           VARCHAR(128),
    buyer_tax_no         VARCHAR(20),
    buyer_address_phone  VARCHAR(255),
    buyer_bank           VARCHAR(255),
    seller_name          VARCHAR(128),
    seller_tax_no        VARCHAR(20),
    seller_address_phone VARCHAR(255),
    seller_bank          VARCHAR(255),
    issue_date           DATE,
    total_amount         DECIMAL(18,2),
    total_tax            DECIMAL(18,2),
    total_with_tax       DECIMAL(18,2),
    check_code           VARCHAR(20),
    status               VARCHAR(16) DEFAULT 'ISSUED',
    red_of_invoice_id    BIGINT,
    request_id           BIGINT,
    red_info_id          BIGINT,
    pdf_url              VARCHAR(255),
    machine_no           VARCHAR(32),
    drawer               VARCHAR(64),
    payee                VARCHAR(64),
    reviewer             VARCHAR(64),
    delivery_status      VARCHAR(16) DEFAULT 'UNDELIVERED',
    red_amount           DECIMAL(18,2) DEFAULT 0,
    red_tax              DECIMAL(18,2) DEFAULT 0,
    created_at           TIMESTAMP,
    updated_at           TIMESTAMP,
    CONSTRAINT uk_inv_invoice_no UNIQUE (invoice_code, invoice_no),
    UNIQUE KEY uk_inv_invoice_request (request_id),
    KEY idx_inv_invoice_q (tax_entity_id, invoice_type, status, issue_date)
);

CREATE TABLE IF NOT EXISTS inv_invoice_line (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id        BIGINT NOT NULL,
    goods_name        VARCHAR(128),
    tax_category_code VARCHAR(19),
    spec              VARCHAR(64),
    unit              VARCHAR(16),
    quantity          DECIMAL(18,4),
    unit_price        DECIMAL(18,6),
    amount            DECIMAL(18,2),
    tax_rate          DECIMAL(5,4),
    tax_amount        DECIMAL(18,2),
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    KEY idx_inv_invoice_line (invoice_id)
);

CREATE TABLE IF NOT EXISTS inv_invoice_event (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    event      VARCHAR(32),
    detail     VARCHAR(512),
    operator   VARCHAR(64),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    KEY idx_inv_event (invoice_id)
);

CREATE TABLE IF NOT EXISTS inv_red_info (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    red_info_no     VARCHAR(16),
    invoice_id      BIGINT NOT NULL,
    reason          VARCHAR(32),
    total_amount    DECIMAL(18,2),
    total_tax       DECIMAL(18,2),
    total_with_tax  DECIMAL(18,2),
    status          VARCHAR(16) DEFAULT 'DRAFT',
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    KEY idx_inv_red_info (invoice_id, status)
);

CREATE TABLE IF NOT EXISTS inv_red_info_line (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    red_info_id       BIGINT NOT NULL,
    goods_name        VARCHAR(128),
    tax_category_code VARCHAR(19),
    spec              VARCHAR(64),
    unit              VARCHAR(16),
    quantity          DECIMAL(18,4),
    unit_price        DECIMAL(18,6),
    amount            DECIMAL(18,2),
    tax_rate          DECIMAL(5,4),
    tax_amount        DECIMAL(18,2),
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    KEY idx_inv_red_line (red_info_id)
);

CREATE TABLE IF NOT EXISTS inv_delivery_log (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    channel    VARCHAR(16),
    target     VARCHAR(128),
    status     VARCHAR(16),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    KEY idx_inv_delivery (invoice_id)
);

-- ============ 进项 ============
CREATE TABLE IF NOT EXISTS inv_input_invoice (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    source         VARCHAR(16) DEFAULT 'MANUAL',
    invoice_type   VARCHAR(20) NOT NULL,
    invoice_code   VARCHAR(12),
    invoice_no     VARCHAR(20) NOT NULL,
    issue_date     DATE,
    seller_name    VARCHAR(128),
    seller_tax_no  VARCHAR(20),
    buyer_name     VARCHAR(128),
    buyer_tax_no   VARCHAR(20),
    total_amount   DECIMAL(18,2),
    total_tax      DECIMAL(18,2),
    total_with_tax DECIMAL(18,2),
    check_code     VARCHAR(20),
    remark         VARCHAR(512),
    tax_entity_id  BIGINT,
    supplier_id    BIGINT,
    verify_status  VARCHAR(16) DEFAULT 'UNVERIFIED',
    verify_msg     VARCHAR(255),
    verified_at    TIMESTAMP,
    status         VARCHAR(16) DEFAULT 'NORMAL',
    deduct_status  VARCHAR(16) DEFAULT 'PENDING',
    deduct_period  VARCHAR(7),
    not_deduct_reason VARCHAR(255),
    match_status   VARCHAR(16) DEFAULT 'UNMATCHED',
    po_no          VARCHAR(64),
    receipt_no     VARCHAR(64),
    match_diff     DECIMAL(18,2),
    account_status VARCHAR(16) DEFAULT 'UNPOSTED',
    voucher_no     VARCHAR(32),
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    CONSTRAINT uk_inv_input_no UNIQUE (invoice_code, invoice_no),
    KEY idx_inv_input_q (status, verify_status, deduct_status)
);

CREATE TABLE IF NOT EXISTS inv_input_invoice_line (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    input_invoice_id  BIGINT NOT NULL,
    goods_name        VARCHAR(128),
    tax_category_code VARCHAR(19),
    spec              VARCHAR(64),
    unit              VARCHAR(16),
    quantity          DECIMAL(18,4),
    unit_price        DECIMAL(18,6),
    amount            DECIMAL(18,2),
    tax_rate          DECIMAL(5,4),
    tax_amount        DECIMAL(18,2),
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    KEY idx_inv_input_line (input_invoice_id)
);

CREATE TABLE IF NOT EXISTS inv_deduction_batch (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    tax_entity_id BIGINT,
    period        VARCHAR(7) NOT NULL,
    batch_no      VARCHAR(40),
    invoice_count INT,
    total_amount  DECIMAL(18,2),
    total_tax     DECIMAL(18,2),
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP
);

-- ============ 费用 ============
CREATE TABLE IF NOT EXISTS inv_expense_invoice (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_name  VARCHAR(64),
    employee_no    VARCHAR(32),
    department     VARCHAR(64),
    upload_time    TIMESTAMP,
    invoice_type   VARCHAR(20),
    invoice_code   VARCHAR(12),
    invoice_no     VARCHAR(20),
    issue_date     DATE,
    seller_name    VARCHAR(128),
    seller_tax_no  VARCHAR(20),
    buyer_name     VARCHAR(128),
    buyer_tax_no   VARCHAR(20),
    total_amount   DECIMAL(18,2),
    total_tax      DECIMAL(18,2),
    total_with_tax DECIMAL(18,2),
    check_code     VARCHAR(20),
    reimburse_no   VARCHAR(64),
    status         VARCHAR(16) DEFAULT 'UPLOADED',
    risk_items     VARCHAR(2000),
    reject_reason  VARCHAR(255),
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    KEY idx_inv_expense (status)
);

-- ============ 税务 ============
CREATE TABLE IF NOT EXISTS inv_tax_period (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    tax_entity_id BIGINT NOT NULL,
    period        VARCHAR(7) NOT NULL,
    status        VARCHAR(16) DEFAULT 'OPEN',
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    CONSTRAINT uk_inv_period UNIQUE (tax_entity_id, period)
);

-- ============ 电子档案 ============
CREATE TABLE IF NOT EXISTS inv_archive (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id   BIGINT,
    invoice_no   VARCHAR(20),
    invoice_type VARCHAR(20),
    direction    VARCHAR(8),
    issue_month  VARCHAR(7),
    pdf_url      VARCHAR(255),
    ofd_url      VARCHAR(255),
    meta         VARCHAR(2000),
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    KEY idx_inv_archive (issue_month, direction)
);
