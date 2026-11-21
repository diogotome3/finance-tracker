CREATE TABLE transactions (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT         NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    category_id       BIGINT         NOT NULL,
    type              VARCHAR(10)    NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    description       VARCHAR(255)   NOT NULL,
    amount            NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    transaction_date  DATE           NOT NULL,
    notes             VARCHAR(1000),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    -- Same user AND same type as the category. NO ACTION (the default) rather than RESTRICT,
    -- so deleting a user still cascades to both categories and transactions in one statement.
    CONSTRAINT fk_transactions_category FOREIGN KEY (category_id, user_id, type) REFERENCES categories (id, user_id, type)
);

-- Main access path: a user's transactions, newest first (listing, dashboard, monthly stats)
CREATE INDEX idx_transactions_user_date ON transactions (user_id, transaction_date DESC);

-- FK lookups, "is this category in use?" and filtering by category
CREATE INDEX idx_transactions_category ON transactions (category_id);
