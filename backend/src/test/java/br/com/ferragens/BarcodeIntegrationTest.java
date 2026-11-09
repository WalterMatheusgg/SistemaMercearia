package br.com.ferragens;

import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.product.BarcodeCatalogService;
import br.com.ferragens.shared.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BarcodeIntegrationTest {
    private static final String SCHEMA =
            "barcode_" + UUID.randomUUID().toString().replace("-", "");
    private static final UUID STORE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LOCATION = UUID.fromString("00000000-0000-0000-0000-000000000201");

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry properties) {
        IntegrationServices.configure(properties, SCHEMA);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired BarcodeCatalogService barcodes;

    @Test
    void generatedCodeIsUniqueAndLookupNeverWritesStock() {
        var actor = new Actor(UUID.randomUUID(), STORE, "test-owner");
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        UUID category = UUID.fromString("00000000-0000-0000-0000-000000000104");
        jdbc.update("INSERT INTO product (id,code,name,category_id,base_unit_code,cost,price)"
                + " VALUES (?,?,?,?,'UN',1,2)", a, "TEST-"+a, "Bucha 6 mm", category);
        jdbc.update("INSERT INTO product (id,code,name,category_id,base_unit_code,cost,price)"
                + " VALUES (?,?,?,?,'UN',1,2)", b, "TEST-"+b, "Parafuso 8 mm", category);

        var first = barcodes.byProduct(actor, LOCATION, a);
        var second = barcodes.byProduct(actor, LOCATION, b);
        assertThat(first.barcode()).matches("LF[0-9]{10}");
        assertThat(first.barcode()).isNotEqualTo(second.barcode());
        assertThat(first.available()).isEqualTo("0");
        assertThat(barcodes.byBarcode(actor, LOCATION, first.barcode().toLowerCase()))
                .isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movement"
                + " WHERE product_id IN (?,?)", Integer.class, a, b)).isZero();

        assertThatThrownBy(() -> barcodes.byBarcode(actor, LOCATION, "INVALID"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> barcodes.byBarcode(actor, UUID.randomUUID(), first.barcode()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> barcodes.byBarcode(
                new Actor(UUID.randomUUID(), UUID.randomUUID(), "other"), LOCATION, first.barcode()))
                .isInstanceOf(NotFoundException.class);
    }
}
