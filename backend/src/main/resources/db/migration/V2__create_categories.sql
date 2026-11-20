CREATE TABLE categories (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    name         VARCHAR(50)  NOT NULL,
    type         VARCHAR(10)  NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    description  VARCHAR(255),
    color        CHAR(7)      CHECK (color ~ '^#[0-9A-Fa-f]{6}$'),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_categories_id_user_type UNIQUE (id, user_id, type)
);

CREATE UNIQUE INDEX uq_categories_user_type_name
    ON categories (user_id, type, lower(name));


