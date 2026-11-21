package com.swstock.controller;

import com.swstock.database.DatabaseManager;
import com.swstock.database.FuncionarioDAO;
import com.swstock.database.HistoricoEstoqueDAO;
import com.swstock.database.ProdutoDAO;
import com.swstock.model.Funcionario;
import com.swstock.model.HistoricoEstoque;
import com.swstock.model.Produto;
import com.swstock.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ProductDetailQuickOperatorTest {

    private static boolean javaFxAvailable = false;

    @BeforeAll
    static void initJavaFX() {
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

    @Test
    void testBotaoSalvarBloqueadoNoPadraoELiberadoApenasAoSelecionarOperador() throws Exception {
        Assumptions.assumeTrue(javaFxAvailable, "JavaFX indisponível em ambiente headless");

        File tempDb = File.createTempFile("test_quick_op_", ".db");
        tempDb.deleteOnExit();

        DatabaseManager db = DatabaseManager.getInstance(tempDb.getAbsolutePath());
        try {
            FuncionarioDAO funcDAO = new FuncionarioDAO(db);
            funcDAO.insert(new Funcionario("Lucas", "10"));
            funcDAO.insert(new Funcionario("Maria", "20"));

            ProdutoDAO prodDAO = new ProdutoDAO(db);
            Produto produto = new Produto(null, "Tinta Teste", "GERAL", 50.0, 60.0, "SKU-TEST-99", "Estante A1", 10, "", "");
            prodDAO.upsert(produto);

            // Mesmo com responsável ativo na sessão, o modal deve abrir TRAVADO (default sem seleção)
            SessionManager.getInstance().setResponsavelAtual("Administrador (01)");

            AtomicReference<Throwable> erro = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            Platform.runLater(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(ProductDetailQuickOperatorTest.class.getResource("/com/swstock/view/ProductDetailModal.fxml"));
                    Parent root = loader.load();
                    assertNotNull(root);

                    ProductDetailController ctrl = loader.getController();
                    ctrl.setProdutoDAO(prodDAO);
                    ctrl.setProduto(produto);

                    Button btnSalvar = getField(ctrl, "btnSalvar", Button.class);
                    FlowPane flow = getField(ctrl, "flowOperadoresRapidos", FlowPane.class);

                    // 1. Estado default: nenhum botão selecionado e Salvar BLOQUEADO
                    assertNull(ctrl.getResponsavelEdicaoSelecionado(), "Não deve haver operador pré-selecionado ao abrir.");
                    assertTrue(btnSalvar.isDisabled(), "Botão Salvar deve iniciar bloqueado enquanto estiver no estado padrão.");
                    assertTrue(flow.getChildren().size() >= 2, "Deve renderizar botões rápidos para os operadores cadastrados.");

                    // 2. Um clique no botão rápido de 'Lucas (10)' desbloqueia imediatamente o Salvar
                    ToggleButton chipLucas = flow.getChildren().stream()
                            .filter(ToggleButton.class::isInstance)
                            .map(ToggleButton.class::cast)
                            .filter(tb -> "Lucas (10)".equals(tb.getText()))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("Chip rápido 'Lucas (10)' não encontrado."));

                    chipLucas.fire();
                    assertEquals("Lucas (10)", ctrl.getResponsavelEdicaoSelecionado());
                    assertFalse(btnSalvar.isDisabled(), "Botão Salvar deve ser liberado após selecionar quem editou.");

                    // 3. Desmarcar o chip volta ao estado padrão e bloqueia Salvar novamente
                    chipLucas.fire();
                    assertNull(ctrl.getResponsavelEdicaoSelecionado());
                    assertTrue(btnSalvar.isDisabled(), "Botão Salvar deve voltar a ficar bloqueado se desmarcar o operador.");

                    // 4. Seleciona 'Maria (20)' e confirma liberação
                    ctrl.selecionarOperadorRapido("Maria (20)");
                    assertEquals("Maria (20)", ctrl.getResponsavelEdicaoSelecionado());
                    assertFalse(btnSalvar.isDisabled());

                    // 5. Reabrir outro item reseta para padrão bloqueado
                    ctrl.setProduto(produto);
                    assertNull(ctrl.getResponsavelEdicaoSelecionado());
                    assertTrue(btnSalvar.isDisabled());
                } catch (Throwable t) {
                    erro.set(t);
                } finally {
                    latch.countDown();
                }
            });

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            if (erro.get() != null) {
                throw new AssertionError("Falha no teste de seletor rápido de operador", erro.get());
            }

            // Garante que nenhuma alteração indevida foi gravada no histórico enquanto bloqueado
            HistoricoEstoqueDAO histDAO = new HistoricoEstoqueDAO(db);
            List<HistoricoEstoque> hist = histDAO.findByProduto(produto.getId());
            assertTrue(hist.isEmpty(), "Nenhuma movimentação deve ter sido salva antes da confirmação.");
        } finally {
            db.close();
        }
    }

    private static <T> T getField(Object target, String name, Class<T> type) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return type.cast(f.get(target));
    }
}
