package com.swstock;

import com.swstock.database.DatabaseManager;
import com.swstock.database.ProdutoDAO;
import com.swstock.model.Produto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProdutoDAOTest {

    private static final String TEST_DB_PATH = "test_swstock.db";
    private static DatabaseManager dbManager;
    private static ProdutoDAO produtoDAO;

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
        produtoDAO = new ProdutoDAO(dbManager);
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
    void testInserirEBuscarPorId() throws SQLException {
        Produto p = new Produto("SSD NVMe 1TB", 350.0, 399.0, "SSD-001", "Estante A1", 10, "", "SSD de alta velocidade");
        Produto inserido = produtoDAO.insert(p);

        assertNotNull(inserido.getId(), "O ID gerado não deve ser nulo.");

        Produto recuperado = produtoDAO.findById(inserido.getId());
        assertNotNull(recuperado, "Produto deve ser encontrado pelo ID.");
        assertEquals("SSD NVMe 1TB", recuperado.getNome());
        assertEquals("SSD-001", recuperado.getCodigoLoja());
        assertEquals(350.0, recuperado.getPrecoVista());
        assertEquals(10, recuperado.getQuantidade());
    }

    @Test
    @Order(2)
    void testUpsertInsercaoEAtualizacao() throws SQLException {
        Produto p1 = new Produto("Monitor 24 Pol", 650.0, 720.0, "MON-001", "Estante B1", 5, "", "Monitor IPS 75Hz");
        Produto res1 = produtoDAO.upsert(p1);
        assertNotNull(res1.getId());

        Produto p2 = new Produto("Monitor 24 Pol IPS Pro", 620.0, 690.0, "MON-001", "Estante B2", 12, "", "Versão atualizada");
        assertNotNull(produtoDAO.upsert(p2));

        Produto atualizado = produtoDAO.findByCodigoLoja("MON-001");
        assertNotNull(atualizado);
        assertEquals("Monitor 24 Pol IPS Pro", atualizado.getNome());
        assertEquals(620.0, atualizado.getPrecoVista());
        assertEquals(5, atualizado.getQuantidade(), "O estoque físico deve ser preservado intacto!");
        assertEquals("Estante B1", atualizado.getLocalizacao(), "A localização da estante deve ser preservada intacta!");
    }

    @Test
    @Order(3)
    void testUpdateQuantidadeRealTime() throws SQLException {
        Produto p = produtoDAO.findByCodigoLoja("SSD-001");
        assertNotNull(p);

        boolean sucesso = produtoDAO.updateQuantidade(p.getId(), 25);
        assertTrue(sucesso, "A atualização de quantidade deve retornar true.");

        Produto atualizado = produtoDAO.findById(p.getId());
        assertNotNull(atualizado);
        assertEquals(25, atualizado.getQuantidade());
    }

    @Test
    @Order(4)
    void testFiltroTermoELocalizacao() throws SQLException {
        List<Produto> filtradosNome = produtoDAO.findByFilter("NVMe", null);
        assertEquals(1, filtradosNome.size());

        List<Produto> filtradosLocal = produtoDAO.findByFilter(null, "Estante B1");
        assertEquals(1, filtradosLocal.size());
        assertEquals("MON-001", filtradosLocal.get(0).getCodigoLoja());
    }

    @Test
    @Order(5)
    void testCalculoOcupacaoMapa2D() throws SQLException {
        Map<String, Integer> contagens = produtoDAO.getLocationCounts();
        assertNotNull(contagens);
        assertTrue(contagens.containsKey("ESTANTE A1"));
        assertEquals(25, contagens.get("ESTANTE A1"));
    }

    @Test
    @Order(6)
    void testDelete() throws SQLException {
        Produto p = produtoDAO.findByCodigoLoja("SSD-001");
        assertNotNull(p);

        boolean deletado = produtoDAO.delete(p.getId());
        assertTrue(deletado);

        Produto busca = produtoDAO.findById(p.getId());
        assertNull(busca, "O produto não deve mais existir após exclusão.");
    }
}
