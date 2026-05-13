-- Make expense category names unique per authenticated profile instead of per branch.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_expense_category_name'
          AND conrelid = 'expense_categories'::regclass
    ) THEN
        ALTER TABLE expense_categories DROP CONSTRAINT uk_expense_category_name;
    END IF;
END $$;

DROP INDEX IF EXISTS idx_expense_categories_name;
DROP INDEX IF EXISTS uk_expense_category_name;

CREATE INDEX IF NOT EXISTS idx_expense_categories_profile
    ON expense_categories (client_id, org_id, created_by);

CREATE UNIQUE INDEX IF NOT EXISTS idx_expense_categories_profile_name
    ON expense_categories (client_id, org_id, created_by, lower(name))
    WHERE created_by IS NOT NULL;
