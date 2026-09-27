package com.swstock;

import com.swstock.database.BaseDAO;
import com.swstock.database.DatabaseManager;
import com.swstock.database.HistoricoBaseDAO;
import com.swstock.model.Base;
import com.swstock.model.HistoricoBase;
import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BaseDAOTest {

    private static final String TEST_DB_PATH = "test_bases_swstock.db";
    private static DatabaseManager dbManager;
    private static BaseDAO baseDAO;
    private static HistoricoBaseDAO historicoBaseDAO;

    @BeforeAll
    static void setUpAll() {
        File dbFile = new File(TEST_DB_PATH);
        if (dbFile.exists()) {
            boolean deleted = dbFile.delete();
            if (!deleted) {
                dbFile.deleteOnExit();
            }
        }
        dbManager = DatabaseManager.getInstance(TEST_DB_PATH);
        baseDAO = new BaseDAO(dbManager);
        historicoBaseDAO = new HistoricoBaseDAO(dbManager);
    }

    @AfterAll
    static void tearDownAll() {
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
    void testInserirEBuscarBase() throws SQLException {
        Base base = new Base("BASE-AUTO-01", "Base Poliéster Azul Royal", "AUTOMOTIVA", "Lazzuril", 15, 110.00);
        Base inserida = baseDAO.insert(base, "Operador Teste");

        assertNotNull(inserida.getId());
        assertEquals("BASE-AUTO-01", inserida.getCodigo());
        assertEquals(15, inserida.getQuantidade());
        assertEquals(110.00, inserida.getValorCompra());

        Base encontrada = baseDAO.findById(inserida.getId());
        assertNotNull(encontrada);
        assertEquals("Base Poliéster Azul Royal", encontrada.getNome());
        assertEquals("AUTOMOTIVA", encontrada.getTipo());
        assertEquals("Lazzuril", encontrada.getFabricante());

        Base porCodigo = baseDAO.findByCodigo("base-auto-01");
        assertNotNull(porCodigo);
        assertEquals(inserida.getId(), porCodigo.getId());

        // Verifica que o histórico isolado registrou o lote inicial
        List<HistoricoBase> hist = historicoBaseDAO.findByBase(inserida.getId());
        assertEquals(1, hist.size());
        assertEquals("ENTRADA", hist.get(0).getTipo());
        assertEquals(15, hist.get(0).getQuantidadeAlterada());
        assertEquals("Operador Teste", hist.get(0).getResponsavel());
    }

    @Test
    @Order(2)
    void testInserirBasePredial() throws SQLException {
        Base basePredial = new Base("BASE-PRED-01", "Base Acrílica Fosca Branco Neve", "PREDIAL", "Suvinil", 25, 75.00);
        Base inserida = baseDAO.insert(basePredial, "Supervisor Obra");

        assertNotNull(inserida.getId());
        assertEquals("PREDIAL", inserida.getTipo());

        List<Base> prediais = baseDAO.findByTipo("PREDIAL");
        assertEquals(1, prediais.size());
        assertEquals("BASE-PRED-01", prediais.get(0).getCodigo());

        List<Base> automotivas = baseDAO.findByTipo("AUTOMOTIVA");
        assertEquals(1, automotivas.size());
        assertEquals("BASE-AUTO-01", automotivas.get(0).getCodigo());
    }

    @Test
    @Order(3)
    void testAtualizarEstoqueGeraHistorico() throws SQLException {
        Base base = baseDAO.findByCodigo("BASE-AUTO-01");
        assertNotNull(base);
        assertEquals(15, base.getQuantidade());

        // Vendeu 5 unidades (novo estoque = 10)
        base.setQuantidade(10);
        boolean ok = baseDAO.update(base, "Vendedor João", "Venda de tinta personalizada balcão");
        assertTrue(ok);

        Base atualizada = baseDAO.findById(base.getId());
        assertEquals(10, atualizada.getQuantidade());

        List<HistoricoBase> hist = historicoBaseDAO.findByBase(base.getId());
        assertEquals(2, hist.size());

        HistoricoBase ultimoMov = hist.get(0); // Ordenado DESC
        assertEquals("SAIDA", ultimoMov.getTipo());
        assertEquals(-5, ultimoMov.getQuantidadeAlterada());
        assertEquals(15, ultimoMov.getQuantidadeAnterior());
        assertEquals(10, ultimoMov.getQuantidadeNova());
        assertEquals("Vendedor João", ultimoMov.getResponsavel());
        assertEquals("Venda de tinta personalizada balcão", ultimoMov.getMotivo());
    }

    @Test
    @Order(4)
    void testAjustarEstoqueDireto() throws SQLException {
        Base base = baseDAO.findByCodigo("BASE-PRED-01");
        assertNotNull(base);
        assertEquals(25, base.getQuantidade());

        // Entrada de 10 unidades
        boolean ok = baseDAO.ajustarEstoque(base.getId(), 10, "ENTRADA", "Reposição de Estoque", "Almoxarife");
        assertTrue(ok);

        Base posEntrada = baseDAO.findById(base.getId());
        assertEquals(35, posEntrada.getQuantidade());

        // Totais calculados
        int totalAdd = historicoBaseDAO.getTotalAdicionado("PREDIAL");
        assertTrue(totalAdd >= 35); // 25 inicial + 10 reposição
    }

    @Test
    @Order(5)
    void testExclusaoGeraHistoricoDeSaida() throws SQLException {
        Base base = new Base("BASE-TEMP", "Base Para Teste Exclusão", "AUTOMOTIVA", "Geral", 8, 40.0);
        base = baseDAO.insert(base, "Admin");

        boolean deleted = baseDAO.delete(base.getId(), "Admin");
        assertTrue(deleted);

        Base buscada = baseDAO.findById(base.getId());
        assertNull(buscada);
    }
}
