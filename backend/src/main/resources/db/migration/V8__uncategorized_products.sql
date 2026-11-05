-- Novas instalações recebem a categoria padrão sem alterar produtos existentes.
INSERT INTO category (id,name,active)
VALUES ('00000000-0000-0000-0000-000000000105','Sem categoria',TRUE)
ON CONFLICT(name) DO NOTHING;
