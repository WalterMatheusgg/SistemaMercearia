ALTER TABLE sale ADD COLUMN cash_tendered NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK(cash_tendered>=0);
ALTER TABLE sale ADD COLUMN cash_change NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK(cash_change>=0 AND cash_change<=cash_tendered);
