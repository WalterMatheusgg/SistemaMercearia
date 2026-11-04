package br.com.ferragens.commerce;

import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.shared.BusinessRuleException;
import br.com.ferragens.shared.NotFoundException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;

@Component
public class Db {
    public static final UUID STORE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID LOCATION = UUID.fromString("00000000-0000-0000-0000-000000000201");
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    public final JdbcTemplate jdbc;

    public Db(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.query(
                sql,
                (rs, n) -> {
                    var row = new LinkedHashMap<String, Object>();
                    var meta = rs.getMetaData();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        Object value = rs.getObject(i);
                        if (value instanceof BigDecimal d) value = d.toPlainString();
                        else if (value instanceof UUID) value = value.toString();
                        else if (value instanceof Timestamp t) value = t.toInstant().toString();
                        else if (value instanceof java.sql.Date d)
                            value = d.toLocalDate().toString();
                        else if (value instanceof OffsetDateTime t)
                            value = t.toInstant().toString();
                        row.put(camel(meta.getColumnLabel(i)), value);
                    }
                    return row;
                },
                args);
    }

    public Map<String, Object> one(String sql, Object... args) {
        return rows(sql, args).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Registro não encontrado."));
    }

    public static String camel(String key) {
        var b = new StringBuilder();
        boolean upper = false;
        for (char c : key.toCharArray()) {
            if (c == '_') upper = true;
            else {
                b.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return b.toString();
    }

    public static UUID id(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : UUID.fromString(v.toString());
    }

    public static BigDecimal decimal(Map<String, Object> row, String key) {
        return new BigDecimal(row.get(key).toString());
    }

    public static String text(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? "" : v.toString();
    }

    public static String required(String value, int max, String label) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > max)
            throw new BusinessRuleException("Confira " + label + ".");
        return value.trim();
    }

    public static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > max)
            throw new BusinessRuleException("Texto acima do tamanho permitido.");
        return value.trim();
    }

    public static BigDecimal money(BigDecimal value, boolean positive) {
        if (value == null
                || value.signum() < (positive ? 1 : 0)
                || value.scale() > 2
                || value.precision() - value.scale() > 12)
            throw new BusinessRuleException(
                    "Informe um valor monetário válido, com até duas casas decimais.");
        return value.setScale(2);
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public void audit(Actor actor, String type, UUID entity, String detail) {
        jdbc.update(
                "INSERT INTO"
                    + " audit_event(id,store_id,actor_id,actor_name,event_type,entity_id,detail)"
                    + " VALUES(?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                actor.storeId(),
                actor.userId(),
                actor.username(),
                type,
                entity,
                detail);
    }

    public Map<String, Object> page(String sql, String count, int page, int size, Object... args) {
        int p = Math.max(0, page), s = Math.clamp(size, 1, 100);
        var values = new ArrayList<>(Arrays.asList(args));
        values.add(s);
        values.add((long) p * s);
        long total = jdbc.queryForObject(count, Long.class, args);
        return Map.of(
                "content",
                rows(sql + " LIMIT ? OFFSET ?", values.toArray()),
                "page",
                p,
                "size",
                s,
                "totalElements",
                total);
    }
}
