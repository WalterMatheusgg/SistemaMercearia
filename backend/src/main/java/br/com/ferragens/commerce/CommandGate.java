package br.com.ferragens.commerce;

import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.shared.ConflictException;

import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.Supplier;

@Component
public class CommandGate {
    private final Db db;
    private final ObjectMapper json;

    public CommandGate(Db db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    /** Caller must provide the domain transaction; no result is kept on rollback. */
    public UUID run(Actor actor, UUID key, String operation, Object body, Supplier<UUID> action) {
        if (key == null)
            throw new br.com.ferragens.shared.BusinessRuleException("Informe Idempotency-Key.");
        String fingerprint =
                hash(actor.userId() + "|" + operation + "|" + json.writeValueAsString(body));
        db.jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                (RowCallbackHandler) rs -> {},
                actor.storeId() + ":command:" + key);
        var previous =
                db.rows(
                        "SELECT fingerprint,result_id FROM business_command WHERE store_id=? AND"
                                + " idempotency_key=?",
                        actor.storeId(),
                        key);
        if (!previous.isEmpty()) {
            if (!previous.getFirst().get("fingerprint").equals(fingerprint))
                throw new ConflictException(
                        "Chave já utilizada com outro conteúdo. Confira o histórico.");
            return Db.id(previous.getFirst(), "resultId");
        }
        UUID result = action.get();
        db.jdbc.update(
                "INSERT INTO business_command(store_id,idempotency_key,fingerprint,result_id)"
                        + " VALUES(?,?,?,?)",
                actor.storeId(),
                key,
                fingerprint,
                result);
        return result;
    }

    public static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
