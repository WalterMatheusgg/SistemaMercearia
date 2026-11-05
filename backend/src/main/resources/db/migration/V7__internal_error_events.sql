-- Registro técnico mínimo: não guarda corpo de requisição, cookies, IP, tokens ou dados pessoais.
CREATE TABLE internal_error_event (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    request_method VARCHAR(12) NOT NULL,
    endpoint VARCHAR(200) NOT NULL,
    http_status INTEGER NOT NULL
);
CREATE INDEX internal_error_event_recent_idx ON internal_error_event(occurred_at DESC);
