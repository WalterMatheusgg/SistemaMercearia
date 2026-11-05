-- Fotos privadas de produtos, vinculadas ao cadastro; não altera estoque ou vendas.
CREATE TABLE IF NOT EXISTS product_photo (
    product_id UUID PRIMARY KEY REFERENCES product(id) ON DELETE CASCADE,
    content_type VARCHAR(20) NOT NULL CHECK (content_type IN ('image/jpeg','image/png','image/webp')),
    image_bytes BYTEA NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Categoria neutra: nunca classificar automaticamente ferramentas como elétrica ou hidráulica.
INSERT INTO category (id,name,active)
VALUES ('00000000-0000-0000-0000-000000000105','Sem categoria',TRUE)
ON CONFLICT(name) DO NOTHING;
