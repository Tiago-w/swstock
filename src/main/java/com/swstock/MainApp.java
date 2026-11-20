package com.swstock;

import com.swstock.controller.LoginController;
import com.swstock.database.DatabaseManager;
import com.swstock.util.SessionManager;
import com.swstock.view.SplashScreen;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ponto de entrada da aplicação JavaFX SWStock.
 * Inicializa a interface com tela de pré-carregamento (Splash Screen),
 * seguida por tela sofisticada de inicialização e identificação do operador (Login),
 * e por fim carrega o sistema principal maximizado.
 */
public class MainApp extends Application {

    private static final Logger LOGGER = Logger.getLogger(MainApp.class.getName());

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

        // 1. Exibe imediatamente a Tela de Pré-carregamento (Splash Screen) com a logo central
        Stage splashStage = new Stage();
        SplashScreen.showAndInit(
            splashStage,
            // Callback quando a inicialização estiver pronta
            () -> exibirTelaLogin(primaryStage),
            // Callback se houver erro crítico ao inicializar o banco
            error -> {
                LOGGER.log(Level.SEVERE, "Erro crítico na inicialização do SWStock.", error);
                exibirErroFatal(primaryStage, "Erro ao Inicializar Banco de Dados",
                        "Não foi possível inicializar a base de dados do SWStock:\n\n"
                        + error.getMessage()
                        + "\n\nCausa: " + (error.getCause() != null ? error.getCause().getMessage() : "Desconhecida")
                        + "\n\nVerifique se o usuário possui permissão de gravação.");
            }
        );
    }

    /**
     * 2. Exibe a tela sofisticada de identificação do operador (Login).
     */
    private void exibirTelaLogin(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/swstock/view/LoginView.fxml"));
            Parent root = loader.load();

            LoginController controller = loader.getController();
            Stage loginStage = new Stage();
            applyAppIcon(loginStage);
            loginStage.setTitle("SWStock - Acesso ao Sistema");
            loginStage.setResizable(false);

            Scene scene = new Scene(root, 760, 450);
            loginStage.setScene(scene);
            loginStage.centerOnScreen();

            controller.setLoginStage(loginStage);
            controller.setOnLoginSucesso(() -> abrirJanelaPrincipal(primaryStage));

            // Se o usuário fechar a janela pelo 'X', encerra a aplicação com segurança
            loginStage.setOnCloseRequest(event -> {
                LOGGER.info("Janela de inicialização fechada pelo operador. Encerrando aplicação...");
                Platform.exit();
            });

            loginStage.show();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao abrir tela de login. Abrindo janela principal diretamente.", e);
            abrirJanelaPrincipal(primaryStage);
        }
    }

    /**
     * 3. Abre a janela principal do sistema (MainView) somente após a seleção do operador.
     */
    private void abrirJanelaPrincipal(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/swstock/view/MainView.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root, 1100, 720);

            primaryStage.setMaximized(true);
            primaryStage.setTitle("SWStock");
            primaryStage.setMinWidth(950);
            primaryStage.setMinHeight(600);
            primaryStage.setScene(scene);

            // Garante o fechamento seguro do banco ao clicar no 'X' da janela principal
            primaryStage.setOnCloseRequest(event -> {
                LOGGER.info("Janela principal fechada. Encerrando conexões SQLite...");
                DatabaseManager.getInstance().close();
            });

            primaryStage.show();
            LOGGER.info("SWStock iniciado com sucesso. Operador ativo: " + SessionManager.getInstance().getResponsavelAtual());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Falha crítica ao iniciar interface principal do SWStock.", e);
            exibirErroFatal(primaryStage, "Falha Crítica ao Iniciar SWStock",
                    "Ocorreu um erro ao carregar a interface da aplicação:\n\n" + e.getMessage());
        }
    }

    private void exibirErroFatal(Stage stage, String titulo, String mensagem) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        if (alert.getDialogPane().getScene() != null && alert.getDialogPane().getScene().getWindow() instanceof Stage s) {
            applyAppIcon(s);
        }
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
