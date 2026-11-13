-- Rails that are money movement rather than spending: credit-card bill payments and bank interest.
INSERT INTO category_rules (pattern, match_type, category_id, priority)
SELECT r.pattern, r.match_type, c.id, r.priority
FROM (VALUES
    ('card payment',     'KEYWORD', 'Transfers',    60),
    ('payment received', 'KEYWORD', 'Transfers',    60),
    ('bank interest',    'KEYWORD', 'Other Income', 60)
) AS r(pattern, match_type, category_name, priority)
JOIN categories c ON c.name = r.category_name AND c.user_id IS NULL;
