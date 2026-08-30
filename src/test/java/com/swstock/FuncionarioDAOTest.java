package com.swstock;

import com.swstock.database.DatabaseManager;
import com.swstock.database.FuncionarioDAO;
import com.swstock.model.Funcionario;
import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FuncionarioDAOTest {

    private static final String TEST_DB_PATH = "test_funcionarios_swstock.db";
    private static DatabaseManager dbManager;
    private static FuncionarioDAO funcionarioDAO;

    @BeforeAll
    static void setUp() {
        File dbFile = new File(TEST_DB_PATH);
        if (dbFile.exists()) {
            dbFile.delete();
        }
        dbManager = DatabaseManager.getInstance(TEST_DB_PATH);
        funcionarioDAO = new FuncionarioDAO(dbManager);
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
    void testNomesPadraoCarregadosNoSeed() throws SQLException {
        List<Funcionario> lista = funcionarioDAO.findAll();
        assertFalse(lista.isEmpty(), "A lista de funcionários padrão não deve ser vazia.");
        assertTrue(lista.size() >= 6, "Deve conter pelo menos os 6 funcionários padrão.");

        List<String> nomes = funcionarioDAO.getNomesFuncionarios();
        assertTrue(nomes.contains("Tiago"));
        assertTrue(nomes.contains("Denise"));
        assertTrue(nomes.contains("Lucas"));
        assertTrue(nomes.contains("Maurício"));
        assertTrue(nomes.contains("Éder"));
        assertTrue(nomes.contains("Gustavo"));
    }

    @Test
    @Order(2)
    void testInserirNovoFuncionario() throws SQLException {
        Funcionario f = new Funcionario("Carlos Eduardo");
        Funcionario inserido = funcionarioDAO.insert(f);

        assertNotNull(inserido.getId(), "O funcionário deve receber um ID gerado.");
        assertEquals("Carlos Eduardo", inserido.getNome());

        List<String> nomes = funcionarioDAO.getNomesFuncionarios();
        assertTrue(nomes.contains("Carlos Eduardo"));
    }

    @Test
    @Order(3)
    void testRemoverFuncionario() throws SQLException {
        Funcionario f = new Funcionario("Temporario");
        Funcionario inserido = funcionarioDAO.insert(f);
        assertNotNull(inserido.getId());

        boolean ok = funcionarioDAO.delete(inserido.getId());
        assertTrue(ok, "Deve retornar true ao excluir funcionário com sucesso.");

        List<String> nomes = funcionarioDAO.getNomesFuncionarios();
        assertFalse(nomes.contains("Temporario"));
    }
}
