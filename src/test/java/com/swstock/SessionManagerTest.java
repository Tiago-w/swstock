package com.swstock;

import com.swstock.util.DialogHelper;
import com.swstock.util.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class SessionManagerTest {

    @BeforeEach
    void setUp() {
        SessionManager.getInstance().setResponsavelAtual("Administrador");
    }

    @Test
    void testDefaultResponsavel() {
        assertNotNull(SessionManager.getInstance().getResponsavelAtual());
        assertFalse(SessionManager.getInstance().getResponsavelAtual().trim().isEmpty());
    }

    @Test
    void testSetAndGetResponsavel() {
        SessionManager.getInstance().setResponsavelAtual("Carlos Silva (102)");
        assertEquals("Carlos Silva (102)", SessionManager.getInstance().getResponsavelAtual());
    }

    @Test
    void testResponsavelReactiveProperty() {
        AtomicBoolean listenerTriggered = new AtomicBoolean(false);
        SessionManager.getInstance().responsavelAtualProperty().addListener((obs, oldVal, newVal) -> {
            if ("Mariana Souza".equals(newVal)) {
                listenerTriggered.set(true);
            }
        });

        SessionManager.getInstance().setResponsavelAtual("Mariana Souza");
        assertTrue(listenerTriggered.get(), "O listener de propriedade reativa deve ser notificado na troca de operador");
    }

    @Test
    void testDialogHelperUsesSessionResponsavelDirectly() {
        SessionManager.getInstance().setResponsavelAtual("Roberto Mendes (005)");

        // DialogHelper.solicitarResponsavel agora não abre popup e retorna o responsável ativo da sessão
        java.util.Optional<String> responsavel = DialogHelper.solicitarResponsavel("Ajuste manual de estoque");
        assertTrue(responsavel.isPresent());
        assertEquals("Roberto Mendes (005)", responsavel.get());
    }
}
