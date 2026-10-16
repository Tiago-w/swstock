package com.swstock;

import com.swstock.database.DatabaseManager;
import com.swstock.database.ProdutoDAO;
import com.swstock.database.ProdutoSubcategoriaDAO;
import com.swstock.model.Produto;
import com.swstock.model.ProdutoSubcategoria;
import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ProdutoSubcategoriaDAOTest {

    private DatabaseManager dbManager;
    private ProdutoDAO produtoDAO;
    private ProdutoSubcategoriaDAO subcategoriaDAO;
    private static final String TEST_DB = "test_subcategorias_swstock.db";

    @BeforeAll
    void setUp() throws Exception {
        new File(TEST_DB).delete();
        dbManager = DatabaseManager.getInstance(TEST_DB);
        produtoDAO = new ProdutoDAO(dbManager);
        subcategoriaDAO = new ProdutoSubcategoriaDAO(dbManager);
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
            stmt.execute("DELETE FROM produto_subcategorias;");
            stmt.execute("DELETE FROM produtos;");
            stmt.execute("DELETE FROM historico_estoque;");
        }
    }

    @Test
    void testAdicionarEListarSubcategorias() throws SQLException {
        Produto p = new Produto("Tinta Acrílica Premium", "SHERWIN WILLIAMS", 89.90, 99.90, "SKU-SUB-01", "Estante A1", 0, null, null);
        produtoDAO.upsert(p);
        assertNotNull(p.getId());

        boolean add900ml = subcategoriaDAO.addSubcategoria(p.getId(), "900ml", 12, "Carlos");
        boolean addGal = subcategoriaDAO.addSubcategoria(p.getId(), "3.6L (Galão)", 6, "Carlos");

        assertTrue(add900ml);
        assertTrue(addGal);

        List<ProdutoSubcategoria> subs = subcategoriaDAO.findByProdutoId(p.getId());
        assertEquals(2, subs.size());
        assertEquals("3.6L (GALÃO)", subs.get(0).getNomeSubcategoria());
        assertEquals(6, subs.get(0).getQuantidade());
        assertEquals("900ML", subs.get(1).getNomeSubcategoria());
        assertEquals(12, subs.get(1).getQuantidade());
    }

    @Test
    void testAtualizarQuantidadeSubcategoria() throws SQLException {
        Produto p = new Produto("Verniz Marítimo", "CORAL", 45.0, 50.0, "SKU-SUB-02", "Estante B2", 0, null, null);
        produtoDAO.upsert(p);

        subcategoriaDAO.addSubcategoria(p.getId(), "Brilhante", 10, "Admin");
        List<ProdutoSubcategoria> subs = subcategoriaDAO.findByProdutoId(p.getId());
        assertEquals(1, subs.size());

        ProdutoSubcategoria sub = subs.get(0);
        boolean atualizado = subcategoriaDAO.updateQuantidade(sub.getId(), 18, "Entrada de mercadoria", "Carlos");
        assertTrue(atualizado);

        ProdutoSubcategoria recarregada = subcategoriaDAO.findById(sub.getId());
        assertNotNull(recarregada);
        assertEquals(18, recarregada.getQuantidade());
    }

    @Test
    void testTotalQuantidadeSubcategoriasEHasSubcategorias() throws SQLException {
        Produto p = new Produto("Fita Crepe", "ADELBRAS", 5.5, 6.5, "SKU-SUB-03", "Estante C3", 0, null, null);
        produtoDAO.upsert(p);

        assertFalse(subcategoriaDAO.hasSubcategorias(p.getId()));
        assertEquals(0, subcategoriaDAO.getTotalQuantidadeSubcategorias(p.getId()));

        subcategoriaDAO.addSubcategoria(p.getId(), "18mm x 50m", 20, "Admin");
        subcategoriaDAO.addSubcategoria(p.getId(), "24mm x 50m", 15, "Admin");
        subcategoriaDAO.addSubcategoria(p.getId(), "48mm x 50m", 5, "Admin");

        assertTrue(subcategoriaDAO.hasSubcategorias(p.getId()));
        assertEquals(40, subcategoriaDAO.getTotalQuantidadeSubcategorias(p.getId()));
    }

    @Test
    void testExcluirSubcategoria() throws SQLException {
        Produto p = new Produto("Massa Corrida", "SUVINIL", 30.0, 35.0, "SKU-SUB-04", "Estante D1", 0, null, null);
        produtoDAO.upsert(p);

        subcategoriaDAO.addSubcategoria(p.getId(), "PVA", 8, "Admin");
        List<ProdutoSubcategoria> subs = subcategoriaDAO.findByProdutoId(p.getId());
        assertEquals(1, subs.size());

        boolean deletado = subcategoriaDAO.deleteSubcategoria(subs.get(0).getId(), "Admin");
        assertTrue(deletado);

        List<ProdutoSubcategoria> restantes = subcategoriaDAO.findByProdutoId(p.getId());
        assertTrue(restantes.isEmpty());
        assertFalse(subcategoriaDAO.hasSubcategorias(p.getId()));
    }

    @Test
    void testUnicidadeSubcategoriaPorProduto() throws SQLException {
        Produto p = new Produto("Primer Sintético", "LAZZURIL", 40.0, 48.0, "SKU-SUB-05", "Estante E2", 0, null, null);
        produtoDAO.upsert(p);

        boolean primeiraVez = subcategoriaDAO.addSubcategoria(p.getId(), "Cinza", 5, "Admin");
        assertTrue(primeiraVez);

        assertThrows(SQLException.class, () -> {
            subcategoriaDAO.addSubcategoria(p.getId(), "Cinza", 10, "Admin");
        });
    }

    @Test
    void testFiltroLevantamentoPorSubcategoria() throws SQLException {
        Produto p1 = new Produto("Esmalte Sintético", "SHERWIN WILLIAMS", 42.0, 46.0, "SKU-SUB-10", "Estante F1", 0, null, null);
        p1.setSubcategoria("LINHA IMOBILIÁRIA");
        produtoDAO.upsert(p1);

        Produto p2 = new Produto("Verniz PU", "SHERWIN WILLIAMS", 75.0, 82.0, "SKU-SUB-11", "Estante F2", 0, null, null);
        p2.setSubcategoria("LINHA AUTOMOTIVA");
        produtoDAO.upsert(p2);

        Produto p3 = new Produto("Primer Epóxi", "SHERWIN WILLIAMS", 110.0, 120.0, "SKU-SUB-12", "Estante F3", 0, null, null);
        // Sem subcategoria direta, mas com variação
        produtoDAO.upsert(p3);
        subcategoriaDAO.addSubcategoria(p3.getId(), "LINHA INDUSTRIAL", 10, "Admin");

        // 1. Sem filtro de subcategoria ("Todas as Subcategorias"): não bloqueia
        List<Produto> todos = produtoDAO.findByGrupoAndSubcategoria("SHERWIN WILLIAMS", "Todas as Subcategorias", null);
        assertEquals(3, todos.size());

        // 2. Filtrando por "LINHA AUTOMOTIVA"
        List<Produto> automotiva = produtoDAO.findByGrupoAndSubcategoria("SHERWIN WILLIAMS", "LINHA AUTOMOTIVA", null);
        assertEquals(1, automotiva.size());
        assertEquals("SKU-SUB-11", automotiva.get(0).getCodigoLoja());

        // 3. Filtrando por "LINHA INDUSTRIAL" (presente na tabela de variações)
        List<Produto> industrial = produtoDAO.findByGrupoAndSubcategoria("SHERWIN WILLIAMS", "LINHA INDUSTRIAL", null);
        assertEquals(1, industrial.size());
        assertEquals("SKU-SUB-12", industrial.get(0).getCodigoLoja());

        // 4. Teste de busca de todas as subcategorias para o grupo
        List<String> subcategoriasSW = produtoDAO.getSubcategoriasByGrupo("SHERWIN WILLIAMS");
        assertTrue(subcategoriasSW.contains("LINHA IMOBILIÁRIA"));
        assertTrue(subcategoriasSW.contains("LINHA AUTOMOTIVA"));
        assertTrue(subcategoriasSW.contains("LINHA INDUSTRIAL"));
    }
}
