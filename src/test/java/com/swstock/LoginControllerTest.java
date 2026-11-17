package com.swstock;

import com.swstock.controller.LoginController;
import com.swstock.database.DatabaseManager;
import com.swstock.database.FuncionarioDAO;
import com.swstock.model.Funcionario;
import com.swstock.util.SessionManager;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LoginControllerTest {

    private static final String TEST_DB = "test_login_ctrl.db";
    private static DatabaseManager dbManager;
    private static boolean javaFxAvailable = false;

    @BeforeAll
    static void initFx() {
        if (isHeadless()) {
            javaFxAvailable = false;
            return;
        }

        try {
            Platform.startup(() -> {});
            javaFxAvailable = true;
        } catch (IllegalStateException ignored) {
            javaFxAvailable = true;
        } catch (Throwable t) {
            javaFxAvailable = false;
        }

        File f = new File(TEST_DB);
        if (f.exists()) f.delete();
        dbManager = DatabaseManager.getInstance(TEST_DB);
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
        Assumptions.assumeTrue(javaFxAvailable, "JavaFX toolkit não inicializado neste ambiente headless");
    }

    @AfterAll
    static void cleanup() {
        if (dbManager != null) {
            dbManager.close();
        }
        File f = new File(TEST_DB);
        if (f.exists()) f.delete();
    }

    @Test
    void testCarregarOperadoresESelecionar() throws Exception {
        FuncionarioDAO dao = new FuncionarioDAO(dbManager);
        dao.insert(new Funcionario("Operador Teste", "099"));

        LoginController controller = new LoginController();
        controller.setFuncionarioDAO(dao);

        ComboBox<String> cmb = new ComboBox<>();
        Field cmbField = LoginController.class.getDeclaredField("cmbOperadores");
        cmbField.setAccessible(true);
        cmbField.set(controller, cmb);

        controller.carregarOperadores();

        assertTrue(cmb.getItems().size() >= 1, "Lista de operadores no combo não deve ser vazia.");
        boolean encontrou = cmb.getItems().stream().anyMatch(n -> n.contains("Operador Teste"));
        assertTrue(encontrou, "Operador salvo deve estar presente no combo.");

        cmb.getSelectionModel().select("Operador Teste (099)");

        AtomicBoolean loginSucesso = new AtomicBoolean(false);
        controller.setOnLoginSucesso(() -> loginSucesso.set(true));

        controller.handleAcessarSistema();

        assertEquals("Operador Teste (099)", SessionManager.getInstance().getResponsavelAtual());
        assertTrue(SessionManager.getInstance().isLoginRealizado());
        assertTrue(loginSucesso.get(), "O callback de sucesso deve ser acionado.");
    }
}
