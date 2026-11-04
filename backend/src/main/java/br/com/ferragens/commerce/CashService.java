package br.com.ferragens.commerce;
import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.shared.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.constraints.*;
import java.math.*;
import java.util.*;

@Service
@Transactional
public class CashService {
    private final Db db;private final CommandGate gate;
    public CashService(Db db,CommandGate gate){this.db=db;this.gate=gate;}
    public record Open(@NotNull BigDecimal amount){}
    public record Adjustment(@NotNull BigDecimal amount,@NotBlank String kind,@NotBlank @Size(max=500) String reason){}
    public record Close(@NotNull BigDecimal counted,@Size(max=500) String note){}
    @Transactional(readOnly=true)
    public Map<String,Object> overview(Actor actor){var rows=db.rows("SELECT * FROM cash_session WHERE store_id=? ORDER BY opened_at DESC LIMIT 30",actor.storeId());Map<String,Object> current=new LinkedHashMap<>();
        for(var row:rows)if(row.get("closedAt")==null){current=detail(actor,Db.id(row,"id"));break;}
        return Map.of("current",current,"sessions",rows);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> detail(Actor actor,UUID id){var session=db.one("SELECT c.*,u.username AS opened_by_name FROM cash_session c JOIN app_user u ON u.id=c.opened_by WHERE c.id=? AND c.store_id=?",id,actor.storeId());
        session.put("balance",balance(id).toPlainString());session.put("entries",db.rows("SELECT e.*,u.username AS actor_name FROM cash_entry e JOIN app_user u ON u.id=e.actor_id WHERE session_id=? ORDER BY occurred_at DESC,id DESC",id));return session;}
    public Map<String,Object> open(Actor actor,UUID key,Open body){UUID id=gate.run(actor,key,"cash:open",body,()->{Db.money(body.amount(),false);UUID target=UUID.randomUUID();db.jdbc.update("INSERT INTO cash_session(id,store_id,opened_by,opening_amount) VALUES(?,?,?,?)",target,actor.storeId(),actor.userId(),body.amount());db.audit(actor,"CASH_OPENED",target,"Abertura de caixa");return target;});return detail(actor,id);}
    public Map<String,Object> adjust(Actor actor,UUID key,Adjustment body){UUID id=gate.run(actor,key,"cash:adjust",body,()->{BigDecimal amount=Db.money(body.amount(),true);if(!Set.of("SUPPLY","WITHDRAWAL").contains(body.kind()))throw new BusinessRuleException("Escolha suprimento ou sangria.");return add(actor,body.kind().equals("WITHDRAWAL")?amount.negate():amount,body.kind(),Db.required(body.reason(),500,"o motivo"));});var row=db.one("SELECT session_id FROM cash_entry WHERE id=?",id);return detail(actor,Db.id(row,"sessionId"));}
    public Map<String,Object> close(Actor actor,UUID key,UUID id,Close body){UUID result=gate.run(actor,key,"cash:close:"+id,body,()->{db.one("SELECT id FROM cash_session WHERE id=? AND store_id=? AND closed_at IS NULL FOR UPDATE",id,actor.storeId());BigDecimal counted=Db.money(body.counted(),false),expected=balance(id),difference=counted.subtract(expected);String note=Db.optional(body.note(),500);if(difference.signum()!=0&&(note==null||note.length()<3))throw new BusinessRuleException("Explique a diferença entre o valor contado e o esperado.");db.jdbc.update("UPDATE cash_session SET closed_at=now(),closed_by=?,counted_amount=?,expected_amount=?,difference=?,closing_note=? WHERE id=?",actor.userId(),counted,expected,difference,note,id);db.audit(actor,"CASH_CLOSED",id,"Fechamento; diferença: "+difference);return id;});return detail(actor,result);}
    UUID requireOpen(Actor actor){return Db.id(db.rows("SELECT id FROM cash_session WHERE store_id=? AND closed_at IS NULL FOR UPDATE",actor.storeId()).stream().findFirst().orElseThrow(()->new ConflictException("Abra o caixa antes de registrar esta operação em dinheiro ou venda de balcão.")),"id");}
    UUID add(Actor actor,BigDecimal signed,String kind,String reason){UUID session=requireOpen(actor);if(balance(session).add(signed).signum()<0)throw new ConflictException("Dinheiro insuficiente na gaveta. Confira o caixa.");UUID id=UUID.randomUUID();db.jdbc.update("INSERT INTO cash_entry(id,session_id,amount,kind,reason,actor_id) VALUES(?,?,?,?,?,?)",id,session,signed,kind,reason,actor.userId());db.audit(actor,"CASH_ENTRY",id,kind+": "+reason);return id;}
    BigDecimal balance(UUID id){return db.jdbc.queryForObject("SELECT c.opening_amount+coalesce((SELECT sum(e.amount) FROM cash_entry e WHERE e.session_id=c.id),0) FROM cash_session c WHERE c.id=?",BigDecimal.class,id).setScale(2);}
}
