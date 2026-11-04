ALTER TABLE unit ADD COLUMN quantity_scale SMALLINT NOT NULL DEFAULT 0 CHECK (quantity_scale BETWEEN 0 AND 4);
ALTER TABLE unit ADD COLUMN quantity_step NUMERIC(19,4) NOT NULL DEFAULT 1 CHECK (quantity_step > 0);
UPDATE unit SET quantity_scale = 2, quantity_step = 0.01 WHERE code = 'M';
UPDATE unit SET quantity_scale = 3, quantity_step = 0.001 WHERE code IN ('KG', 'L');

CREATE TABLE inventory_location (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES store(id),
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (store_id, name)
);

INSERT INTO inventory_location (id, store_id, name)
SELECT '00000000-0000-0000-0000-000000000201', id, 'Estoque principal' FROM store
WHERE id = '00000000-0000-0000-0000-000000000001';

CREATE TABLE inventory_balance (
    location_id UUID NOT NULL REFERENCES inventory_location(id),
    product_id UUID NOT NULL REFERENCES product(id),
    physical NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (physical >= 0),
    reserved NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    unavailable NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (unavailable >= 0),
    minimum_quantity NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (minimum_quantity >= 0),
    revision BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (location_id, product_id),
    CHECK (reserved + unavailable <= physical)
);

CREATE TABLE inventory_movement (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES store(id),
    location_id UUID NOT NULL REFERENCES inventory_location(id),
    product_id UUID NOT NULL REFERENCES product(id),
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('INITIAL','ENTRY','EXIT','LOSS','COUNT','MINIMUM','REVERSAL')),
    product_code VARCHAR(40) NOT NULL,
    product_name VARCHAR(160) NOT NULL,
    unit_code VARCHAR(12) NOT NULL,
    quantity NUMERIC(19,4) NOT NULL CHECK (quantity >= 0),
    delta NUMERIC(19,4) NOT NULL,
    balance_before NUMERIC(19,4) NOT NULL CHECK (balance_before >= 0),
    balance_after NUMERIC(19,4) NOT NULL CHECK (balance_after >= 0),
    minimum_before NUMERIC(19,4) NOT NULL CHECK (minimum_before >= 0),
    minimum_after NUMERIC(19,4) NOT NULL CHECK (minimum_after >= 0),
    reason VARCHAR(500) NOT NULL CHECK (length(trim(reason)) >= 3),
    actor_id UUID NOT NULL REFERENCES app_user(id),
    actor_name VARCHAR(120) NOT NULL,
    idempotency_key UUID NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    reverses_movement_id UUID REFERENCES inventory_movement(id),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (store_id, idempotency_key),
    CHECK (balance_after = balance_before + delta),
    CHECK ((kind = 'REVERSAL') = (reverses_movement_id IS NOT NULL))
);

CREATE UNIQUE INDEX inventory_single_opening ON inventory_movement (location_id, product_id) WHERE kind = 'INITIAL';
CREATE UNIQUE INDEX inventory_single_reversal ON inventory_movement (reverses_movement_id) WHERE reverses_movement_id IS NOT NULL;
CREATE INDEX inventory_history_idx ON inventory_movement (store_id, location_id, product_id, occurred_at DESC, id);

CREATE FUNCTION protect_inventory_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Movimentações confirmadas são imutáveis. Registre uma operação compensatória.';
END;
$$;
CREATE TRIGGER inventory_history_immutable BEFORE UPDATE OR DELETE ON inventory_movement
FOR EACH ROW EXECUTE FUNCTION protect_inventory_history();
