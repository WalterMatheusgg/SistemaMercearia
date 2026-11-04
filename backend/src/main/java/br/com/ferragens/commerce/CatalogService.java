package br.com.ferragens.commerce;
import br.com.ferragens.shared.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

@Service
public class CatalogService {
    private final Db db;
    public CatalogService(Db db){this.db=db;}
    public Map<String,Object> catalog(String query,int page){String pattern="%"+(query==null?"":query.trim().toLowerCase(Locale.ROOT)).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        String from=" FROM product p JOIN unit u ON u.code=p.base_unit_code LEFT JOIN inventory_balance b ON b.product_id=p.id AND b.location_id=? WHERE p.active AND (lower(p.name) LIKE ? ESCAPE '\\' OR lower(p.code) LIKE ? ESCAPE '\\')";
        return db.page("SELECT p.id,p.code,p.name,p.description,p.price,u.code AS unit,u.quantity_scale,u.quantity_step,coalesce(b.physical-b.reserved-b.unavailable,0) AS available"+from+" ORDER BY p.name,p.id","SELECT count(*)"+from,page,20,Db.LOCATION,pattern,pattern);
    }
    public Map<String,Object> settings(){return db.one("SELECT s.name,c.phone,c.address,c.delivery_enabled,c.delivery_fee,c.payment_instructions FROM store s JOIN store_settings c ON c.store_id=s.id WHERE s.id=?",Db.STORE);}
    Map<String,Object> lockProduct(UUID product){return db.one("SELECT p.*,u.quantity_scale,u.quantity_step FROM product p JOIN unit u ON u.code=p.base_unit_code WHERE p.id=? FOR UPDATE OF p",product);}
    void quantity(Map<String,Object> product,BigDecimal value){if(value==null||value.signum()<=0||value.stripTrailingZeros().scale()>((Number)product.get("quantityScale")).intValue()||value.precision()-value.scale()>15||value.remainder(Db.decimal(product,"quantityStep")).signum()!=0)throw new BusinessRuleException("Quantidade inválida para "+product.get("name")+". Incremento: "+product.get("quantityStep")+" "+product.get("baseUnitCode"));}
}
