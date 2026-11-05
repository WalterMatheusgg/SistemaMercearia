package br.com.ferragens.product;

import br.com.ferragens.auth.CurrentActorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/products")
public class ProductPhotoController {
    private final ProductPhotoService photos;
    private final ProductWorkflowService workflow;
    private final CurrentActorService actors;

    public ProductPhotoController(ProductPhotoService photos,
            ProductWorkflowService workflow,CurrentActorService actors) {
        this.photos=photos;this.workflow=workflow;this.actors=actors;
    }

    @GetMapping("/{id}/photo")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STOCKKEEPER','SELLER','CASHIER','FINANCE')")
    public ResponseEntity<byte[]> image(@PathVariable UUID id) {
        return photos.get(id);
    }

    @PostMapping(value="/complete",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STOCKKEEPER')")
    public ProductResponse create(Authentication authentication,
            @Valid @RequestPart("data") ProductWorkflowService.NewMaterial data,
            @RequestPart(value="photo",required=false) MultipartFile photo) {
        return workflow.create(actors.require(authentication),data,photo);
    }

    @PutMapping(value="/{id}/complete",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STOCKKEEPER')")
    public ProductResponse update(@PathVariable UUID id,
            @Valid @RequestPart("data") ProductRequest data,
            @RequestPart(value="photo",required=false) MultipartFile photo) {
        return workflow.update(id,data,photo);
    }
}
