package br.com.ferragens.notification;
import br.com.ferragens.commerce.Db;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

@Service
public class Notifications {
    private final Db db;
    public Notifications(Db db){this.db=db;}
    public void transactional(UUID store,UUID user,String email,String key,String subject,String body){enqueue(store,user,email,key,"TRANSACTIONAL",subject,body);}
    public void event(UUID store,String key,String type,String subject,String body){
        String column=switch(type){case "SALE_COMPLETED"->"sale_completed";case "STOCK_LOW"->"stock_low";case "DAILY_SUMMARY"->"daily_summary";default->throw new IllegalArgumentException();};
        for(var user:db.rows("SELECT u.id,u.email FROM notification_preference n JOIN app_user u ON u.id=n.user_id WHERE u.store_id=? AND u.enabled AND u.email_verified AND u.email IS NOT NULL AND n."+column+" AND EXISTS(SELECT 1 FROM user_role r WHERE r.user_id=u.id AND (r.role_code IN ('ROLE_OWNER','ROLE_MANAGER') OR (?='STOCK_LOW' AND r.role_code='ROLE_STOCKKEEPER')))",store,type))
            enqueue(store,Db.id(user,"id"),Db.text(user,"email"),key,type,subject,body);
    }
    private void enqueue(UUID store,UUID user,String email,String key,String type,String subject,String body){db.jdbc.update("INSERT INTO notification_outbox(id,store_id,user_id,event_key,event_type,recipient,subject,body) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(user_id,event_key) DO NOTHING",UUID.randomUUID(),store,user,key,type,email,subject,body);}
    public void stock(UUID store,UUID product,String name,String unit,BigDecimal before,BigDecimal after,BigDecimal oldMin,BigDecimal minimum,String kind,UUID movement){
        if(after.compareTo(minimum)<=0&&(before.compareTo(oldMin)>0||kind.equals("INITIAL")||(kind.equals("MINIMUM")&&oldMin.compareTo(minimum)!=0)))event(store,"stock:"+movement,"STOCK_LOW","Estoque para reposição: "+name,"Produto: "+name+"\nDisponível: "+after.stripTrailingZeros().toPlainString()+" "+unit+"\nMínimo: "+minimum.stripTrailingZeros().toPlainString()+" "+unit+"\nConfira o estoque no sistema.");
    }
}
