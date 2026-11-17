package com.swstock.view;

import com.swstock.MainApp;
import com.swstock.database.DatabaseManager;
import com.swstock.database.FuncionarioDAO;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tela de Pré-carregamento (Splash Screen) com a logo da empresa centralizada.
 * Exibida no momento do clique até o carregamento do banco SQLite e dos recursos.
 */
public class SplashScreen {

    private static final Logger LOGGER = Logger.getLogger(SplashScreen.class.getName());

    public static void showAndInit(Stage stage, Runnable onReady, Consumer<Throwable> onError) {
        MainApp.applyAppIcon(stage);
        try {
            stage.initStyle(StageStyle.UNDECORATED);
        } catch (Exception ignored) {
            // Caso já tenha sido estilizado anteriormente
        }

        VBox root = new VBox(14);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(34, 45, 30, 45));
        root.setStyle(
            "-fx-background-color: #FFFFFF; " +
            "-fx-border-color: #CBD5E1; " +
            "-fx-border-width: 1.5px; " +
            "-fx-border-radius: 14px; " +
            "-fx-background-radius: 14px; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.22), 20, 0, 0, 6);"
        );

        // Logo da empresa centralizada
        ImageView logoView = new ImageView();
        try {
            var stream = SplashScreen.class.getResourceAsStream("/com/swstock/images/logo_empresa.png");
            if (stream != null) {
                logoView.setImage(new Image(stream));
                logoView.setFitHeight(95);
                logoView.setPreserveRatio(true);
                logoView.setSmooth(true);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Não foi possível carregar a logo para a splash screen.", e);
        }

        Label lblTitulo = new Label("SWStock");
        lblTitulo.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #0F172A; -fx-letter-spacing: 1px;");

        Label lblSubtitulo = new Label("Sistema de Controle & Gestão de Estoque");
        lblSubtitulo.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");

        ProgressBar progressBar = new ProgressBar();
        progressBar.setPrefWidth(320);
        progressBar.setPrefHeight(6);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.setStyle("-fx-accent: #1565C0;");

        Label lblStatus = new Label("Iniciando componentes do sistema...");
        lblStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");

        Label lblRodape = new Label("Versão 1.0.14 • Carregando base de dados...");
        lblRodape.setStyle("-fx-font-size: 10px; -fx-text-fill: #94A3B8;");

        root.getChildren().addAll(logoView, lblTitulo, lblSubtitulo, progressBar, lblStatus, lblRodape);

        Scene scene = new Scene(root, 480, 340);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();

        // Animação de fade-in suave
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), root);
        fadeIn.setFromValue(0.1);
        fadeIn.setToValue(1.0);
        fadeIn.play();

        // Task de inicialização em background
        Task<Void> initTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                long startTime = System.currentTimeMillis();

                Platform.runLater(() -> lblStatus.setText("Conectando ao banco de dados SQLite..."));
                DatabaseManager.getInstance();

                Platform.runLater(() -> lblStatus.setText("Carregando operadores e configurações..."));
                try {
                    new FuncionarioDAO().findAll();
                } catch (Exception ignored) {}

                Platform.runLater(() -> lblStatus.setText("Pronto!"));

                // Mantém tempo mínimo suficiente para visualização confortável da tela (~950ms)
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed < 950) {
                    Thread.sleep(950 - elapsed);
                }

                return null;
            }
        };

        initTask.setOnSucceeded(e -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(250), root);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(ev -> {
                stage.close();
                if (onReady != null) {
                    onReady.run();
                }
            });
            fadeOut.play();
        });

        initTask.setOnFailed(e -> {
            stage.close();
            Throwable ex = initTask.getException();
            LOGGER.log(Level.SEVERE, "Erro durante a inicialização na splash screen", ex);
            if (onError != null) {
                onError.accept(ex);
            }
        });

        Thread th = new Thread(initTask, "SWStock-SplashInit");
        th.setDaemon(true);
        th.start();
    }
}
