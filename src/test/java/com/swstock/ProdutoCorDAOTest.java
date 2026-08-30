package com.swstock;

import com.swstock.database.DatabaseManager;
import com.swstock.database.ProdutoCorDAO;
import com.swstock.database.ProdutoDAO;
import com.swstock.model.Produto;
import com.swstock.model.ProdutoCor;
import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.SQLException;
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
        Produto p = new Produto("Spray Cores Lisas", "09 SPRAY", 24.94, 26.25, "SKU-90001", "Estante A1", 0, null, null);
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
        Produto p = new Produto("Tinta Automotiva", "10 WANDA", 50.0, 55.0, "SKU-90002", "Estante B1", 0, null, null);
        produtoDAO.upsert(p);

        produtoCorDAO.addCor(p.getId(), "Preto Fosco", 8);
        List<ProdutoCor> cores = produtoCorDAO.findByProdutoId(p.getId());
        assertEquals(1, cores.size());

        ProdutoCor cor = cores.get(0);
        boolean atualizado = produtoCorDAO.updateQuantidade(cor.getId(), 15, "Ajuste de Inventário", "Tiago");
        assertTrue(atualizado);

        ProdutoCor recarregada = produtoCorDAO.findById(cor.getId());
        assertEquals(15, recarregada.getQuantidade());
    }

    @Test
    void testTotalQuantidadeCores() throws SQLException {
        Produto p = new Produto("Esmalte Sintético", "04 KILLING", 30.0, 35.0, "SKU-90003", "Estante C1", 0, null, null);
        produtoDAO.upsert(p);

        produtoCorDAO.addCor(p.getId(), "Branco", 12);
        produtoCorDAO.addCor(p.getId(), "Amarelo", 8);
        produtoCorDAO.addCor(p.getId(), "Verde", 4);

        int total = produtoCorDAO.getTotalQuantidadeCores(p.getId());
        assertEquals(24, total);
    }

    @Test
    void testExcluirCor() throws SQLException {
        Produto p = new Produto("Verniz", "27 NAUTICA", 40.0, 45.0, "SKU-90004", "Estante D1", 0, null, null);
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
        Produto p = new Produto("Spray Teste", "09 SPRAY", 20.0, 22.0, "SKU-99001", "Estante A1", 0, null, null);
        produtoDAO.upsert(p);

        // 1. Criar cor sem estoque
        produtoCorDAO.addCor(p.getId(), "Preto", 0, "Tiago");
        // 2. Criar cor com estoque
        produtoCorDAO.addCor(p.getId(), "Azul", 10, "Maurício");

        List<ProdutoCor> cores = produtoCorDAO.findByProdutoId(p.getId());
        ProdutoCor corAzul = cores.stream().filter(c -> c.getNomeCor().equals("AZUL")).findFirst().orElseThrow();

        // 3. Atualizar estoque da cor
        produtoCorDAO.updateQuantidade(corAzul.getId(), 15, null, "Lucas");

        // 4. Excluir cor
        produtoCorDAO.deleteCor(corAzul.getId(), "Denise");

        var historicoDAO = new com.swstock.database.HistoricoEstoqueDAO(dbManager);
        var hist = historicoDAO.findByProduto(p.getId());

        assertEquals(4, hist.size());
        // Ordem cronológica decrescente:
        assertEquals("Exclusão de cor [AZUL]", hist.get(0).getMotivo());
        assertEquals("Modificação de estoque [AZUL]", hist.get(1).getMotivo());
        assertEquals("Cadastro de cor [AZUL] / Modificação de estoque", hist.get(2).getMotivo());
        assertEquals("Cadastro de cor [PRETO]", hist.get(3).getMotivo());
    }
}
