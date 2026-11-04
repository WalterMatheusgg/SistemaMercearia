ALTER TABLE app_user ADD COLUMN email VARCHAR(254);
ALTER TABLE app_user ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_user ADD COLUMN auth_revision BIGINT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX user_email_unique ON app_user(lower(email)) WHERE email IS NOT NULL;

CREATE TABLE store_settings (
    store_id UUID PRIMARY KEY REFERENCES store(id),
    phone VARCHAR(40) NOT NULL DEFAULT '', address VARCHAR(500) NOT NULL DEFAULT '',
    delivery_fee NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (delivery_fee >= 0),
    delivery_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    payment_instructions VARCHAR(1000) NOT NULL DEFAULT 'Pagamento combinado com a loja. Nenhuma cobrança automática é feita neste site.',
    reservation_minutes INTEGER NOT NULL DEFAULT 1440 CHECK (reservation_minutes BETWEEN 15 AND 10080)
);
INSERT INTO store_settings(store_id) SELECT id FROM store;

CREATE TABLE party (
    id UUID PRIMARY KEY, store_id UUID NOT NULL REFERENCES store(id),
    kind VARCHAR(12) NOT NULL CHECK(kind IN ('CUSTOMER','SUPPLIER')),
    name VARCHAR(160) NOT NULL, email VARCHAR(254), phone VARCHAR(40), document VARCHAR(30), address VARCHAR(500),
    user_id UUID UNIQUE REFERENCES app_user(id), active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK(user_id IS NULL OR kind='CUSTOMER')
);
CREATE INDEX party_store_kind_name ON party(store_id,kind,name);

CREATE TABLE business_command (
    store_id UUID NOT NULL REFERENCES store(id), idempotency_key UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL, result_id UUID NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(store_id,idempotency_key)
);
CREATE TABLE audit_event (
    id UUID PRIMARY KEY, store_id UUID NOT NULL REFERENCES store(id),
    actor_id UUID REFERENCES app_user(id), actor_name VARCHAR(120) NOT NULL,
    event_type VARCHAR(80) NOT NULL, entity_id UUID, detail VARCHAR(1000) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_store_date ON audit_event(store_id,occurred_at DESC,id);
CREATE TRIGGER audit_immutable BEFORE UPDATE OR DELETE ON audit_event FOR EACH ROW EXECUTE FUNCTION protect_inventory_history();

CREATE TABLE cash_session (
    id UUID PRIMARY KEY, store_id UUID NOT NULL REFERENCES store(id), opened_by UUID NOT NULL REFERENCES app_user(id),
    opened_at TIMESTAMPTZ NOT NULL DEFAULT now(), opening_amount NUMERIC(14,2) NOT NULL CHECK(opening_amount>=0),
    closed_at TIMESTAMPTZ, closed_by UUID REFERENCES app_user(id), counted_amount NUMERIC(14,2),
    expected_amount NUMERIC(14,2), difference NUMERIC(14,2), closing_note VARCHAR(500),
    CHECK((closed_at IS NULL) = (counted_amount IS NULL))
);
CREATE UNIQUE INDEX cash_one_open_per_store ON cash_session(store_id) WHERE closed_at IS NULL;
CREATE INDEX cash_session_store_date ON cash_session(store_id,opened_at DESC);
CREATE TABLE cash_entry (
    id UUID PRIMARY KEY, session_id UUID NOT NULL REFERENCES cash_session(id),
    amount NUMERIC(14,2) NOT NULL CHECK(amount<>0), kind VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL, actor_id UUID NOT NULL REFERENCES app_user(id),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX cash_entry_session ON cash_entry(session_id,occurred_at);
CREATE TRIGGER cash_entry_immutable BEFORE UPDATE OR DELETE ON cash_entry FOR EACH ROW EXECUTE FUNCTION protect_inventory_history();

CREATE TABLE sale (
    id UUID PRIMARY KEY, store_id UUID NOT NULL REFERENCES store(id), customer_id UUID REFERENCES party(id),
    customer_name VARCHAR(160) NOT NULL, channel VARCHAR(12) NOT NULL CHECK(channel IN ('POS','ONLINE','QUOTE')),
    status VARCHAR(30) NOT NULL CHECK(status IN ('QUOTE','PENDING_PAYMENT','COMPLETED','PAID','READY','DISPATCHED','CANCELLED','EXPIRED')),
    subtotal NUMERIC(14,2) NOT NULL CHECK(subtotal>=0), discount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK(discount>=0),
    delivery_fee NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK(delivery_fee>=0), total NUMERIC(14,2) NOT NULL CHECK(total>=0),
    fulfillment VARCHAR(12) NOT NULL CHECK(fulfillment IN ('PICKUP','DELIVERY')), delivery_address VARCHAR(500),
    payment_choice VARCHAR(30), note VARCHAR(500), created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), confirmed_at TIMESTAMPTZ, expires_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ, cancellation_reason VARCHAR(500),
    CHECK(total=subtotal-discount+delivery_fee), CHECK(discount<=subtotal)
);
CREATE INDEX sale_store_date ON sale(store_id,created_at DESC,id);
CREATE INDEX sale_customer_date ON sale(customer_id,created_at DESC);
CREATE INDEX sale_expiration ON sale(expires_at) WHERE status='PENDING_PAYMENT';
CREATE TABLE sale_item (
    id UUID PRIMARY KEY, sale_id UUID NOT NULL REFERENCES sale(id), product_id UUID NOT NULL REFERENCES product(id),
    code VARCHAR(40) NOT NULL, name VARCHAR(160) NOT NULL, unit_code VARCHAR(12) NOT NULL,
    quantity NUMERIC(19,4) NOT NULL CHECK(quantity>0), unit_price NUMERIC(14,2) NOT NULL CHECK(unit_price>=0),
    unit_cost NUMERIC(14,2) NOT NULL CHECK(unit_cost>=0), line_total NUMERIC(14,2) NOT NULL CHECK(line_total>=0),
    movement_id UUID UNIQUE REFERENCES inventory_movement(id), UNIQUE(sale_id,product_id)
);
CREATE INDEX sale_item_product ON sale_item(product_id);
CREATE TABLE inventory_reservation (
    sale_item_id UUID PRIMARY KEY REFERENCES sale_item(id), location_id UUID NOT NULL REFERENCES inventory_location(id),
    product_id UUID NOT NULL REFERENCES product(id), quantity NUMERIC(19,4) NOT NULL CHECK(quantity>0),
    status VARCHAR(12) NOT NULL CHECK(status IN ('ACTIVE','CONSUMED','RELEASED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), finished_at TIMESTAMPTZ
);
CREATE INDEX reservation_product ON inventory_reservation(location_id,product_id) WHERE status='ACTIVE';

CREATE TABLE purchase_order (
    id UUID PRIMARY KEY, store_id UUID NOT NULL REFERENCES store(id), supplier_id UUID NOT NULL REFERENCES party(id),
    supplier_name VARCHAR(160) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','PARTIAL','RECEIVED','CANCELLED')),
    total NUMERIC(14,2) NOT NULL CHECK(total>=0), due_date DATE NOT NULL, note VARCHAR(500),
    created_by UUID NOT NULL REFERENCES app_user(id), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX purchase_store_date ON purchase_order(store_id,created_at DESC);
CREATE INDEX purchase_supplier ON purchase_order(supplier_id);
CREATE TABLE purchase_item (
    id UUID PRIMARY KEY, purchase_id UUID NOT NULL REFERENCES purchase_order(id), product_id UUID NOT NULL REFERENCES product(id),
    code VARCHAR(40) NOT NULL, name VARCHAR(160) NOT NULL, unit_code VARCHAR(12) NOT NULL,
    quantity NUMERIC(19,4) NOT NULL CHECK(quantity>0), received NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK(received>=0 AND received<=quantity),
    unit_cost NUMERIC(14,2) NOT NULL CHECK(unit_cost>=0), line_total NUMERIC(14,2) NOT NULL CHECK(line_total>=0),
    UNIQUE(purchase_id,product_id)
);
CREATE INDEX purchase_item_product ON purchase_item(product_id);
CREATE TABLE purchase_receipt (
    id UUID PRIMARY KEY, purchase_id UUID NOT NULL REFERENCES purchase_order(id), total NUMERIC(14,2) NOT NULL CHECK(total>=0),
    actor_id UUID NOT NULL REFERENCES app_user(id), received_at TIMESTAMPTZ NOT NULL DEFAULT now(), note VARCHAR(500) NOT NULL
);
CREATE INDEX receipt_purchase ON purchase_receipt(purchase_id);
CREATE TABLE purchase_receipt_item (
    id UUID PRIMARY KEY, receipt_id UUID NOT NULL REFERENCES purchase_receipt(id), purchase_item_id UUID NOT NULL REFERENCES purchase_item(id),
    quantity NUMERIC(19,4) NOT NULL CHECK(quantity>0), movement_id UUID NOT NULL UNIQUE REFERENCES inventory_movement(id)
);
CREATE INDEX receipt_item_receipt ON purchase_receipt_item(receipt_id);
CREATE INDEX receipt_item_purchase_item ON purchase_receipt_item(purchase_item_id);
CREATE TRIGGER receipt_item_immutable BEFORE UPDATE OR DELETE ON purchase_receipt_item FOR EACH ROW EXECUTE FUNCTION protect_inventory_history();

CREATE TABLE financial_account (
    id UUID PRIMARY KEY, store_id UUID NOT NULL REFERENCES store(id), party_id UUID REFERENCES party(id),
    direction VARCHAR(12) NOT NULL CHECK(direction IN ('PAYABLE','RECEIVABLE')), description VARCHAR(300) NOT NULL,
    amount NUMERIC(14,2) NOT NULL CHECK(amount>=0), paid NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK(paid>=0 AND paid<=amount),
    due_date DATE NOT NULL, status VARCHAR(12) NOT NULL CHECK(status IN ('OPEN','PARTIAL','PAID','CANCELLED')),
    sale_id UUID UNIQUE REFERENCES sale(id), receipt_id UUID UNIQUE REFERENCES purchase_receipt(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX account_store_due ON financial_account(store_id,direction,due_date);
CREATE INDEX account_party ON financial_account(party_id);
CREATE TABLE financial_payment (
    id UUID PRIMARY KEY, account_id UUID NOT NULL REFERENCES financial_account(id),
    amount NUMERIC(14,2) NOT NULL CHECK(amount>0), method VARCHAR(20) NOT NULL CHECK(method IN ('CASH','PIX','DEBIT_CARD','CREDIT_CARD','TRANSFER')),
    direction VARCHAR(3) NOT NULL CHECK(direction IN ('IN','OUT')), actor_id UUID NOT NULL REFERENCES app_user(id),
    note VARCHAR(500) NOT NULL, reversed BOOLEAN NOT NULL DEFAULT FALSE, reversal_of UUID UNIQUE REFERENCES financial_payment(id),
    cash_entry_id UUID UNIQUE REFERENCES cash_entry(id), occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX payment_account ON financial_payment(account_id,occurred_at);

CREATE TABLE account_token (
    token_hash VARCHAR(64) PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id), purpose VARCHAR(12) NOT NULL CHECK(purpose IN ('RESET','VERIFY')),
    expires_at TIMESTAMPTZ NOT NULL, used_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX account_token_user ON account_token(user_id,purpose,created_at DESC);
CREATE TABLE notification_preference (
    user_id UUID PRIMARY KEY REFERENCES app_user(id), sale_completed BOOLEAN NOT NULL DEFAULT FALSE,
    stock_low BOOLEAN NOT NULL DEFAULT FALSE, daily_summary BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY, store_id UUID NOT NULL REFERENCES store(id), user_id UUID NOT NULL REFERENCES app_user(id),
    event_key VARCHAR(200) NOT NULL, event_type VARCHAR(30) NOT NULL,
    recipient VARCHAR(254) NOT NULL, subject VARCHAR(200) NOT NULL, body TEXT NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','PROCESSING','SENT','FAILED','CANCELLED')),
    attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lease_id UUID, locked_until TIMESTAMPTZ, sent_at TIMESTAMPTZ, last_error VARCHAR(300), created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id,event_key)
);
CREATE INDEX outbox_pending ON notification_outbox(next_attempt_at) WHERE status IN ('PENDING','PROCESSING');
