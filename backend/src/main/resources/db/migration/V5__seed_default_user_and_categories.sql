-- Default user: the owner of all data until authentication exists (decision D2).
-- No explicit id: it's GENERATED ALWAYS, and on a fresh database this first row gets id 1
-- (app.default-user-id). Forcing id 1 with OVERRIDING SYSTEM VALUE would leave the identity
-- counter at 1, so the next user created would collide with it.
INSERT INTO app_users (email, display_name)
VALUES ('default@financetracker.local', 'Default User');

-- Starting categories for the default user. The user is looked up by email rather than
-- hardcoding user_id = 1, so the categories always belong to the row inserted above.
-- "Other" exists once per type: uniqueness is (user_id, type, lower(name)).
INSERT INTO categories (user_id, name, type, color)
SELECT u.id, c.name, c.type, c.color
FROM app_users u
CROSS JOIN (VALUES
    -- EXPENSE (12)
    ('Food & Groceries', 'EXPENSE', '#F59E0B'),
    ('Restaurants',      'EXPENSE', '#EF4444'),
    ('Transportation',   'EXPENSE', '#3B82F6'),
    ('Housing',          'EXPENSE', '#8B5CF6'),
    ('Utilities',        'EXPENSE', '#06B6D4'),
    ('Shopping',         'EXPENSE', '#EC4899'),
    ('Entertainment',    'EXPENSE', '#F97316'),
    ('Health',           'EXPENSE', '#14B8A6'),
    ('Sports',           'EXPENSE', '#84CC16'),
    ('Subscriptions',    'EXPENSE', '#6366F1'),
    ('Travel',           'EXPENSE', '#0EA5E9'),
    ('Other',            'EXPENSE', '#78716C'),
    -- INCOME (5)
    ('Salary',           'INCOME',  '#16A34A'),
    ('Freelance',        'INCOME',  '#0D9488'),
    ('Investments',      'INCOME',  '#2563EB'),
    ('Gifts',            'INCOME',  '#DB2777'),
    ('Other',            'INCOME',  '#64748B')
) AS c(name, type, color)
WHERE u.email = 'default@financetracker.local';
