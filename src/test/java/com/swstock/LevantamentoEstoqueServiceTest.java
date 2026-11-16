package com.swstock;

import com.swstock.model.Produto;
import com.swstock.model.ProdutoCor;
import com.swstock.model.ProdutoSubcategoria;
import com.swstock.service.LevantamentoEstoqueService;
import com.swstock.service.LevantamentoEstoqueService.FiltroEstoque;
import com.swstock.service.LevantamentoEstoqueService.Linha;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LevantamentoEstoqueServiceTest {

    @Test
    void testProdutoComCoresGeraLinhaPorCor() {
        Produto p = new Produto(1, "Esmalte Sintético", "Tintas", 35.0, 40.0, "ESM-01", "A1", 15, null, null);
        Map<Integer, List<ProdutoCor>> mapaCores = new HashMap<>();
        mapaCores.put(1, List.of(
                new ProdutoCor(10, 1, "AZUL", 10),
                new ProdutoCor(11, 1, "VERMELHO", 5),
                new ProdutoCor(12, 1, "AMARELO", 0)
        ));

        // Filtro TODOS: deve retornar 3 linhas, incluindo a cor zerada
        List<Linha> linhasTodos = LevantamentoEstoqueService.montarLinhas(
                List.of(p), mapaCores, Collections.emptyMap(), "Todas as Subcategorias", FiltroEstoque.TODOS);
        assertEquals(3, linhasTodos.size());
        assertEquals("AZUL", linhasTodos.get(0).cor());
        assertEquals(10, linhasTodos.get(0).quantidade());
        assertEquals("VERMELHO", linhasTodos.get(1).cor());
        assertEquals(5, linhasTodos.get(1).quantidade());
        assertEquals("AMARELO", linhasTodos.get(2).cor());
        assertEquals(0, linhasTodos.get(2).quantidade());

        // Filtro MAIOR_QUE_ZERO: 2 linhas
        List<Linha> linhasMaior = LevantamentoEstoqueService.montarLinhas(
                List.of(p), mapaCores, Collections.emptyMap(), "Todas as Subcategorias", FiltroEstoque.MAIOR_QUE_ZERO);
        assertEquals(2, linhasMaior.size());

        // Filtro IGUAL_A_ZERO: 1 linha (AMARELO)
        List<Linha> linhasZero = LevantamentoEstoqueService.montarLinhas(
                List.of(p), mapaCores, Collections.emptyMap(), "Todas as Subcategorias", FiltroEstoque.IGUAL_A_ZERO);
        assertEquals(1, linhasZero.size());
        assertEquals("AMARELO", linhasZero.get(0).cor());
    }

    @Test
    void testSubcategoriaComItensZeradosApareceNoRelatorio() {
        Produto p = new Produto(2, "Lixa d'Água", "Abrasivos", 2.5, 3.0, "LIX-01", "B2", 100, null, null);
        p.setTemSubcategorias(true);

        Map<Integer, List<ProdutoSubcategoria>> mapaSubs = new HashMap<>();
        mapaSubs.put(2, List.of(
                new ProdutoSubcategoria(1, 2, "GRÃO 400", 50),
                new ProdutoSubcategoria(2, 2, "GRÃO 600", 0),
                new ProdutoSubcategoria(3, 2, "GRÃO 1200", 0)
        ));

        // Filtrando pela subcategoria GRÃO 600 que está ZERADA: deve aparecer se o filtro for TODOS ou IGUAL_A_ZERO
        List<Linha> linhas = LevantamentoEstoqueService.montarLinhas(
                List.of(p), Collections.emptyMap(), mapaSubs, "GRÃO 600", FiltroEstoque.TODOS);
        assertEquals(1, linhas.size());
        assertEquals("GRÃO 600", linhas.get(0).subcategoria());
        assertEquals(0, linhas.get(0).quantidade());

        // Filtro IGUAL_A_ZERO
        List<Linha> linhasZero = LevantamentoEstoqueService.montarLinhas(
                List.of(p), Collections.emptyMap(), mapaSubs, "GRÃO 600", FiltroEstoque.IGUAL_A_ZERO);
        assertEquals(1, linhasZero.size());

        // Filtro MAIOR_QUE_ZERO: deve ficar vazia
        List<Linha> linhasMaior = LevantamentoEstoqueService.montarLinhas(
                List.of(p), Collections.emptyMap(), mapaSubs, "GRÃO 600", FiltroEstoque.MAIOR_QUE_ZERO);
        assertTrue(linhasMaior.isEmpty());
    }

    @Test
    void testProdutoComCoresESubcategoriaJuntas() {
        Produto p = new Produto(3, "Tinta Acrílica Premium", "Tintas", 120.0, 140.0, "ACR-01", "C1", 20, null, null);
        p.setSubcategoria("FOSCA");

        Map<Integer, List<ProdutoCor>> mapaCores = new HashMap<>();
        mapaCores.put(3, List.of(
                new ProdutoCor(20, 3, "BRANCO NEVE", 8),
                new ProdutoCor(21, 3, "GELO", 4)
        ));

        // Filtrando pela subcategoria FOSCA: as variações de cor devem aparecer com suas cores corretas!
        List<Linha> linhas = LevantamentoEstoqueService.montarLinhas(
                List.of(p), mapaCores, Collections.emptyMap(), "FOSCA", FiltroEstoque.TODOS);
        assertEquals(2, linhas.size());
        assertEquals("BRANCO NEVE", linhas.get(0).cor());
        assertEquals("FOSCA", linhas.get(0).subcategoria());
        assertEquals("GELO", linhas.get(1).cor());
        assertEquals("FOSCA", linhas.get(1).subcategoria());
    }

    @Test
    void testProdutoComCoresESubcategoriasNaoDuplicaEstoque() {
        Produto p = new Produto(4, "Tinta Acrílica Semibrilho", "Tintas", 130.0, 150.0, "ACR-02", "C2", 12, null, null);
        Map<Integer, List<ProdutoCor>> mapaCores = new HashMap<>();
        mapaCores.put(4, List.of(
                new ProdutoCor(30, 4, "BRANCO", 7),
                new ProdutoCor(31, 4, "CINZA", 5)
        ));

        Map<Integer, List<ProdutoSubcategoria>> mapaSubs = new HashMap<>();
        mapaSubs.put(4, List.of(
                new ProdutoSubcategoria(10, 4, "3.6L", 12)
        ));

        // Quando o produto tem cores e também subcategoria cadastrada, deve gerar as linhas das cores e NÃO duplicar com linha "PADRÃO"
        List<Linha> linhas = LevantamentoEstoqueService.montarLinhas(
                List.of(p), mapaCores, mapaSubs, "Todas as Subcategorias", FiltroEstoque.TODOS);
        assertEquals(2, linhas.size());
        assertEquals("BRANCO", linhas.get(0).cor());
        assertEquals("3.6L", linhas.get(0).subcategoria());
        assertEquals("CINZA", linhas.get(1).cor());
        assertEquals("3.6L", linhas.get(1).subcategoria());
    }

    @Test
    void testFiltroEstoqueDeRotulo() {
        assertEquals(FiltroEstoque.TODOS, FiltroEstoque.deRotulo("Todos (≥ 0)"));
        assertEquals(FiltroEstoque.TODOS, FiltroEstoque.deRotulo("Todos os Produtos"));
        assertEquals(FiltroEstoque.TODOS, FiltroEstoque.deRotulo("Todas as Bases"));
        assertEquals(FiltroEstoque.TODOS, FiltroEstoque.deRotulo("Todas (≥ 0)"));
        assertEquals(FiltroEstoque.TODOS, FiltroEstoque.deRotulo(null));

        assertEquals(FiltroEstoque.MAIOR_QUE_ZERO, FiltroEstoque.deRotulo("Maior que zero (> 0)"));
        assertEquals(FiltroEstoque.MAIOR_QUE_ZERO, FiltroEstoque.deRotulo("Apenas com Estoque (> 0)"));
        assertEquals(FiltroEstoque.MAIOR_QUE_ZERO, FiltroEstoque.deRotulo("APENAS_ESTOQUE"));

        assertEquals(FiltroEstoque.IGUAL_A_ZERO, FiltroEstoque.deRotulo("Igual a zero (= 0)"));
        assertEquals(FiltroEstoque.IGUAL_A_ZERO, FiltroEstoque.deRotulo("Apenas Zerados (= 0)"));
        assertEquals(FiltroEstoque.IGUAL_A_ZERO, FiltroEstoque.deRotulo("Apenas Zeradas (= 0)"));
    }
}
