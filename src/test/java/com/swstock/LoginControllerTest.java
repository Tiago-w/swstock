package com.swstock;

import com.swstock.controller.LoginController;
import com.swstock.database.DatabaseManager;
import com.swstock.database.FuncionarioDAO;
import com.swstock.model.Funcionario;
import com.swstock.util.SessionManager;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LoginControllerTest {

    private static final String TEST_DB = "test_login_ctrl.db";
    private static DatabaseManager dbManager;

    @BeforeAll
    static void initFx() {
        try {
            Platform.startup(() -> {});
        } catch (Throwable ignored) {}

        File f = new File(TEST_DB);
        if (f.exists()) f.delete();
        dbManager = DatabaseManager.getInstance(TEST_DB);
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
