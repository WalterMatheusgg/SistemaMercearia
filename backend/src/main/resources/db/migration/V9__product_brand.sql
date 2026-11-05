-- Marca opcional: preserva o catálogo existente e não afeta histórico de vendas.
ALTER TABLE product ADD COLUMN brand VARCHAR(80);
CREATE INDEX product_brand_search ON product (lower(brand)) WHERE brand IS NOT NULL;
