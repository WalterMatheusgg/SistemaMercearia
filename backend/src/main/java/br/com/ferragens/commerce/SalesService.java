package br.com.ferragens.commerce;
import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.commerce.FinanceService.Payment;
import br.com.ferragens.inventory.*;
import br.com.ferragens.inventory.InventoryModels.*;
import br.com.ferragens.notification.Notifications;
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
public class SalesService {
    private final Db db;private final CommandGate gate;private final CatalogService catalog;private final InventoryService inventory;private final FinanceService finance;private final CashService cash;private final Notifications notices;
    public SalesService(Db db,CommandGate gate,CatalogService catalog,InventoryService inventory,FinanceService finance,CashService cash,Notifications notices){this.db=db;this.gate=gate;this.catalog=catalog;this.inventory=inventory;this.finance=finance;this.cash=cash;this.notices=notices;}
    public record Item(@NotNull UUID productId,@NotNull BigDecimal quantity){}
    public record Order(UUID customerId,@NotEmpty @Size(max=100) List<@Valid Item> items,BigDecimal discount,@Size(max=500)String note,boolean quote,LocalDate dueDate,@Size(max=10)List<@Valid Payment> payments,BigDecimal expectedTotal){}
    public record Checkout(@NotEmpty @Size(max=100)List<@Valid Item> items,@NotBlank String fulfillment,@Size(max=500)String address,@NotBlank String paymentChoice,@Size(max=500)String note,BigDecimal expectedTotal){}
    public record Confirm(@Size(max=10)List<@Valid Payment> payments,LocalDate dueDate){}
    @Transactional(readOnly=true)
    public Map<String,Object> list(Actor actor,String status,int page){String from=" FROM sale WHERE store_id=? AND (?='' OR status=?)";return db.page("SELECT id,customer_name,channel,status,total,created_at,confirmed_at,fulfillment"+from+" ORDER BY created_at DESC,id DESC","SELECT count(*)"+from,page,20,actor.storeId(),status,status);}
    @Transactional(readOnly=true)
    public Map<String,Object> detail(Actor actor,UUID id){var sale=db.one("SELECT * FROM sale WHERE id=? AND store_id=?",id,actor.storeId());sale.put("items",db.rows("SELECT id,product_id,code,name,unit_code,quantity,unit_price,line_total FROM sale_item WHERE sale_id=? ORDER BY name,id",id));sale.put("account",db.rows("SELECT id,amount,paid,status,due_date FROM financial_account WHERE sale_id=?",id));sale.put("events",db.rows("SELECT event_type,detail,actor_name,occurred_at FROM audit_event WHERE entity_id=? AND store_id=? ORDER BY occurred_at,id",id,actor.storeId()));return sale;}
    @Transactional(readOnly=true)
    public Map<String,Object> preview(Order body){
        if(body.items()==null||body.items().isEmpty()||body.items().size()>100)throw new BusinessRuleException("Inclua os produtos.");
        BigDecimal subtotal=BigDecimal.ZERO;var lines=new ArrayList<Map<String,Object>>();Set<UUID> unique=new HashSet<>();
        for(var item:body.items()){if(!unique.add(item.productId()))throw new BusinessRuleException("Agrupe cada produto em uma linha.");var product=db.one("SELECT p.*,u.quantity_scale,u.quantity_step FROM product p JOIN unit u ON u.code=p.base_unit_code WHERE p.id=? AND p.active",item.productId());catalog.quantity(product,item.quantity());BigDecimal total=Db.decimal(product,"price").multiply(item.quantity()).setScale(2,RoundingMode.HALF_UP);subtotal=subtotal.add(total);lines.add(Map.of("productId",item.productId(),"name",product.get("name"),"quantity",item.quantity().toPlainString(),"price",product.get("price"),"total",total.toPlainString()));}
        BigDecimal discount=Db.money(body.discount()==null?BigDecimal.ZERO:body.discount(),false);if(discount.compareTo(subtotal)>0)throw new BusinessRuleException("Desconto acima do subtotal.");return Map.of("items",lines,"subtotal",subtotal.toPlainString(),"discount",discount.toPlainString(),"total",Db.money(subtotal.subtract(discount),false).toPlainString());
    }
    public Map<String,Object> create(Actor actor,UUID key,Order body){UUID id=gate.run(actor,key,"sale:create",body,()->{
        if(!body.quote())cash.requireOpen(actor);
        UUID customer=body.customerId();String customerName="Consumidor não identificado";
        if(customer!=null)customerName=Db.text(db.one("SELECT name FROM party WHERE id=? AND store_id=? AND kind='CUSTOMER' AND active",customer,actor.storeId()),"name");
        if(body.quote()&&body.payments()!=null&&!body.payments().isEmpty())throw new BusinessRuleException("Orçamento não recebe pagamentos.");
        UUID sale=createRecord(actor,customer,customerName,body.quote()?"QUOTE":"POS",body.items(),body.discount(),"PICKUP",null,BigDecimal.ZERO,null,body.note(),body.quote()?Instant.now().plus(Duration.ofDays(7)):null);
        if(body.expectedTotal()!=null&&body.expectedTotal().compareTo(Db.decimal(lockSale(actor,sale),"total"))!=0)throw new ConflictException("O preço mudou desde o cálculo. Calcule e confira o total novamente.");
        if(!body.quote())complete(actor,sale,body.payments(),body.dueDate(),true);
        db.audit(actor,body.quote()?"QUOTE_CREATED":"SALE_CREATED",sale,body.quote()?"Orçamento criado; validade de 7 dias":"Venda de balcão");return sale;
    });return detail(actor,id);}
    @Transactional(readOnly=true)
    public Map<String,Object> checkoutPreview(Checkout body){var totals=preview(new Order(null,body.items(),BigDecimal.ZERO,null,false,null,List.of(),null));var settings=db.one("SELECT delivery_enabled,delivery_fee FROM store_settings WHERE store_id=?",Db.STORE);BigDecimal fee=BigDecimal.ZERO;if(body.fulfillment().equals("DELIVERY")){if(!Boolean.TRUE.equals(settings.get("deliveryEnabled")))throw new BusinessRuleException("Entrega não disponível.");fee=Db.decimal(settings,"deliveryFee");}var result=new LinkedHashMap<>(totals);result.put("deliveryFee",fee.toPlainString());result.put("total",new BigDecimal(totals.get("total").toString()).add(fee).toPlainString());return result;}
    public Map<String,Object> checkout(Actor actor,UUID key,Checkout body){UUID id=gate.run(actor,key,"checkout",body,()->{
        if(!Boolean.TRUE.equals(db.one("SELECT email_verified FROM app_user WHERE id=?",actor.userId()).get("emailVerified")))throw new BusinessRuleException("Verifique seu e-mail em Minha conta antes de enviar o pedido.");
        var customer=db.one("SELECT p.id,p.name FROM party p JOIN app_user u ON u.id=p.user_id WHERE p.user_id=? AND p.store_id=? AND p.active AND u.email_verified",actor.userId(),actor.storeId());
        if(!Set.of("PICKUP","DELIVERY").contains(body.fulfillment())||!Set.of("PAY_ON_PICKUP","MANUAL_PIX","TRANSFER").contains(body.paymentChoice()))throw new BusinessRuleException("Entrega ou pagamento inválidos.");
        var config=db.one("SELECT * FROM store_settings WHERE store_id=?",actor.storeId());BigDecimal fee=BigDecimal.ZERO;
        if(body.fulfillment().equals("DELIVERY")){if(!Boolean.TRUE.equals(config.get("deliveryEnabled")))throw new BusinessRuleException("Entrega ainda não disponível. Escolha retirada.");Db.required(body.address(),500,"o endereço de entrega");fee=Db.decimal(config,"deliveryFee");if(body.paymentChoice().equals("PAY_ON_PICKUP"))throw new BusinessRuleException("Pagamento na retirada exige retirada na loja.");}
        Instant expires=Instant.now().plus(Duration.ofMinutes(((Number)config.get("reservationMinutes")).longValue()));
        UUID sale=createRecord(actor,Db.id(customer,"id"),Db.text(customer,"name"),"ONLINE",body.items(),BigDecimal.ZERO,body.fulfillment(),body.address(),fee,body.paymentChoice(),body.note(),expires);
        if(body.expectedTotal()!=null&&body.expectedTotal().compareTo(Db.decimal(lockSale(actor,sale),"total"))!=0)throw new ConflictException("Preços ou entrega mudaram. Calcule e confira o pedido novamente.");
        for(var item:items(sale)){UUID product=Db.id(item,"productId");ensureAvailable(product,Db.decimal(item,"quantity"));db.jdbc.update("UPDATE inventory_balance SET reserved=reserved+?,revision=revision+1,updated_at=now() WHERE location_id=? AND product_id=?",Db.decimal(item,"quantity"),Db.LOCATION,product);db.jdbc.update("INSERT INTO inventory_reservation(sale_item_id,location_id,product_id,quantity,status) VALUES(?,?,?,?,'ACTIVE')",Db.id(item,"id"),Db.LOCATION,product,Db.decimal(item,"quantity"));}
        db.audit(actor,"ORDER_PLACED",sale,"Pedido online reservado; aguardando confirmação manual do pagamento");return sale;
    });return customerDetail(actor,id);}
    public Map<String,Object> confirm(Actor actor,UUID key,UUID id,Confirm body){gate.run(actor,key,"sale:confirm:"+id,body,()->{var sale=lockSale(actor,id);String status=Db.text(sale,"status");if(!Set.of("QUOTE","PENDING_PAYMENT").contains(status))throw new ConflictException("Este pedido já foi confirmado ou encerrado.");
        if(sale.get("expiresAt")!=null&&Instant.parse(Db.text(sale,"expiresAt")).isBefore(Instant.now()))throw new ConflictException("Prazo encerrado. Atualize a lista; pedidos vencidos serão liberados automaticamente.");
        boolean pos=status.equals("QUOTE");if(pos)cash.requireOpen(actor);lockItems(id);if(!pos){BigDecimal paid=sumPayments(body.payments());if(paid.compareTo(Db.decimal(sale,"total"))!=0)throw new BusinessRuleException("Pedido online exige confirmação integral do valor recebido.");releaseReservations(id,"CONSUMED");}
        complete(actor,id,body.payments(),body.dueDate(),pos);db.audit(actor,"SALE_CONFIRMED",id,"Venda confirmada pela loja");return id;});return detail(actor,id);}
    public Map<String,Object> cancel(Actor actor,UUID key,UUID id,String reason,boolean customer){String note=Db.required(reason,500,"o motivo");if(note.length()<3)throw new BusinessRuleException("Informe um motivo com pelo menos 3 caracteres.");
        if(customer)requireCustomerOrder(actor,id);
        gate.run(actor,key,"sale:cancel:"+id,note,()->{var sale=lockSale(actor,id);String status=Db.text(sale,"status");if(Set.of("CANCELLED","EXPIRED").contains(status))throw new ConflictException("Pedido já encerrado.");if(customer&&!status.equals("PENDING_PAYMENT"))throw new ConflictException("Procure a loja para devolver uma compra já confirmada.");lockItems(id);
            if(status.equals("PENDING_PAYMENT"))releaseReservations(id,"RELEASED");
            else if(!status.equals("QUOTE")){for(var item:items(id)){UUID movement=Db.id(item,"movementId");inventory.execute(actor,UUID.randomUUID(),new Command(Db.id(item,"productId"),Db.LOCATION,Kind.REVERSAL,Db.decimal(item,"quantity"),("Devolução integral da venda "+id+": "+note).substring(0,Math.min(500,("Devolução integral da venda "+id+": "+note).length())),null,movement));}finance.cancelTitle(actor,id,note);}
            db.jdbc.update("UPDATE sale SET status='CANCELLED',cancelled_at=now(),cancellation_reason=? WHERE id=?",note,id);db.audit(actor,"SALE_CANCELLED",id,note);if(!status.equals("QUOTE")&&!status.equals("PENDING_PAYMENT"))notices.event(actor.storeId(),"sale-cancelled:"+id,"SALE_COMPLETED","Venda cancelada / devolvida","Venda: "+id+"\nMotivo: "+note+"\nConfira as compensações no sistema.");return id;});return customer?customerDetail(actor,id):detail(actor,id);}
    public Map<String,Object> fulfillment(Actor actor,UUID key,UUID id,String status){gate.run(actor,key,"sale:fulfillment:"+id,status,()->{var sale=lockSale(actor,id);String before=Db.text(sale,"status");boolean delivery=sale.get("fulfillment").equals("DELIVERY");boolean allowed=before.equals("PAID")&&status.equals("READY")||before.equals("READY")&&status.equals(delivery?"DISPATCHED":"COMPLETED")||before.equals("DISPATCHED")&&status.equals("COMPLETED");if(!allowed)throw new ConflictException("Transição inválida. Confirme pagamento, prepare e conclua retirada/entrega na ordem indicada.");db.jdbc.update("UPDATE sale SET status=? WHERE id=?",status,id);db.audit(actor,"ORDER_"+status,id,"Andamento atualizado para "+status);return id;});return detail(actor,id);}
    @Transactional(readOnly=true)
    public List<Map<String,Object>> customerOrders(Actor actor){return db.rows("SELECT s.id,s.status,s.channel,s.total,s.fulfillment,s.created_at,s.expires_at FROM sale s JOIN party p ON p.id=s.customer_id WHERE p.user_id=? AND s.store_id=? ORDER BY s.created_at DESC LIMIT 100",actor.userId(),actor.storeId());}
    @Transactional(readOnly=true)
    public Map<String,Object> customerDetail(Actor actor,UUID id){requireCustomerOrder(actor,id);var sale=detail(actor,id);sale.remove("createdBy");sale.remove("events");return sale;}
    public void expire(UUID id){var found=db.rows("SELECT * FROM sale WHERE id=? AND status='PENDING_PAYMENT' AND expires_at<now() FOR UPDATE",id);if(found.isEmpty())return;var sale=found.getFirst();Actor actor=actorFor(sale);lockItems(id);releaseReservations(id,"RELEASED");db.jdbc.update("UPDATE sale SET status='EXPIRED',cancelled_at=now(),cancellation_reason='Prazo da reserva expirado' WHERE id=?",id);db.audit(actor,"ORDER_EXPIRED",id,"Reserva liberada pelo prazo");}
    private UUID createRecord(Actor actor,UUID customer,String customerName,String channel,List<Item> entries,BigDecimal discount,String fulfillment,String address,BigDecimal fee,String choice,String note,Instant expires){
        if(entries==null||entries.isEmpty()||entries.size()>100)throw new BusinessRuleException("Inclua de 1 a 100 produtos.");Set<UUID> unique=new HashSet<>();var lines=new ArrayList<Map<String,Object>>();BigDecimal subtotal=BigDecimal.ZERO;
        for(Item item:entries.stream().sorted(Comparator.comparing(Item::productId)).toList()){if(!unique.add(item.productId()))throw new BusinessRuleException("Agrupe a quantidade de cada produto em uma única linha.");var product=catalog.lockProduct(item.productId());if(!Boolean.TRUE.equals(product.get("active")))throw new BusinessRuleException("Produto inativo.");catalog.quantity(product,item.quantity());BigDecimal price=Db.decimal(product,"price"),line=Db.money(price.multiply(item.quantity()).setScale(2,RoundingMode.HALF_UP),false);product.put("orderedQuantity",item.quantity());product.put("lineTotal",line);lines.add(product);subtotal=subtotal.add(line);}
        BigDecimal reduction=Db.money(discount==null?BigDecimal.ZERO:discount,false);if(reduction.compareTo(subtotal)>0)throw new BusinessRuleException("Desconto maior que o subtotal.");UUID id=UUID.randomUUID();String status=channel.equals("QUOTE")?"QUOTE":channel.equals("ONLINE")?"PENDING_PAYMENT":"COMPLETED";BigDecimal total=Db.money(subtotal.subtract(reduction).add(fee),false);
        db.jdbc.update("INSERT INTO sale(id,store_id,customer_id,customer_name,channel,status,subtotal,discount,delivery_fee,total,fulfillment,delivery_address,payment_choice,note,created_by,expires_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,actor.storeId(),customer,customerName,channel,status,subtotal,reduction,fee,total,fulfillment,Db.optional(address,500),choice,Db.optional(note,500),actor.userId(),expires==null?null:OffsetDateTime.ofInstant(expires,ZoneOffset.UTC));
        for(var p:lines)db.jdbc.update("INSERT INTO sale_item(id,sale_id,product_id,code,name,unit_code,quantity,unit_price,unit_cost,line_total) VALUES(?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),id,Db.id(p,"id"),p.get("code"),p.get("name"),p.get("baseUnitCode"),p.get("orderedQuantity"),Db.decimal(p,"price"),Db.decimal(p,"cost"),p.get("lineTotal"));return id;
    }
    private void complete(Actor actor,UUID sale,List<FinanceService.Payment> payments,LocalDate due,boolean pos){var order=lockSale(actor,sale);if(Db.decimal(order,"total").subtract(sumPayments(payments)).signum()>0&&order.get("customerId")==null)throw new BusinessRuleException("Identifique o cliente para deixar valor a receber.");if(sumPayments(payments).compareTo(Db.decimal(order,"total"))>0)throw new BusinessRuleException("Pagamentos acima do total. Informe o valor líquido, sem o troco.");lockItems(sale);
        UUID title=finance.createTitle(actor,Db.id(order,"customerId"),"RECEIVABLE","Venda "+sale,Db.decimal(order,"total"),due==null?Db.today():due,sale,null);
        for(var item:items(sale)){var movement=inventory.execute(actor,UUID.randomUUID(),new Command(Db.id(item,"productId"),Db.LOCATION,Kind.EXIT,Db.decimal(item,"quantity"),"Venda "+sale,null,null));db.jdbc.update("UPDATE sale_item SET movement_id=? WHERE id=?",movement.id(),Db.id(item,"id"));}
        if(payments!=null)for(var payment:payments)finance.pay(actor,title,payment);
        db.jdbc.update("UPDATE sale SET channel=CASE WHEN channel='QUOTE' THEN 'POS' ELSE channel END,status=?,confirmed_at=now(),expires_at=NULL WHERE id=?",pos?"COMPLETED":"PAID",sale);
        notices.event(actor.storeId(),"sale:"+sale,"SALE_COMPLETED","Venda concluída","Venda: "+sale+"\nTotal: R$ "+order.get("total")+"\nResponsável: "+actor.username()+"\nConfira pagamentos e recebíveis no sistema.");
    }
    private BigDecimal sumPayments(List<FinanceService.Payment> payments){BigDecimal total=BigDecimal.ZERO;if(payments!=null){if(payments.size()>10)throw new BusinessRuleException("Use no máximo dez parcelas de pagamento.");for(var p:payments)total=total.add(Db.money(p.amount(),true));}return total;}
    private void requireCustomerOrder(Actor actor,UUID id){db.one("SELECT s.id FROM sale s JOIN party p ON p.id=s.customer_id WHERE s.id=? AND p.user_id=? AND s.store_id=?",id,actor.userId(),actor.storeId());}
    private Map<String,Object> lockSale(Actor actor,UUID id){return db.one("SELECT * FROM sale WHERE id=? AND store_id=? FOR UPDATE",id,actor.storeId());}
    private List<Map<String,Object>> items(UUID id){return db.rows("SELECT * FROM sale_item WHERE sale_id=? ORDER BY product_id",id);}
    private void lockItems(UUID id){for(var item:items(id))catalog.lockProduct(Db.id(item,"productId"));}
    private void ensureAvailable(UUID product,BigDecimal quantity){db.jdbc.update("INSERT INTO inventory_balance(location_id,product_id) VALUES(?,?) ON CONFLICT DO NOTHING",Db.LOCATION,product);var b=db.one("SELECT physical-reserved-unavailable AS available FROM inventory_balance WHERE location_id=? AND product_id=? FOR UPDATE",Db.LOCATION,product);if(Db.decimal(b,"available").compareTo(quantity)<0)throw new ConflictException("Quantidade indisponível. Atualize o catálogo.");}
    private void releaseReservations(UUID id,String status){for(var item:items(id)){int changed=db.jdbc.update("UPDATE inventory_reservation SET status=?,finished_at=now() WHERE sale_item_id=? AND status='ACTIVE'",status,Db.id(item,"id"));if(changed!=1)throw new ConflictException("Reserva já encerrada. Confira o pedido.");db.jdbc.update("UPDATE inventory_balance SET reserved=reserved-?,revision=revision+1,updated_at=now() WHERE location_id=? AND product_id=?",Db.decimal(item,"quantity"),Db.LOCATION,Db.id(item,"productId"));}}
    private Actor actorFor(Map<String,Object> sale){var user=db.one("SELECT username FROM app_user WHERE id=?",Db.id(sale,"createdBy"));return new Actor(Db.id(sale,"createdBy"),Db.id(sale,"storeId"),Db.text(user,"username"));}
}
