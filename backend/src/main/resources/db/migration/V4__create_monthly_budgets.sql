CREATE TABLE monthly_budgets (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT         NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    budget_year       SMALLINT       NOT NULL CHECK (budget_year BETWEEN 2000 AND 2100),
    budget_month      SMALLINT       NOT NULL CHECK (budget_month BETWEEN 1 AND 12),
    amount            NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),   -- 0 = no spending planned
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    -- One budget per user per month. Also the index for "this month's budget" and "a year's budgets".
    CONSTRAINT uq_monthly_budgets_user_period UNIQUE (user_id, budget_year, budget_month)
);
