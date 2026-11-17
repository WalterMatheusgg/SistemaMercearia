ALTER TABLE party
    ADD COLUMN representative_name VARCHAR(160),
    ADD COLUMN whatsapp VARCHAR(40),
    ADD COLUMN city VARCHAR(120),
    ADD COLUMN products VARCHAR(1000),
    ADD COLUMN brands VARCHAR(1000),
    ADD COLUMN minimum_order VARCHAR(200),
    ADD COLUMN quantity_discount VARCHAR(500),
    ADD COLUMN payment_terms VARCHAR(500),
    ADD COLUMN delivery_time VARCHAR(160),
    ADD COLUMN freight_terms VARCHAR(300),
    ADD COLUMN return_policy VARCHAR(1000),
    ADD COLUMN warranty_policy VARCHAR(1000);

CREATE INDEX party_supplier_city ON party(store_id, city) WHERE kind='SUPPLIER';
