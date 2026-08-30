package com.swstock;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class FxmlLoadTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized
        }
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
