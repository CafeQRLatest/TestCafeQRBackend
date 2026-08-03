DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='variant_pricing' AND column_name='cost_price') THEN
        ALTER TABLE variant_pricing ADD COLUMN cost_price NUMERIC(19, 2);
    END IF;
END $$;
