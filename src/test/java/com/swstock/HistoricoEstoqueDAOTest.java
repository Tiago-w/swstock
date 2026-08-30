package com.swstock;

import com.swstock.database.DatabaseManager;
import com.swstock.database.HistoricoEstoqueDAO;
import com.swstock.database.ProdutoDAO;
import com.swstock.model.HistoricoEstoque;
import com.swstock.model.Produto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.File;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HistoricoEstoqueDAOTest {

    private static final String TEST_DB_PATH = "test_historico_swstock.db";
    private static DatabaseManager dbManager;
    private static ProdutoDAO produtoDAO;
    private static HistoricoEstoqueDAO historicoDAO;
    private static int produtoTesteId;

    @BeforeAll
    static void setUp() throws SQLException {
        File dbFile = new File(TEST_DB_PATH);
        if (dbFile.exists()) {
            boolean deleted = dbFile.delete();
            if (!deleted) {
                dbFile.deleteOnExit();
            }
        }
        dbManager = DatabaseManager.getInstance(TEST_DB_PATH);
        produtoDAO = new ProdutoDAO(dbManager);
        historicoDAO = new HistoricoEstoqueDAO(dbManager);

        Produto p = new Produto("Memória RAM 16GB DDR4", 250.0, 280.0, "RAM-001", "Estante B1", 10, "", "Memória gamer");
        Produto inserido = produtoDAO.insert(p);
        assertNotNull(inserido.getId());
        produtoTesteId = inserido.getId();
    }

    @AfterAll
    static void tearDown() {
        if (dbManager != null) {
            dbManager.close();
        }
        File dbFile = new File(TEST_DB_PATH);
        if (dbFile.exists()) {
            boolean deleted = dbFile.delete();
            if (!deleted) {
                dbFile.deleteOnExit();
            }
        }
    }

    @Test
    @Order(1)
    void testAdicaoEstoqueComHistoricoAutomatico() throws SQLException {
        boolean ok = produtoDAO.updateQuantidade(produtoTesteId, 15, "Edição Rápida (+)");
        assertTrue(ok);

        List<HistoricoEstoque> historico = historicoDAO.findByProduto(produtoTesteId);
        assertFalse(historico.isEmpty());

        HistoricoEstoque ultimo = historico.get(0);
        assertEquals("ENTRADA", ultimo.getTipo());
        assertEquals(5, ultimo.getQuantidadeAlterada());
        assertEquals(10, ultimo.getQuantidadeAnterior());
        assertEquals(15, ultimo.getQuantidadeNova());
        assertEquals("Edição Rápida (+)", ultimo.getMotivo());
    }

    @Test
    @Order(2)
    void testSubtracaoEstoqueComHistoricoAutomatico() throws SQLException {
        boolean ok = produtoDAO.updateQuantidade(produtoTesteId, 12, "Edição Rápida (-)");
        assertTrue(ok);

        List<HistoricoEstoque> historico = historicoDAO.findByProduto(produtoTesteId);
        assertEquals(2, historico.size());

        HistoricoEstoque ultimo = historico.get(0);
        assertEquals("SAIDA", ultimo.getTipo());
        assertEquals(-3, ultimo.getQuantidadeAlterada());
        assertEquals(15, ultimo.getQuantidadeAnterior());
        assertEquals(12, ultimo.getQuantidadeNova());
    }

    @Test
    @Order(3)
    void testCalculoTotaisAdicionadosESubtraidos() {
        int totalAdd = historicoDAO.getTotalAdicionado(produtoTesteId);
        int totalSub = historicoDAO.getTotalSubtraido(produtoTesteId);

        assertEquals(5, totalAdd, "Total adicionado deve ser 5.");
        assertEquals(3, totalSub, "Total subtraído deve ser 3.");
    }

    @Test
    @Order(4)
    void testFiltroPorDataECalendario() throws SQLException {
        LocalDate hoje = LocalDate.now();
        List<HistoricoEstoque> registrosHoje = historicoDAO.findByProdutoAndDate(produtoTesteId, hoje);
        assertEquals(2, registrosHoje.size());

        LocalDate dataPassada = hoje.minusDays(10);
        List<HistoricoEstoque> registrosPassado = historicoDAO.findByProdutoAndDate(produtoTesteId, dataPassada);
        assertTrue(registrosPassado.isEmpty());
    }

    @Test
    @Order(5)
    void testMovimentacaoComResponsavelERelatorioSaidas() throws SQLException {
        boolean ok = produtoDAO.updateQuantidade(produtoTesteId, 8, "Retirada Teste", "Denise");
        assertTrue(ok);

        List<HistoricoEstoque> historico = historicoDAO.findByProduto(produtoTesteId);
        HistoricoEstoque ultimo = historico.get(0);
        assertEquals("SAIDA", ultimo.getTipo());
        assertEquals("Denise", ultimo.getResponsavel());
        assertEquals(-4, ultimo.getQuantidadeAlterada());

        List<HistoricoEstoque> saidas = historicoDAO.findSaidasParaRelatorio(LocalDate.now(), LocalDate.now());
        assertFalse(saidas.isEmpty());
        assertTrue(saidas.stream().anyMatch(h -> "Denise".equals(h.getResponsavel())));
    }

    @Test
    @Order(6)
    void testFindAllGlobalComTodosOsTiposEntradaESaida() throws SQLException {
        List<HistoricoEstoque> todos = historicoDAO.findAllGlobal(LocalDate.now(), LocalDate.now(), "Todos os Tipos (Entradas, Saídas e Ajustes)", null);
        assertFalse(todos.isEmpty());
        boolean temEntrada = todos.stream().anyMatch(h -> "ENTRADA".equalsIgnoreCase(h.getTipo()) || (h.getQuantidadeAlterada() != null && h.getQuantidadeAlterada() > 0));
        boolean temSaida = todos.stream().anyMatch(h -> "SAIDA".equalsIgnoreCase(h.getTipo()) || (h.getQuantidadeAlterada() != null && h.getQuantidadeAlterada() < 0));
        assertTrue(temEntrada, "Deve conter entradas no extrato global.");
        assertTrue(temSaida, "Deve conter saídas no extrato global.");

        // Testa também com texto alternativo "Todas as Operações"
        List<HistoricoEstoque> todasOps = historicoDAO.findAllGlobal(null, null, "Todas as Operações", null);
        assertEquals(todos.size(), todasOps.size(), "Ambos os filtros gerais devem trazer todas as movimentações.");
    }

    @Test
    @Order(7)
    void testFiltroPorGrupoEFabricante() throws SQLException {
        List<String> grupos = historicoDAO.getAllGrupos();
        assertNotNull(grupos);

        List<HistoricoEstoque> filtrados = historicoDAO.findAllGlobal(null, null, "Todas as Operações", "GERAL", null);
        assertNotNull(filtrados);
    }
}
