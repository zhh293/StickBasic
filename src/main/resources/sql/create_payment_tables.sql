-- Payment tables for WeChat Pay

-- payment_order
CREATE TABLE IF NOT EXISTS payment_order (
    id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(64) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    amount NUMERIC(12,2) NOT NULL,
    currency VARCHAR(16) NOT NULL DEFAULT 'CNY',
    description VARCHAR(255),
    prepay_id VARCHAR(128),
    transaction_id VARCHAR(128),
    pay_time TIMESTAMP NULL,
    refund_amount NUMERIC(12,2),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_order_user ON payment_order(user_id);

-- payment_refund
CREATE TABLE IF NOT EXISTS payment_refund (
    id BIGSERIAL PRIMARY KEY,
    refund_no VARCHAR(64) UNIQUE NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reason VARCHAR(255),
    transaction_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_refund_order ON payment_refund(order_no);