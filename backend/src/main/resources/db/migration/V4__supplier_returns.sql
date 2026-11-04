ALTER TABLE purchase_receipt ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE purchase_receipt ADD COLUMN cancellation_reason VARCHAR(500);
CREATE TABLE purchase_return (
    id UUID PRIMARY KEY, receipt_id UUID NOT NULL UNIQUE REFERENCES purchase_receipt(id),
    actor_id UUID NOT NULL REFERENCES app_user(id), reason VARCHAR(500) NOT NULL CHECK(length(trim(reason))>=3),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE purchase_return_item (
    id UUID PRIMARY KEY, return_id UUID NOT NULL REFERENCES purchase_return(id),
    receipt_item_id UUID NOT NULL UNIQUE REFERENCES purchase_receipt_item(id),
    movement_id UUID NOT NULL UNIQUE REFERENCES inventory_movement(id)
);
CREATE INDEX purchase_return_item_return ON purchase_return_item(return_id);
CREATE TRIGGER purchase_return_immutable BEFORE UPDATE OR DELETE ON purchase_return FOR EACH ROW EXECUTE FUNCTION protect_inventory_history();
CREATE TRIGGER purchase_return_item_immutable BEFORE UPDATE OR DELETE ON purchase_return_item FOR EACH ROW EXECUTE FUNCTION protect_inventory_history();
