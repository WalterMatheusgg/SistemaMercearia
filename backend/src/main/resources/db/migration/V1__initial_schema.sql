CREATE TABLE store (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE role (
    code VARCHAR(40) PRIMARY KEY,
    name VARCHAR(80) NOT NULL
);

CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES store(id),
    username VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_role (
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role_code VARCHAR(40) NOT NULL REFERENCES role(code),
    PRIMARY KEY (user_id, role_code)
);

CREATE TABLE category (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE unit (
    code VARCHAR(12) PRIMARY KEY,
    name VARCHAR(60) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE product (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(2000),
    category_id UUID NOT NULL REFERENCES category(id),
    base_unit_code VARCHAR(12) NOT NULL REFERENCES unit(code),
    cost NUMERIC(14, 2) NOT NULL CHECK (cost >= 0),
    price NUMERIC(14, 2) NOT NULL CHECK (price >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX product_name_idx ON product (name);
CREATE INDEX product_active_idx ON product (active);

INSERT INTO store (id, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'Loja principal');

INSERT INTO role (code, name) VALUES
    ('ROLE_OWNER', 'Dono'),
    ('ROLE_MANAGER', 'Gerente'),
    ('ROLE_SELLER', 'Vendedor'),
    ('ROLE_CASHIER', 'Operador de caixa'),
    ('ROLE_STOCKKEEPER', 'Estoquista'),
    ('ROLE_FINANCE', 'Financeiro'),
    ('ROLE_CUSTOMER', 'Cliente');

INSERT INTO category (id, name) VALUES
    ('00000000-0000-0000-0000-000000000101', 'Ferramentas manuais'),
    ('00000000-0000-0000-0000-000000000102', 'Elétrica'),
    ('00000000-0000-0000-0000-000000000103', 'Hidráulica'),
    ('00000000-0000-0000-0000-000000000104', 'Fixação');

INSERT INTO unit (code, name) VALUES
    ('UN', 'Unidade'),
    ('M', 'Metro'),
    ('KG', 'Quilograma'),
    ('L', 'Litro');