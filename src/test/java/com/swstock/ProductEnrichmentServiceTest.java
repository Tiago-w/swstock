package com.swstock;

import com.swstock.service.ProductEnrichmentService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductEnrichmentServiceTest {

    private final ProductEnrichmentService service = new ProductEnrichmentService();

    @Test
    void testBuscarSprayEtaniz() {
        ProductEnrichmentService.ProductMediaResult resultado = service.buscarEEnriquecer("spray da marca etaniz");
        assertNotNull(resultado);
        assertNotNull(resultado.generatedDescription());
        assertFalse(resultado.generatedDescription().trim().isEmpty());

        if (resultado.success()) {
            assertNotNull(resultado.primaryImageUrl(), "Deve encontrar uma URL de foto principal.");
            assertTrue(resultado.primaryImageUrl().startsWith("http"), "URL de imagem deve começar com http.");
            assertTrue(resultado.generatedDescription().toLowerCase().contains("spray") ||
                       resultado.generatedDescription().toLowerCase().contains("etaniz"),
                    "Descrição deve conter termos relativos a spray ou Etaniz.");
        }
    }

    @Test
    void testTermoVazio() {
        ProductEnrichmentService.ProductMediaResult resultado = service.buscarEEnriquecer("");
        assertNotNull(resultado);
        assertFalse(resultado.success());
    }
}
