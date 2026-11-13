-- System categories (user_id NULL).
INSERT INTO categories (name, kind, color) VALUES
    ('Food & Dining',      'EXPENSE',  '#f97316'),
    ('Groceries',          'EXPENSE',  '#84cc16'),
    ('Transport',          'EXPENSE',  '#0ea5e9'),
    ('Travel',             'EXPENSE',  '#6366f1'),
    ('Shopping',           'EXPENSE',  '#ec4899'),
    ('Entertainment',      'EXPENSE',  '#a855f7'),
    ('Bills & Utilities',  'EXPENSE',  '#eab308'),
    ('Rent',               'EXPENSE',  '#ef4444'),
    ('Health',             'EXPENSE',  '#14b8a6'),
    ('Education',          'EXPENSE',  '#8b5cf6'),
    ('Fuel',               'EXPENSE',  '#f59e0b'),
    ('Personal Care',      'EXPENSE',  '#f472b6'),
    ('Insurance',          'EXPENSE',  '#64748b'),
    ('EMI & Loans',        'EXPENSE',  '#dc2626'),
    ('Investments',        'TRANSFER', '#10b981'),
    ('Transfers',          'TRANSFER', '#94a3b8'),
    ('Cash Withdrawal',    'EXPENSE',  '#78716c'),
    ('Salary',             'INCOME',   '#22c55e'),
    ('Other Income',       'INCOME',   '#4ade80'),
    ('Uncategorised',      'EXPENSE',  '#cbd5e1');

-- Built-in keyword rules for well-known Indian merchants. Matched case-insensitively against the
-- normalised merchant and the raw description. Lower priority value wins.
-- REGEX rules use Java syntax; short tokens are word-bounded so "ola" does not match "Coca Cola".
INSERT INTO category_rules (pattern, match_type, category_id, priority)
SELECT r.pattern, r.match_type, c.id, r.priority
FROM (VALUES
    -- Food & Dining
    ('swiggy', 'KEYWORD', 'Food & Dining', 100), ('zomato', 'KEYWORD', 'Food & Dining', 100),
    ('dominos', 'KEYWORD', 'Food & Dining', 100), ('mcdonald', 'KEYWORD', 'Food & Dining', 100),
    ('starbucks', 'KEYWORD', 'Food & Dining', 100), ('kfc', 'KEYWORD', 'Food & Dining', 100),
    ('eatsure', 'KEYWORD', 'Food & Dining', 100), ('chaayos', 'KEYWORD', 'Food & Dining', 100),
    -- Groceries (quick commerce)
    ('bigbasket', 'KEYWORD', 'Groceries', 90), ('blinkit', 'KEYWORD', 'Groceries', 90),
    ('zepto', 'KEYWORD', 'Groceries', 90), ('dmart', 'KEYWORD', 'Groceries', 90),
    ('jiomart', 'KEYWORD', 'Groceries', 80), ('instamart', 'KEYWORD', 'Groceries', 80),
    -- Transport
    ('uber', 'KEYWORD', 'Transport', 100), ('\bola\b', 'REGEX', 'Transport', 100),
    ('rapido', 'KEYWORD', 'Transport', 100), ('metro', 'KEYWORD', 'Transport', 110),
    ('fastag', 'KEYWORD', 'Transport', 100),
    -- Travel
    ('irctc', 'KEYWORD', 'Travel', 100), ('makemytrip', 'KEYWORD', 'Travel', 100),
    ('goibibo', 'KEYWORD', 'Travel', 100), ('indigo', 'KEYWORD', 'Travel', 100),
    ('cleartrip', 'KEYWORD', 'Travel', 100), ('redbus', 'KEYWORD', 'Travel', 100),
    -- Shopping
    ('amazon', 'KEYWORD', 'Shopping', 120), ('flipkart', 'KEYWORD', 'Shopping', 100),
    ('myntra', 'KEYWORD', 'Shopping', 100), ('ajio', 'KEYWORD', 'Shopping', 100),
    ('nykaa', 'KEYWORD', 'Personal Care', 100), ('meesho', 'KEYWORD', 'Shopping', 100),
    ('croma', 'KEYWORD', 'Shopping', 100), ('decathlon', 'KEYWORD', 'Shopping', 100),
    -- Entertainment (streaming)
    ('netflix', 'KEYWORD', 'Entertainment', 100), ('spotify', 'KEYWORD', 'Entertainment', 100),
    ('hotstar', 'KEYWORD', 'Entertainment', 100), ('prime video', 'KEYWORD', 'Entertainment', 90),
    ('youtube', 'KEYWORD', 'Entertainment', 100), ('bookmyshow', 'KEYWORD', 'Entertainment', 100),
    ('pvr', 'KEYWORD', 'Entertainment', 100), ('sonyliv', 'KEYWORD', 'Entertainment', 100),
    -- Bills & Utilities
    ('\bjio\b', 'REGEX', 'Bills & Utilities', 110), ('airtel', 'KEYWORD', 'Bills & Utilities', 100),
    ('vodafone', 'KEYWORD', 'Bills & Utilities', 100), ('bsnl', 'KEYWORD', 'Bills & Utilities', 100),
    ('electricity', 'KEYWORD', 'Bills & Utilities', 100), ('bescom', 'KEYWORD', 'Bills & Utilities', 100),
    ('tata power', 'KEYWORD', 'Bills & Utilities', 100), ('act fibernet', 'KEYWORD', 'Bills & Utilities', 100),
    ('\bgas\b', 'REGEX', 'Bills & Utilities', 130),
    -- Rent / housing
    ('\brent\b', 'REGEX', 'Rent', 100), ('nobroker', 'KEYWORD', 'Rent', 100),
    -- Health
    ('apollo', 'KEYWORD', 'Health', 100), ('pharmeasy', 'KEYWORD', 'Health', 100),
    ('1mg', 'KEYWORD', 'Health', 100), ('practo', 'KEYWORD', 'Health', 100),
    ('cult.fit', 'KEYWORD', 'Health', 100), ('cultfit', 'KEYWORD', 'Health', 100),
    -- Fuel
    ('indian oil', 'KEYWORD', 'Fuel', 100), ('iocl', 'KEYWORD', 'Fuel', 100),
    ('hpcl', 'KEYWORD', 'Fuel', 100), ('bpcl', 'KEYWORD', 'Fuel', 100), ('petrol', 'KEYWORD', 'Fuel', 100),
    -- Insurance / loans / investments
    ('\blic\b', 'REGEX', 'Insurance', 100), ('policybazaar', 'KEYWORD', 'Insurance', 100),
    ('\bemi\b', 'REGEX', 'EMI & Loans', 90), ('bajaj finserv', 'KEYWORD', 'EMI & Loans', 100),
    ('zerodha', 'KEYWORD', 'Investments', 100), ('groww', 'KEYWORD', 'Investments', 100),
    ('\bsip\b', 'REGEX', 'Investments', 100), ('mutual fund', 'KEYWORD', 'Investments', 100),
    -- Education
    ('udemy', 'KEYWORD', 'Education', 100), ('coursera', 'KEYWORD', 'Education', 100),
    ('byju', 'KEYWORD', 'Education', 100),
    -- Income / cash / transfers
    ('salary', 'KEYWORD', 'Salary', 50), ('sal cr', 'KEYWORD', 'Salary', 50),
    ('\binterest\b', 'REGEX', 'Other Income', 100), ('refund', 'KEYWORD', 'Other Income', 100),
    ('cashback', 'KEYWORD', 'Other Income', 100),
    ('\batm\b', 'REGEX', 'Cash Withdrawal', 80), ('cash wdl', 'KEYWORD', 'Cash Withdrawal', 80)
) AS r(pattern, match_type, category_name, priority)
JOIN categories c ON c.name = r.category_name AND c.user_id IS NULL;

