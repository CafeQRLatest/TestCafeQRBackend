ALTER TABLE customers ADD COLUMN IF NOT EXISTS phone VARCHAR(50);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS order_links JSONB NOT NULL DEFAULT '[]'::jsonb;

UPDATE customers
SET order_links = '[]'::jsonb
WHERE order_links IS NULL;

CREATE INDEX IF NOT EXISTS idx_customers_order_links
    ON customers USING GIN (order_links jsonb_path_ops);

CREATE INDEX IF NOT EXISTS idx_customers_client_phone
    ON customers(client_id, phone)
    WHERE phone IS NOT NULL AND btrim(phone) <> '';

CREATE OR REPLACE FUNCTION pg_temp.safe_jsonb(input_text text)
RETURNS jsonb
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN input_text::jsonb;
EXCEPTION WHEN others THEN
    RETURN '[]'::jsonb;
END;
$$;

UPDATE customers c
SET order_links = c.order_links || jsonb_build_array(jsonb_build_object(
        'orderId', o.id::text,
        'isPrimary', true,
        'attachedAt', COALESCE(o.created_at, CURRENT_TIMESTAMP)::text
    ))
FROM orders o
WHERE o.customer_id = c.id
  AND NOT c.order_links @> jsonb_build_array(jsonb_build_object('orderId', o.id::text));

WITH parsed_order_customers AS (
    SELECT
        o.id AS order_id,
        o.created_at,
        COALESCE(
            CASE WHEN jsonb_typeof(customer_item.value) = 'string'
                THEN trim(both '"' from customer_item.value::text)
                ELSE customer_item.value ->> 'id'
            END,
            ''
        ) AS customer_id,
        customer_item.ordinality
    FROM orders o
    CROSS JOIN LATERAL jsonb_array_elements(pg_temp.safe_jsonb(o.customer_ids)) WITH ORDINALITY AS customer_item(value, ordinality)
    WHERE o.customer_ids IS NOT NULL
      AND btrim(o.customer_ids) <> ''
)
UPDATE customers c
SET order_links = c.order_links || jsonb_build_array(jsonb_build_object(
        'orderId', poc.order_id::text,
        'isPrimary', poc.ordinality = 1,
        'attachedAt', COALESCE(poc.created_at, CURRENT_TIMESTAMP)::text
    ))
FROM parsed_order_customers poc
WHERE c.id::text = poc.customer_id
  AND NOT c.order_links @> jsonb_build_array(jsonb_build_object('orderId', poc.order_id::text));

UPDATE customers c
SET order_links = c.order_links || jsonb_build_array(jsonb_build_object(
        'orderId', o.id::text,
        'isPrimary', true,
        'attachedAt', COALESCE(o.created_at, CURRENT_TIMESTAMP)::text
    ))
FROM orders o
WHERE o.customer_id IS NULL
  AND o.customer_phone IS NOT NULL
  AND btrim(o.customer_phone) <> ''
  AND c.client_id = o.client_id
  AND c.phone = o.customer_phone
  AND NOT c.order_links @> jsonb_build_array(jsonb_build_object('orderId', o.id::text));

INSERT INTO customers (
    id,
    client_id,
    org_id,
    name,
    phone,
    customer_category,
    loyalty_points,
    credit_limit,
    opening_balance,
    isactive,
    created_at,
    updated_at,
    created_by,
    updated_by,
    order_links
)
SELECT
    gen_random_uuid(),
    o.client_id,
    o.org_id,
    COALESCE(NULLIF(btrim(o.customer_name), ''), 'Guest'),
    NULLIF(btrim(o.customer_phone), ''),
    'REGULAR',
    0,
    0,
    0,
    'Y',
    COALESCE(o.created_at, CURRENT_TIMESTAMP),
    COALESCE(o.updated_at, CURRENT_TIMESTAMP),
    o.created_by,
    o.updated_by,
    jsonb_build_array(jsonb_build_object(
        'orderId', o.id::text,
        'isPrimary', true,
        'attachedAt', COALESCE(o.created_at, CURRENT_TIMESTAMP)::text
    ))
FROM orders o
WHERE o.customer_id IS NULL
  AND (NULLIF(btrim(o.customer_name), '') IS NOT NULL OR NULLIF(btrim(o.customer_phone), '') IS NOT NULL)
  AND NOT EXISTS (
      SELECT 1
      FROM customers c
      WHERE c.client_id = o.client_id
        AND NULLIF(btrim(c.phone), '') IS NOT DISTINCT FROM NULLIF(btrim(o.customer_phone), '')
        AND c.order_links @> jsonb_build_array(jsonb_build_object('orderId', o.id::text))
  );

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM (
            SELECT client_id, phone
            FROM customers
            WHERE phone IS NOT NULL AND btrim(phone) <> ''
            GROUP BY client_id, phone
            HAVING COUNT(*) > 1
        ) duplicate_phones
    ) THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uk_customers_client_phone
            ON customers(client_id, phone)
            WHERE phone IS NOT NULL AND btrim(phone) <> '';
    END IF;
END $$;

ALTER TABLE orders DROP COLUMN IF EXISTS customer_name;
ALTER TABLE orders DROP COLUMN IF EXISTS customer_phone;
ALTER TABLE orders DROP COLUMN IF EXISTS customer_ids;
