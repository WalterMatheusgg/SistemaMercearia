package br.com.ferragens.product;

import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.inventory.InventoryModels;
import br.com.ferragens.inventory.InventoryService;
import br.com.ferragens.shared.BusinessRuleException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ProductWorkflowService {
    private final ProductService products;
    private final ProductPhotoService photos;
    private final InventoryService inventory;
    private final EntityManager entityManager;

    public ProductWorkflowService(ProductService products, ProductPhotoService photos,
                                  InventoryService inventory, EntityManager entityManager) {
        this.products=products;
        this.photos=photos;
        this.inventory=inventory;
        this.entityManager=entityManager;
    }

    public record NewMaterial(@jakarta.validation.Valid ProductRequest product, BigDecimal initialQuantity) { }

    /** Produto, fotografia e quantidade inicial são confirmados na mesma transação. */
    @Transactional
    public ProductResponse create(Actor actor, NewMaterial data, MultipartFile photo) {
        if (data == null || data.product() == null)
            throw new BusinessRuleException("Dados do material não informados.");
        ProductPhotoService.Photo validated=photos.validate(photo);
        BigDecimal quantity=data.initialQuantity()==null?BigDecimal.ZERO:data.initialQuantity();
        if (quantity.signum()<0 || quantity.scale()>4 || quantity.precision()-quantity.scale()>15)
            throw new BusinessRuleException("Informe uma quantidade inicial válida.");
        ProductResponse product=products.create(data.product());
        // JdbcTemplate registra a foto e o saldo na mesma transação; antes,
        // a entidade JPA precisa existir fisicamente para satisfazer as FKs.
        entityManager.flush();
        photos.store(product.id(),validated);
        if (quantity.signum()>0) {
            var locations=inventory.locations(actor);
            if (locations.isEmpty()) throw new BusinessRuleException(
                    "Cadastre um local de estoque antes de informar o saldo inicial.");
            inventory.execute(actor,UUID.randomUUID(),new InventoryModels.Command(
                    product.id(),locations.getFirst().id(),InventoryModels.Kind.INITIAL,
                    quantity,"Contagem inicial no cadastro do produto",null,null));
        }
        return product;
    }

    @Transactional
    public ProductResponse update(UUID id,ProductRequest data,MultipartFile photo) {
        if(data==null) throw new BusinessRuleException("Dados do produto não informados.");
        ProductPhotoService.Photo validated=photo!=null && !photo.isEmpty()?photos.validate(photo):null;
        ProductResponse result=products.update(id,data);
        if(validated!=null) photos.store(id,validated);
        return result;
    }
}
