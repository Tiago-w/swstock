package com.swstock;

import com.swstock.database.ConfiguracaoDAO;
import com.swstock.database.DatabaseManager;
import com.swstock.util.TablePreferencesHelper;
import javafx.application.Platform;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.*;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TablePreferencesHelperTest {

    private static boolean javaFxAvailable = false;
    private DatabaseManager dbManager;
    private ConfiguracaoDAO configDAO;
    private static final String TEST_DB = "test_table_prefs_swstock.db";
    private File tempFile;

    @BeforeAll
    void init() {
        if (isHeadless()) {
            javaFxAvailable = false;
        } else {
            try {
                Platform.startup(() -> {});
                javaFxAvailable = true;
            } catch (IllegalStateException ignored) {
                javaFxAvailable = true;
            } catch (Throwable t) {
                javaFxAvailable = false;
            }
        }

        new File(TEST_DB).delete();
        dbManager = DatabaseManager.getInstance(TEST_DB);
        configDAO = new ConfiguracaoDAO(dbManager);
        tempFile = new File("test_table_prefs.properties");
        tempFile.delete();
    }

    @AfterAll
    void tearDown() {
        if (dbManager != null) {
            dbManager.close();
        }
        new File(TEST_DB).delete();
        if (tempFile != null) {
            tempFile.delete();
        }
    }

    private static boolean isHeadless() {
        if (GraphicsEnvironment.isHeadless()) {
            return true;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("linux")) {
            String display = System.getenv("DISPLAY");
            String wayland = System.getenv("WAYLAND_DISPLAY");
            return (display == null || display.isBlank()) && (wayland == null || wayland.isBlank());
        }
        return false;
    }

    @BeforeEach
    void checkJavaFx() {
        Assumptions.assumeTrue(javaFxAvailable, "JavaFX toolkit não inicializado");
    }

    @Test
    void testSalvarERestaurarOrdemColunasQuantidadeAoLadoDeCodigo() {
        // Tabela original na ordem padrão do FXML
        TableView<String> table = new TableView<>();
        TableColumn<String, String> colCodigo = new TableColumn<>("CÓD. LOJA");
        colCodigo.setId("colCodigoLoja");
        colCodigo.setPrefWidth(105);

        TableColumn<String, String> colNome = new TableColumn<>("NOME DO PRODUTO");
        colNome.setId("colNome");
        colNome.setPrefWidth(300);

        TableColumn<String, String> colQuantidade = new TableColumn<>("QUANTIDADE");
        colQuantidade.setId("colQuantidade");
        colQuantidade.setPrefWidth(115);

        TableColumn<String, String> colAcoes = new TableColumn<>("AÇÕES");
        colAcoes.setId("colAcoes");
        colAcoes.setPrefWidth(135);

        table.getColumns().addAll(colCodigo, colNome, colQuantidade, colAcoes);

        // Usuário arrasta a coluna QUANTIDADE para ser a segunda coluna (ao lado de CÓD. LOJA)
        table.getColumns().setAll(colCodigo, colQuantidade, colNome, colAcoes);
        colQuantidade.setPrefWidth(140); // Usuário também redimensionou a coluna

        // Salva as preferências
        TablePreferencesHelper.salvar(table, "produtos_test", configDAO, tempFile);

        // Verifica que foi salvo no SQLite
        String salvo = configDAO.getValor("table_columns_produtos_test", null);
        assertNotNull(salvo);
        assertTrue(salvo.startsWith("colCodigoLoja:"));
        assertTrue(salvo.contains("colQuantidade:140"));

        // Agora simula fechar e reabrir o programa: cria uma nova TableView com a ordem padrão original
        TableView<String> novaTable = new TableView<>();
        TableColumn<String, String> novaColCodigo = new TableColumn<>("CÓD. LOJA");
        novaColCodigo.setId("colCodigoLoja");
        novaColCodigo.setPrefWidth(105);

        TableColumn<String, String> novaColNome = new TableColumn<>("NOME DO PRODUTO");
        novaColNome.setId("colNome");
        novaColNome.setPrefWidth(300);

        TableColumn<String, String> novaColQuantidade = new TableColumn<>("QUANTIDADE");
        novaColQuantidade.setId("colQuantidade");
        novaColQuantidade.setPrefWidth(115);

        TableColumn<String, String> novaColAcoes = new TableColumn<>("AÇÕES");
        novaColAcoes.setId("colAcoes");
        novaColAcoes.setPrefWidth(135);

        novaTable.getColumns().addAll(novaColCodigo, novaColNome, novaColQuantidade, novaColAcoes);

        // Restaura as preferências
        TablePreferencesHelper.restaurar(novaTable, "produtos_test", configDAO, tempFile, new AtomicBoolean(false));

        // Valida que a ordem restaurada coloca QUANTIDADE logo ao lado de CÓDIGO
        assertEquals(4, novaTable.getColumns().size());
        assertEquals("colCodigoLoja", novaTable.getColumns().get(0).getId());
        assertEquals("colQuantidade", novaTable.getColumns().get(1).getId());
        assertEquals("colNome", novaTable.getColumns().get(2).getId());
        assertEquals("colAcoes", novaTable.getColumns().get(3).getId());

        // Valida que a largura personalizada também foi restaurada
        assertEquals(140.0, novaTable.getColumns().get(1).getPrefWidth());
    }
}
