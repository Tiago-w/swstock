package com.swstock;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class FxmlLoadTest {

    private static boolean javaFxAvailable = false;

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
            javaFxAvailable = true;
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized
            javaFxAvailable = true;
        } catch (Throwable t) {
            // Em ambiente headless (sem display X11/Wayland), marcar como indisponível
            javaFxAvailable = false;
        }
    }

    @BeforeEach
    void checkJavaFx() {
        Assumptions.assumeTrue(javaFxAvailable, "JavaFX toolkit não inicializado neste ambiente headless");
    }

    @Test
    void testLoadProductDetailModalFxml() throws Exception {
        FXMLLoader loader = new FXMLLoader(FxmlLoadTest.class.getResource("/com/swstock/view/ProductDetailModal.fxml"));
        Object root = loader.load();
        assertNotNull(root);
    }

    @Test
    void testLoadGlobalStockHistoryFxml() throws Exception {
        FXMLLoader loader = new FXMLLoader(FxmlLoadTest.class.getResource("/com/swstock/view/GlobalStockHistoryView.fxml"));
        Object root = loader.load();
        assertNotNull(root);
    }

    @Test
    void testLoadMainViewFxml() throws Exception {
        FXMLLoader loader = new FXMLLoader(FxmlLoadTest.class.getResource("/com/swstock/view/MainView.fxml"));
        Object root = loader.load();
        assertNotNull(root);
    }

    @Test
    void testLoadMap2DViewFxml() throws Exception {
        FXMLLoader loader = new FXMLLoader(FxmlLoadTest.class.getResource("/com/swstock/view/Map2DView.fxml"));
        Object root = loader.load();
        assertNotNull(root);
    }

    @Test
    void testLoadFuncionariosViewFxml() throws Exception {
        FXMLLoader loader = new FXMLLoader(FxmlLoadTest.class.getResource("/com/swstock/view/FuncionariosView.fxml"));
        Object root = loader.load();
        assertNotNull(root);
    }

    @Test
    void testLoadProductColorsModalFxml() throws Exception {
        FXMLLoader loader = new FXMLLoader(FxmlLoadTest.class.getResource("/com/swstock/view/ProductColorsModal.fxml"));
        Object root = loader.load();
        assertNotNull(root);
    }

    @Test
    void testLoadStockInventoryModalFxml() throws Exception {
        FXMLLoader loader = new FXMLLoader(FxmlLoadTest.class.getResource("/com/swstock/view/StockInventoryModal.fxml"));
        Object root = loader.load();
        assertNotNull(root);
    }
}
