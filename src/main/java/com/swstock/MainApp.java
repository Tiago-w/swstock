package com.swstock;

import com.swstock.database.DatabaseManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ponto de entrada da aplicação JavaFX SWStock.
 * Inicializa a interface gráfica, gerencia o ciclo de vida e garante
 * o fechamento gracioso do banco SQLite no encerramento da janela.
 */
public class MainApp extends Application {

    private static final Logger LOGGER = Logger.getLogger(MainApp.class.getName());

    @Override
    public void init() throws Exception {
        super.init();
        LOGGER.info("Inicializando SWStock e verificando base SQLite...");
        // Pré-carrega o banco de dados offline
        DatabaseManager.getInstance();
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/swstock/view/MainView.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root, 1100, 720);

            primaryStage.setMaximized(true);
            primaryStage.setTitle("SWStock");
            primaryStage.setMinWidth(950);
            primaryStage.setMinHeight(600);
            primaryStage.setScene(scene);

            // Garante o fechamento seguro do banco ao clicar no 'X' da janela
            primaryStage.setOnCloseRequest(event -> {
                LOGGER.info("Janela principal fechada. Encerrando conexões SQLite...");
                DatabaseManager.getInstance().close();
            });

            primaryStage.show();
            LOGGER.info("SWStock iniciado com sucesso.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Falha crítica ao iniciar SWStock.", e);
        }
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        LOGGER.info("Aplicação finalizada. Executando cleanup...");
        DatabaseManager.getInstance().close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
