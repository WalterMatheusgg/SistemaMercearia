package br.com.ferragens.product;

import br.com.ferragens.shared.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository products;

    @Mock
    private CategoryRepository categories;

    @Mock
    private UnitRepository units;

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private ProductService service;

    @Test
    void parsesFourOptionalFieldsAndSkipsCsvHeader() {
        var items = AssistantController.parseProductLines(
                "Nome;Preço;Custo;Quantidade\nParafuso;2,50;1,20;10\n;;;7\nBucha;;;\n");
        org.junit.jupiter.api.Assertions.assertEquals(3, items.size());
        org.junit.jupiter.api.Assertions.assertEquals("Parafuso", items.get(0).name());
        org.junit.jupiter.api.Assertions.assertEquals(10, items.get(0).quantity().intValueExact());
        org.junit.jupiter.api.Assertions.assertEquals("", items.get(1).name());
        org.junit.jupiter.api.Assertions.assertEquals(7, items.get(1).quantity().intValueExact());
        org.junit.jupiter.api.Assertions.assertEquals(0, items.get(2).quantity().intValueExact());
    }

    @Test
    void rejectsNegativeQuantityAndExtraColumns() {
        assertThrows(br.com.ferragens.shared.BusinessRuleException.class,
                () -> AssistantController.parseProductLines("Parafuso;1;2;-5"));
        assertThrows(br.com.ferragens.shared.BusinessRuleException.class,
                () -> AssistantController.parseProductLines("Parafuso;1;2;3;4"));
    }

    @Test
    void rejectsDuplicateCodeWithoutPersisting() {
        ProductRequest request = new ProductRequest("  fur-01 ", "Furadeira", null,
                UUID.randomUUID(), "UN", new BigDecimal("120.00"), new BigDecimal("199.90"));
        when(products.existsByCodeIgnoreCase("FUR-01")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.create(request));
        verify(products).existsByCodeIgnoreCase("FUR-01");
    }
}
