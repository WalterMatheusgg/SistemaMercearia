package br.com.ferragens.commerce;
import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.inventory.*;
import br.com.ferragens.inventory.InventoryModels.*;
import br.com.ferragens.shared.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class PurchasingService {
    private final Db db;private final CommandGate gate;private final CatalogService catalog;private final InventoryService inventory;private final FinanceService finance;
    public PurchasingService(Db db,CommandGate gate,CatalogService catalog,InventoryService inventory,FinanceService finance){this.db=db;this.gate=gate;this.catalog=catalog;this.inventory=inventory;this.finance=finance;}
    public record Item(@NotNull UUID productId,@NotNull BigDecimal quantity,@NotNull BigDecimal unitCost){}
    public record Order(@NotNull UUID supplierId,@NotEmpty @Size(max=100)List<@Valid Item> items,@NotNull LocalDate dueDate,@Size(max=500)String note){}
    public record Received(@NotNull UUID itemId,@NotNull BigDecimal quantity){}
    public record Receipt(@NotEmpty @Size(max=100)List<@Valid Received> items,@NotBlank @Size(min=3,max=500)String note){}
    @Transactional(readOnly=true)
    public Map<String,Object> list(Actor actor,int page){String from=" FROM purchase_order WHERE store_id=?";return db.page("SELECT *"+from+" ORDER BY created_at DESC,id DESC","SELECT count(*)"+from,page,20,actor.storeId());}
    @Transactional(readOnly=true)
    public Map<String,Object> detail(Actor actor,UUID id){var order=db.one("SELECT * FROM purchase_order WHERE id=? AND store_id=?",id,actor.storeId());order.put("items",db.rows("SELECT * FROM purchase_item WHERE purchase_id=? ORDER BY name,id",id));order.put("receipts",db.rows("SELECT r.*,u.username AS actor_name FROM purchase_receipt r JOIN app_user u ON u.id=r.actor_id WHERE purchase_id=? ORDER BY received_at DESC",id));return order;}
    public Map<String,Object> create(Actor actor,UUID key,Order body){UUID id=gate.run(actor,key,"purchase:create",body,()->{
        String supplier=Db.text(db.one("SELECT name FROM party WHERE id=? AND store_id=? AND kind='SUPPLIER' AND active",body.supplierId(),actor.storeId()),"name");
        if(body.items()==null||body.items().isEmpty()||body.items().size()>100||body.dueDate()==null)throw new BusinessRuleException("Informe fornecedor, itens e vencimento.");
        UUID order=UUID.randomUUID();BigDecimal total=BigDecimal.ZERO;var values=new ArrayList<Object[]>();Set<UUID> ids=new HashSet<>();
        for(var item:body.items().stream().sorted(Comparator.comparing(Item::productId)).toList()){if(!ids.add(item.productId()))throw new BusinessRuleException("Agrupe cada produto em uma única linha.");var p=catalog.lockProduct(item.productId());if(!Boolean.TRUE.equals(p.get("active")))throw new BusinessRuleException("Produto inativo.");catalog.quantity(p,item.quantity());BigDecimal cost=Db.money(item.unitCost(),false),line=Db.money(cost.multiply(item.quantity()).setScale(2,RoundingMode.HALF_UP),false);total=total.add(line);values.add(new Object[]{UUID.randomUUID(),order,item.productId(),p.get("code"),p.get("name"),p.get("baseUnitCode"),item.quantity(),cost,line});}
        db.jdbc.update("INSERT INTO purchase_order(id,store_id,supplier_id,supplier_name,total,due_date,note,created_by) VALUES(?,?,?,?,?,?,?,?)",order,actor.storeId(),body.supplierId(),supplier,Db.money(total,false),body.dueDate(),Db.optional(body.note(),500),actor.userId());
        for(var item:values)db.jdbc.update("INSERT INTO purchase_item(id,purchase_id,product_id,code,name,unit_code,quantity,unit_cost,line_total) VALUES(?,?,?,?,?,?,?,?,?)",item);
        db.audit(actor,"PURCHASE_CREATED",order,"Pedido ao fornecedor "+supplier);return order;
    });return detail(actor,id);}
    public Map<String,Object> receive(Actor actor,UUID key,UUID id,Receipt body){gate.run(actor,key,"purchase:receive:"+id,body,()->{
        var order=db.one("SELECT * FROM purchase_order WHERE id=? AND store_id=? FOR UPDATE",id,actor.storeId());if(!Set.of("OPEN","PARTIAL").contains(order.get("status")))throw new ConflictException("Pedido já encerrado.");
        if(body.items()==null||body.items().isEmpty()||body.items().size()>100)throw new BusinessRuleException("Informe os itens efetivamente recebidos.");String note=Db.required(body.note(),500,"o motivo");if(note.length()<3)throw new BusinessRuleException("Explique o recebimento.");
        var all=db.rows("SELECT * FROM purchase_item WHERE purchase_id=? ORDER BY product_id",id);var products=new HashMap<UUID,Map<String,Object>>();for(var item:all)products.put(Db.id(item,"productId"),catalog.lockProduct(Db.id(item,"productId")));
        UUID receipt=UUID.randomUUID();var received=new ArrayList<Object[]>();BigDecimal total=BigDecimal.ZERO;Set<UUID> seen=new HashSet<>();
        for(var line:body.items()){if(!seen.add(line.itemId()))throw new BusinessRuleException("Item repetido.");var item=all.stream().filter(i->Db.id(i,"id").equals(line.itemId())).findFirst().orElseThrow(()->new NotFoundException("Item não pertence ao pedido."));var p=products.get(Db.id(item,"productId"));if(!item.get("unitCode").equals(p.get("baseUnitCode")))throw new ConflictException("Unidade alterada desde o pedido. Confira o cadastro.");catalog.quantity(p,line.quantity());BigDecimal before=Db.decimal(item,"received"),after=before.add(line.quantity());if(after.compareTo(Db.decimal(item,"quantity"))>0)throw new ConflictException("Quantidade recebida supera o restante do pedido.");
            BigDecimal cost=Db.decimal(item,"unitCost");total=total.add(after.multiply(cost).setScale(2,RoundingMode.HALF_UP).subtract(before.multiply(cost).setScale(2,RoundingMode.HALF_UP)));
            var movement=inventory.execute(actor,UUID.randomUUID(),new Command(Db.id(item,"productId"),Db.LOCATION,Kind.ENTRY,line.quantity(),("Recebimento "+id+": "+note).substring(0,Math.min(500,("Recebimento "+id+": "+note).length())),null,null));db.jdbc.update("UPDATE purchase_item SET received=? WHERE id=?",after,line.itemId());received.add(new Object[]{UUID.randomUUID(),receipt,line.itemId(),line.quantity(),movement.id()});}
        db.jdbc.update("INSERT INTO purchase_receipt(id,purchase_id,total,actor_id,note) VALUES(?,?,?,?,?)",receipt,id,total,actor.userId(),note);
        for(var item:received)db.jdbc.update("INSERT INTO purchase_receipt_item(id,receipt_id,purchase_item_id,quantity,movement_id) VALUES(?,?,?,?,?)",item);
        finance.createTitle(actor,Db.id(order,"supplierId"),"PAYABLE","Recebimento do pedido "+id,total,LocalDate.parse(Db.text(order,"dueDate")),null,receipt);
        boolean full=Boolean.TRUE.equals(db.jdbc.queryForObject("SELECT bool_and(received=quantity) FROM purchase_item WHERE purchase_id=?",Boolean.class,id));db.jdbc.update("UPDATE purchase_order SET status=? WHERE id=?",full?"RECEIVED":"PARTIAL",id);db.audit(actor,"PURCHASE_RECEIVED",id,note+"; valor recebido: "+total);return receipt;
    });return detail(actor,id);}
    public Map<String,Object> cancel(Actor actor,UUID key,UUID id,String reason){gate.run(actor,key,"purchase:cancel:"+id,reason,()->{var order=db.one("SELECT status FROM purchase_order WHERE id=? AND store_id=? FOR UPDATE",id,actor.storeId());if(!order.get("status").equals("OPEN"))throw new ConflictException("Somente pedidos sem recebimentos podem ser cancelados.");db.jdbc.update("UPDATE purchase_order SET status='CANCELLED' WHERE id=?",id);db.audit(actor,"PURCHASE_CANCELLED",id,Db.required(reason,500,"o motivo"));return id;});return detail(actor,id);}
}
