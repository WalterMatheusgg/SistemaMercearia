-- Etiquetas internas curtas, exclusivas e estáveis. Não altera product.code,
-- valores, saldos nem o histórico comercial existente.
CREATE SEQUENCE product_barcode_seq START WITH 1 INCREMENT BY 1;
ALTER TABLE product ADD COLUMN barcode VARCHAR(12);
UPDATE product SET barcode = 'LF' || lpad(nextval('product_barcode_seq')::text, 10, '0');
ALTER TABLE product ALTER COLUMN barcode SET DEFAULT ('LF' || lpad(nextval('product_barcode_seq')::text, 10, '0'));
ALTER TABLE product ALTER COLUMN barcode SET NOT NULL;
ALTER TABLE product ADD CONSTRAINT product_barcode_unique UNIQUE (barcode);
ALTER TABLE product ADD CONSTRAINT product_barcode_format CHECK (barcode ~ '^LF[0-9]{10}$');
