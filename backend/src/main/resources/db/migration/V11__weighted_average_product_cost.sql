-- Custo de aquisição efetivo por recebimento, incluindo frete rateado.
ALTER TABLE purchase_receipt_item
    ADD COLUMN landed_unit_cost NUMERIC(18,6) NOT NULL DEFAULT 0;

UPDATE purchase_receipt_item ri
SET landed_unit_cost = pi.unit_cost
FROM purchase_item pi
WHERE pi.id = ri.purchase_item_id AND ri.landed_unit_cost = 0;

-- Livro imutável de alterações do custo médio ponderado.
CREATE TABLE product_cost_event (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES product(id),
    movement_id UUID NOT NULL UNIQUE REFERENCES inventory_movement(id),
    source_id UUID NOT NULL,
    source_type VARCHAR(32) NOT NULL CHECK (source_type IN
        ('PURCHASE_RECEIPT','PURCHASE_RETURN','SALE_RETURN')),
    quantity NUMERIC(19,4) NOT NULL CHECK (quantity > 0),
    unit_cost NUMERIC(18,6) NOT NULL CHECK (unit_cost >= 0),
    stock_before NUMERIC(19,4) NOT NULL CHECK (stock_before >= 0),
    stock_after NUMERIC(19,4) NOT NULL CHECK (stock_after >= 0),
    cost_before NUMERIC(14,2) NOT NULL CHECK (cost_before >= 0),
    cost_after NUMERIC(14,2) NOT NULL CHECK (cost_after >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX product_cost_event_history ON product_cost_event(product_id,created_at DESC);
