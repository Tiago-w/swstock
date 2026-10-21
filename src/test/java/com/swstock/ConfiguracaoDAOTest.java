package com.swstock;

import com.swstock.database.ConfiguracaoDAO;
import com.swstock.database.DatabaseManager;
import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ConfiguracaoDAOTest {

    private DatabaseManager dbManager;
    private ConfiguracaoDAO configuracaoDAO;
    private static final String TEST_DB = "test_config_swstock.db";

    @BeforeAll
    void setUp() {
        new File(TEST_DB).delete();
        dbManager = DatabaseManager.getInstance(TEST_DB);
        configuracaoDAO = new ConfiguracaoDAO(dbManager);
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
            stmt.execute("DELETE FROM configuracoes;");
        }
    }

    @Test
    void testSalvarERecuperarConfiguracao() {
        String defaultVal = configuracaoDAO.getValor("chave_inexistente", "PADRAO");
        assertEquals("PADRAO", defaultVal);

        configuracaoDAO.setValor("table_columns_main_produtos", "colCodigoLoja:100;colQuantidade:120;colNome:300");
        String recuperado = configuracaoDAO.getValor("table_columns_main_produtos", null);
        assertNotNull(recuperado);
        assertEquals("colCodigoLoja:100;colQuantidade:120;colNome:300", recuperado);
    }

    @Test
    void testAtualizarConfiguracaoExistente() {
        configuracaoDAO.setValor("pref_tema", "claro");
        assertEquals("claro", configuracaoDAO.getValor("pref_tema", null));

        configuracaoDAO.setValor("pref_tema", "escuro");
        assertEquals("escuro", configuracaoDAO.getValor("pref_tema", null));
    }
}
