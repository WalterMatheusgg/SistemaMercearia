-- Trigger de histórico: fixa search_path sem tocar em dados ou redefinir trigger.
-- Usa o schema ativo do Flyway: public localmente, loja no Supabase.
ALTER FUNCTION protect_inventory_history() SET search_path = pg_catalog;
