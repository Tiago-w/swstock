package com.swstock;

import com.swstock.database.DatabaseManager;
import com.swstock.database.ProdutoCorDAO;
import com.swstock.database.ProdutoDAO;
import com.swstock.model.Produto;
import com.swstock.model.ProdutoCor;
import com.swstock.controller.ProductColorsController;
import com.swstock.controller.ProductDetailController;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ProdutoCorDAOTest {

    private DatabaseManager dbManager;
    private ProdutoDAO produtoDAO;
    private ProdutoCorDAO produtoCorDAO;
    private static final String TEST_DB = "test_cores_swstock.db";

    @BeforeAll
    void setUp() throws Exception {
        new File(TEST_DB).delete();
        dbManager = DatabaseManager.getInstance(TEST_DB);
        produtoDAO = new ProdutoDAO(dbManager);
        produtoCorDAO = new ProdutoCorDAO(dbManager);
    }

    @AfterAll
    void tearDown() {
        if (dbManager != null) {
            dbManager.close();
        }
        new File(TEST_DB).delete();
    }

    @BeforeEach
    void cleanDb() throws SQLException {
        try (var conn = dbManager.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM produto_cores;");
            stmt.execute("DELETE FROM produtos;");
            stmt.execute("DELETE FROM historico_estoque;");
        }
    }

    @Test
    void testAdicionarEListarCores() throws SQLException {
        Produto p = new Produto("Produto Teste 1", "Grupo A", 20.0, 25.0, "SKU-90001", "Estante A1", 0, null, null);
        produtoDAO.upsert(p);
        assertNotNull(p.getId());

        boolean addAzul = produtoCorDAO.addCor(p.getId(), "Azul", 10);
        boolean addVermelho = produtoCorDAO.addCor(p.getId(), "Vermelho", 5);

        assertTrue(addAzul);
        assertTrue(addVermelho);

        List<ProdutoCor> cores = produtoCorDAO.findByProdutoId(p.getId());
        assertEquals(2, cores.size());
        assertEquals("AZUL", cores.get(0).getNomeCor());
        assertEquals(10, cores.get(0).getQuantidade());
        assertEquals("VERMELHO", cores.get(1).getNomeCor());
        assertEquals(5, cores.get(1).getQuantidade());
    }

    @Test
    void testAtualizarQuantidadeCor() throws SQLException {
        Produto p = new Produto("Produto Teste 2", "Grupo B", 50.0, 55.0, "SKU-90002", "Estante B1", 0, null, null);
        produtoDAO.upsert(p);

        produtoCorDAO.addCor(p.getId(), "Preto Fosco", 8);
        List<ProdutoCor> cores = produtoCorDAO.findByProdutoId(p.getId());
        assertEquals(1, cores.size());

        ProdutoCor cor = cores.get(0);
        boolean atualizado = produtoCorDAO.updateQuantidade(cor.getId(), 15, "Ajuste de Inventário", "Operador 1");
        assertTrue(atualizado);

        ProdutoCor recarregada = produtoCorDAO.findById(cor.getId());
        assertEquals(15, recarregada.getQuantidade());
    }

    @Test
    void testTotalQuantidadeCores() throws SQLException {
        Produto p = new Produto("Produto Teste 3", "Grupo C", 30.0, 35.0, "SKU-90003", "Estante C1", 0, null, null);
        produtoDAO.upsert(p);

        produtoCorDAO.addCor(p.getId(), "Branco", 12);
        produtoCorDAO.addCor(p.getId(), "Amarelo", 8);
        produtoCorDAO.addCor(p.getId(), "Verde", 4);

        int total = produtoCorDAO.getTotalQuantidadeCores(p.getId());
        assertEquals(24, total);
    }

    @Test
    void testExcluirCor() throws SQLException {
        Produto p = new Produto("Produto Teste 4", "Grupo D", 40.0, 45.0, "SKU-90004", "Estante D1", 0, null, null);
        produtoDAO.upsert(p);

        produtoCorDAO.addCor(p.getId(), "Incolor", 10);
        List<ProdutoCor> cores = produtoCorDAO.findByProdutoId(p.getId());
        assertEquals(1, cores.size());

        boolean excluido = produtoCorDAO.deleteCor(cores.get(0).getId());
        assertTrue(excluido);

        List<ProdutoCor> coresApos = produtoCorDAO.findByProdutoId(p.getId());
        assertTrue(coresApos.isEmpty());
    }

    @Test
    void testMotivosHistoricoCores() throws SQLException {
        Produto p = new Produto("Produto Teste 5", "Grupo A", 20.0, 22.0, "SKU-99001", "Estante A1", 0, null, null);
        produtoDAO.upsert(p);

        // 1. Criar cor sem estoque
        produtoCorDAO.addCor(p.getId(), "Preto", 0, "Operador 1");
        // 2. Criar cor com estoque
        produtoCorDAO.addCor(p.getId(), "Azul", 10, "Operador 2");

        List<ProdutoCor> cores = produtoCorDAO.findByProdutoId(p.getId());
        ProdutoCor corAzul = cores.stream().filter(c -> c.getNomeCor().equals("AZUL")).findFirst().orElseThrow();

        // 3. Atualizar estoque da cor
        produtoCorDAO.updateQuantidade(corAzul.getId(), 15, null, "Operador 3");

        // 4. Excluir cor
        produtoCorDAO.deleteCor(corAzul.getId(), "Operador 4");

        var historicoDAO = new com.swstock.database.HistoricoEstoqueDAO(dbManager);
        var hist = historicoDAO.findByProduto(p.getId());

        assertEquals(4, hist.size());
        // Ordem cronológica decrescente:
        assertEquals("Exclusão de cor [AZUL]", hist.get(0).getMotivo());
        assertEquals("Modificação de estoque [AZUL]", hist.get(1).getMotivo());
        assertEquals("Cadastro de cor [AZUL] / Modificação de estoque", hist.get(2).getMotivo());
        assertEquals("Cadastro de cor [PRETO]", hist.get(3).getMotivo());
    }

    @Test
    void testProductDetailControllerColorCyclingVerdeVermelho() {
        ProductDetailController controller = new ProductDetailController();
        List<String> items = new ArrayList<>();
        items.add("Nenhuma Cor (Estoque Geral)");
        items.add("AZUL (10 un.)");
        items.add("VERDE (5 un.)");
        items.add("VERMELHO (8 un.)");
        items.add("VINHO (2 un.)");

        long time = 1000L;

        // 1. Pressiona 'v' a partir do índice 0 ("Nenhuma Cor") -> deve ir para VERDE (índice 2)
        int idx1 = controller.calcularProximoIndiceCor(items, 0, "v", KeyCode.V, time);
        assertEquals(2, idx1, "Pressionar 'v' a partir de 0 deve ir para VERDE");

        // 2. Pressiona 'v' novamente a partir de VERDE (índice 2) -> deve ir para VERMELHO (índice 3)
        time += 300L;
        int idx2 = controller.calcularProximoIndiceCor(items, idx1, "v", KeyCode.V, time);
        assertEquals(3, idx2, "Pressionar 'v' a partir de VERDE deve ir para VERMELHO");

        // 3. Pressiona 'v' novamente a partir de VERMELHO (índice 3) -> deve ir para VINHO (índice 4)
        time += 300L;
        int idx3 = controller.calcularProximoIndiceCor(items, idx2, "v", KeyCode.V, time);
        assertEquals(4, idx3, "Pressionar 'v' a partir de VERMELHO deve ir para VINHO");

        // 4. Pressiona 'v' novamente a partir de VINHO (índice 4) -> wrap-around volta para VERDE (índice 2)
        time += 300L;
        int idx4 = controller.calcularProximoIndiceCor(items, idx3, "v", KeyCode.V, time);
        assertEquals(2, idx4, "Pressionar 'v' no final deve voltar em loop para VERDE");
    }

    @Test
    void testProductDetailControllerUppercaseAndFastTyping() {
        ProductDetailController controller = new ProductDetailController();
        List<String> items = new ArrayList<>();
        items.add("Nenhuma Cor (Estoque Geral)");
        items.add("AZUL (10 un.)");
        items.add("VERDE (5 un.)");
        items.add("VERMELHO (8 un.)");

        long time = 2000L;

        // Maiúscula 'V'
        int idx1 = controller.calcularProximoIndiceCor(items, 0, "V", KeyCode.V, time);
        assertEquals(2, idx1, "Letra 'V' maiúscula deve encontrar VERDE");

        time += 200L;
        int idx2 = controller.calcularProximoIndiceCor(items, idx1, "V", KeyCode.V, time);
        assertEquals(3, idx2, "Letra 'V' maiúscula repetida deve ir para VERMELHO");

        // Digitação multi-caractere rápida: "v" -> "e" -> "r" -> "m"
        controller = new ProductDetailController();
        time = 5000L;
        int t1 = controller.calcularProximoIndiceCor(items, 0, "v", KeyCode.V, time);
        assertEquals(2, t1); // VERDE

        time += 100L;
        int t2 = controller.calcularProximoIndiceCor(items, t1, "e", KeyCode.E, time);
        assertEquals(2, t2); // prefixo "ve" ainda casa com VERDE

        time += 100L;
        int t3 = controller.calcularProximoIndiceCor(items, t2, "r", KeyCode.R, time);
        assertEquals(2, t3); // prefixo "ver" ainda casa com VERDE

        time += 100L;
        int t4 = controller.calcularProximoIndiceCor(items, t3, "m", KeyCode.M, time);
        assertEquals(3, t4, "Prefixo 'verm' deve selecionar VERMELHO diretamente");
    }

    @Test
    void testProductColorsControllerColorCycling() {
        ProductColorsController controller = new ProductColorsController();
        List<ProdutoCor> cores = new ArrayList<>();
        cores.add(new ProdutoCor(1, 10, "AZUL", 10));
        cores.add(new ProdutoCor(2, 10, "VERDE", 5));
        cores.add(new ProdutoCor(3, 10, "VERMELHO", 8));
        cores.add(new ProdutoCor(4, 10, "VINHO", 2));

        long time = 1000L;

        // Pressiona 'v' com seleção inicial vazia (-1) -> VERDE (índice 1)
        int idx1 = controller.calcularProximoIndiceCor(cores, -1, "v", KeyCode.V, time);
        assertEquals(1, idx1, "Primeiro 'v' deve selecionar VERDE na tabela de cores");

        // Pressiona 'v' novamente -> VERMELHO (índice 2)
        time += 300L;
        int idx2 = controller.calcularProximoIndiceCor(cores, idx1, "v", KeyCode.V, time);
        assertEquals(2, idx2, "Segundo 'v' deve ir para VERMELHO");

        // Pressiona 'v' novamente -> VINHO (índice 3)
        time += 300L;
        int idx3 = controller.calcularProximoIndiceCor(cores, idx2, "v", KeyCode.V, time);
        assertEquals(3, idx3, "Terceiro 'v' deve ir para VINHO");

        // Pressiona 'v' novamente -> loop para VERDE (índice 1)
        time += 300L;
        int idx4 = controller.calcularProximoIndiceCor(cores, idx3, "v", KeyCode.V, time);
        assertEquals(1, idx4, "Quarto 'v' deve reiniciar o loop em VERDE");
    }

    @Test
    void testStripAccentsAndSpecialCharacters() {
        assertEquals("agua", ProductDetailController.stripAccents("ÁGUA").toLowerCase());
        assertEquals("ambar", ProductDetailController.stripAccents("ÂMBAR").toLowerCase());
        assertEquals("cinza", ProductDetailController.stripAccents("CINZA").toLowerCase());
    }
}
