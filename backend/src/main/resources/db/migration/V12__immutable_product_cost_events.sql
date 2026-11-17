-- Eventos de custo são evidência contábil gerencial e não podem ser alterados ou apagados.
CREATE TRIGGER product_cost_event_immutable
BEFORE UPDATE OR DELETE ON product_cost_event
FOR EACH ROW EXECUTE FUNCTION protect_inventory_history();
