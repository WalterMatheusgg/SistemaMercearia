package br.com.ferragens.product;

import br.com.ferragens.shared.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private ProductService service;

    @Test
    void rejectsDuplicateCodeWithoutPersisting() {
        ProductRequest request = new ProductRequest("  fur-01 ", "Furadeira", null,
                UUID.randomUUID(), "UN", new BigDecimal("120.00"), new BigDecimal("199.90"));
        when(products.existsByCodeIgnoreCase("FUR-01")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.create(request));
        verify(products).existsByCodeIgnoreCase("FUR-01");
    }
}