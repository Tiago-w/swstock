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

    private Exception initError;

    @Override
    public void init() throws Exception {
        super.init();
        LOGGER.info("Inicializando SWStock e verificando base SQLite...");
        try {
            // Pré-carrega o banco de dados offline
            DatabaseManager.getInstance();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao inicializar base SQLite durante o init()", e);
            initError = e;
        }
    }

    private static javafx.scene.image.Image appIcon = null;

    /**
     * Retorna o ícone oficial da aplicação SWStock (PNG).
     */
    public static javafx.scene.image.Image getAppIcon() {
        if (appIcon == null) {
            try {
                var stream = MainApp.class.getResourceAsStream("/com/swstock/images/app_icon.png");
                if (stream != null) {
                    appIcon = new javafx.scene.image.Image(stream);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Não foi possível carregar o ícone da aplicação.", e);
            }
        }
        return appIcon;
    }

    /**
     * Aplica o ícone oficial da aplicação na janela informada (Stage).
     */
    public static void applyAppIcon(Stage stage) {
        if (stage == null) return;
        javafx.scene.image.Image icon = getAppIcon();
        if (icon != null && stage.getIcons().isEmpty()) {
            stage.getIcons().add(icon);
        }
    }

    @Override
    public void start(Stage primaryStage) {
        applyAppIcon(primaryStage);
        if (initError != null) {
            exibirErroFatal(primaryStage, "Erro ao Inicializar Banco de Dados",
                    "Não foi possível inicializar a base de dados do SWStock:\n\n"
                    + initError.getMessage()
                    + "\n\nCausa: " + (initError.getCause() != null ? initError.getCause().getMessage() : "Desconhecida")
                    + "\n\nVerifique se o usuário possui permissão de gravação.");
            return;
        }

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

            // Exibe o diálogo de login inicial para selecionar o responsável pelas operações
            javafx.application.Platform.runLater(() -> {
                com.swstock.util.SessionManager.getInstance().exibirLoginInicial(primaryStage);
            });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Falha crítica ao iniciar SWStock.", e);
            exibirErroFatal(primaryStage, "Falha Crítica ao Iniciar SWStock",
                    "Ocorreu um erro ao carregar a interface da aplicação:\n\n" + e.getMessage());
        }
    }

    private void exibirErroFatal(Stage stage, String titulo, String mensagem) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle(titulo);
        alert.setHeaderText("O SWStock não pôde ser iniciado");
        alert.setContentText(mensagem);
        alert.showAndWait();
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
