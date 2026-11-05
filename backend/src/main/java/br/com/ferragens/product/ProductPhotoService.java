package br.com.ferragens.product;

import br.com.ferragens.shared.BusinessRuleException;
import br.com.ferragens.shared.NotFoundException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

@Service
public class ProductPhotoService {
    private static final int MAX_BYTES = 3 * 1024 * 1024;
    private final JdbcTemplate jdbc;

    public ProductPhotoService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * Aceita exclusivamente imagens com assinatura PNG, JPEG ou WebP.
     * SVG e URLs externas não são aceitos, evitando código executável ou download arbitrário.
     */
    public Photo validate(MultipartFile uploaded) {
        if (uploaded == null || uploaded.isEmpty())
            throw new BusinessRuleException("Escolha uma foto do produto ou tire uma foto.");
        if (uploaded.getSize() > MAX_BYTES)
            throw new BusinessRuleException("A imagem deve ter no máximo 3 MB.");
        try {
            byte[] bytes = uploaded.getBytes();
            if (bytes.length < 12 || bytes.length > MAX_BYTES)
                throw new BusinessRuleException("Arquivo de imagem inválido.");
            String mime;
            if ((bytes[0] & 255) == 0xFF && (bytes[1] & 255) == 0xD8
                    && (bytes[2] & 255) == 0xFF) mime = "image/jpeg";
            else if ((bytes[0] & 255) == 0x89 && bytes[1] == 0x50
                    && bytes[2] == 0x4E && bytes[3] == 0x47
                    && bytes[4] == 0x0D && bytes[5] == 0x0A
                    && bytes[6] == 0x1A && bytes[7] == 0x0A) mime = "image/png";
            else if (new String(bytes,0,4, StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(bytes,8,4,StandardCharsets.US_ASCII).equals("WEBP"))
                mime = "image/webp";
            else throw new BusinessRuleException("Envie uma foto JPG, PNG ou WebP válida.");
            return new Photo(bytes,mime);
        } catch (IOException e) {
            throw new BusinessRuleException("Não foi possível ler a imagem.");
        }
    }

    @Transactional
    public void store(UUID productId, Photo image) {
        jdbc.update("INSERT INTO product_photo(product_id,content_type,image_bytes) VALUES(?,?,?) "
                + "ON CONFLICT(product_id) DO UPDATE SET content_type=excluded.content_type,"
                + " image_bytes=excluded.image_bytes,updated_at=now()",
                productId,image.mime(),image.bytes());
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> get(UUID productId) {
        var results = jdbc.query(
                "SELECT content_type,image_bytes FROM product_photo WHERE product_id=?",
                (rs,i) -> new Photo(rs.getBytes("image_bytes"),rs.getString("content_type")),
                productId);
        if (results.isEmpty()) throw new NotFoundException("Foto não cadastrada.");
        Photo photo = results.getFirst();
        return ResponseEntity.ok()
            .header("X-Content-Type-Options","nosniff")
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType(photo.mime()))
            .body(photo.bytes());
    }
    public record Photo(byte[] bytes,String mime) { }
}
