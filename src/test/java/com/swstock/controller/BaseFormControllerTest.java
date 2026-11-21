package com.swstock.controller;

import com.swstock.model.Base;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BaseFormControllerTest {

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
    void testParseValorMonetarioFormatosBrasilEInternacional() {
        assertEquals(85.50, BaseFormController.parseValorMonetario("85,50"), 0.001);
        assertEquals(85.50, BaseFormController.parseValorMonetario("85.50"), 0.001);
        assertEquals(1250.75, BaseFormController.parseValorMonetario("1.250,75"), 0.001);
        assertEquals(42.0, BaseFormController.parseValorMonetario("R$ 42,00"), 0.001);
        assertEquals(0.0, BaseFormController.parseValorMonetario(""), 0.001);
        assertNull(BaseFormController.parseValorMonetario("invalido"));
    }

    @Test
    void testMapeamentoTipoRotulo() {
        assertEquals("Automotiva", BaseFormController.tipoParaRotulo("AUTOMOTIVA"));
        assertEquals("Predial", BaseFormController.tipoParaRotulo("PREDIAL"));
        assertEquals("AUTOMOTIVA", BaseFormController.rotuloParaTipo("Automotiva"));
        assertEquals("PREDIAL", BaseFormController.rotuloParaTipo("Predial"));
    }

    @Test
    void testEdicaoBaseStepperEValidacao() throws Exception {
        Assumptions.assumeTrue(javaFxAvailable, "JavaFX indisponível em ambiente headless");

        AtomicReference<Throwable> erro = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(BaseFormControllerTest.class.getResource("/com/swstock/view/BaseFormModal.fxml"));
                Parent root = loader.load();
                assertNotNull(root);

                BaseFormController ctrl = loader.getController();
                Base base = new Base(7, "900ML", "Base Azul", "AUTOMOTIVA", "Lazzuril", 10, 85.50, null, null);
                ctrl.configurarModoEdicao(base);

                TextField txtTamanho = getField(ctrl, "txtTamanho", TextField.class);
                TextField txtNome = getField(ctrl, "txtNome", TextField.class);
                TextField txtQuantidade = getField(ctrl, "txtQuantidade", TextField.class);
                TextField txtValorCompra = getField(ctrl, "txtValorCompra", TextField.class);
                Label lblDelta = getField(ctrl, "lblDelta", Label.class);
                Label lblErro = getField(ctrl, "lblErroValidacao", Label.class);

                assertEquals("900ML", txtTamanho.getText());
                assertEquals("Base Azul", txtNome.getText());
                assertEquals("10", txtQuantidade.getText());
                assertEquals("Sem alteração", lblDelta.getText());

                // Incrementa 2 vezes
                invokePrivate(ctrl, "handleIncrementar");
                invokePrivate(ctrl, "handleIncrementar");
                assertEquals("12", txtQuantidade.getText());
                assertEquals("+2 un. adicionadas", lblDelta.getText());

                // Validação impede salvar sem nome
                txtNome.setText("   ");
                invokePrivate(ctrl, "handleSalvar");
                assertTrue(lblErro.isVisible());
                assertTrue(ctrl.getResultado().isEmpty());
                assertEquals("Base Azul", base.getNome()); // não mutou original

                // Restaura nome e salva com sucesso
                txtNome.setText("Base Azul Royal");
                txtValorCompra.setText("92,40");
                invokePrivate(ctrl, "handleSalvar");

                Optional<Base> res = ctrl.getResultado();
                assertTrue(res.isPresent());
                assertEquals("Base Azul Royal", res.get().getNome());
                assertEquals(12, res.get().getQuantidade());
                assertEquals(92.40, res.get().getValorCompra(), 0.001);
            } catch (Throwable t) {
                erro.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (erro.get() != null) {
            throw new AssertionError("Falha no teste JavaFX do BaseFormController", erro.get());
        }
    }

    @Test
    void testBasesViewConfiguraRowFactoryParaDuploClique() throws Exception {
        Assumptions.assumeTrue(javaFxAvailable, "JavaFX indisponível em ambiente headless");

        AtomicReference<Throwable> erro = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(BaseFormControllerTest.class.getResource("/com/swstock/view/BasesView.fxml"));
                Parent root = loader.load();
                assertNotNull(root);

                BasesController ctrl = loader.getController();
                TableView<?> tblBases = getField(ctrl, "tblBases", TableView.class);
                assertNotNull(tblBases.getRowFactory(), "tblBases deve configurar rowFactory para duplo clique.");
                assertNotNull(tblBases.getOnKeyPressed(), "tblBases deve suportar atalho Enter para modificar base.");
            } catch (Throwable t) {
                erro.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (erro.get() != null) {
            throw new AssertionError("Falha no teste de rowFactory do BasesController", erro.get());
        }
    }

    private static <T> T getField(Object target, String name, Class<T> type) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return type.cast(f.get(target));
    }

    private static void invokePrivate(Object target, String methodName) throws Exception {
        Method m = target.getClass().getDeclaredMethod(methodName);
        m.setAccessible(true);
        m.invoke(target);
    }
}
