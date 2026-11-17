CREATE TABLE IF NOT EXISTS supplier_offer (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES store(id),
    supplier_id UUID NOT NULL REFERENCES party(id),
    product_id UUID NOT NULL REFERENCES product(id),
    brand VARCHAR(80),
    supplier_product_code VARCHAR(80),
    unit_cost NUMERIC(14,2) NOT NULL CHECK (unit_cost >= 0),
    minimum_order_quantity NUMERIC(19,4) NOT NULL DEFAULT 1 CHECK (minimum_order_quantity > 0),
    quoted_at DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_until DATE,
    delivery_days INTEGER CHECK (delivery_days IS NULL OR delivery_days >= 0),
    freight_cost NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (freight_cost >= 0),
    notes VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (valid_until IS NULL OR valid_until >= quoted_at)
);
CREATE INDEX IF NOT EXISTS supplier_offer_product ON supplier_offer(store_id, product_id, active, quoted_at DESC);
CREATE INDEX IF NOT EXISTS supplier_offer_supplier ON supplier_offer(store_id, supplier_id, active);
CREATE TABLE IF NOT EXISTS supplier_offer_tier (
    id UUID PRIMARY KEY,
    offer_id UUID NOT NULL REFERENCES supplier_offer(id) ON DELETE CASCADE,
    minimum_quantity NUMERIC(19,4) NOT NULL CHECK (minimum_quantity > 0),
    unit_cost NUMERIC(14,2) NOT NULL CHECK (unit_cost >= 0),
    UNIQUE (offer_id, minimum_quantity)
);
CREATE INDEX IF NOT EXISTS supplier_offer_tier_lookup ON supplier_offer_tier(offer_id, minimum_quantity DESC);

-- Frete negociado no pedido e rateado proporcionalmente conforme os itens são recebidos.
ALTER TABLE purchase_order
    ADD COLUMN IF NOT EXISTS freight_cost NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (freight_cost >= 0);
ALTER TABLE purchase_receipt
    ADD COLUMN freight_cost NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (freight_cost >= 0);
