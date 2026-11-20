package com.swstock.controller;

import com.swstock.MainApp;
import com.swstock.database.FuncionarioDAO;
import com.swstock.model.Funcionario;
import com.swstock.util.SessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller da Tela Sofisticada de Inicialização / Seleção de Operador (Login).
 * Apresenta a identidade visual da empresa no topo e permite selecionar quem
 * vai operar o sistema antes de abrir a janela principal.
 */
public class LoginController {

    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());

    @FXML private VBox rootPane;
    @FXML private ImageView imgLogo;
    @FXML private ComboBox<String> cmbOperadores;
    @FXML private Button btnGerenciarFuncionarios;
    @FXML private Button btnAcessar;
    @FXML private Button btnSair;

    private Stage loginStage;
    private Runnable onLoginSucesso;
    private FuncionarioDAO funcionarioDAO = new FuncionarioDAO();

    public void setLoginStage(Stage loginStage) {
        this.loginStage = loginStage;
    }

    public void setOnLoginSucesso(Runnable onLoginSucesso) {
        this.onLoginSucesso = onLoginSucesso;
    }

    public void setFuncionarioDAO(FuncionarioDAO funcionarioDAO) {
        this.funcionarioDAO = funcionarioDAO;
    }

    public ComboBox<String> getCmbOperadores() {
        return cmbOperadores;
    }

    @FXML
    public void initialize() {
        carregarLogo();
        carregarOperadores();
    }

    private void carregarLogo() {
        if (imgLogo == null) return;
        try {
            javafx.scene.image.Image logo = com.swstock.util.LogoManager.getInstance().getLogoFxImageOrDefault();
            if (logo != null && !logo.isError()) {
                imgLogo.setImage(logo);
                imgLogo.setVisible(true);
                imgLogo.setManaged(true);
            } else {
                imgLogo.setVisible(false);
                imgLogo.setManaged(false);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Não foi possível carregar a logo na tela de login.", e);
        }
    }

    /**
     * Carrega a lista atualizada de operadores cadastrados no banco SQLite.
     */
    public void carregarOperadores() {
        List<String> nomes = new ArrayList<>();
        try {
            if (funcionarioDAO != null) {
                List<Funcionario> lista = funcionarioDAO.findAll();
                for (Funcionario f : lista) {
                    if (f.getNome() != null && !f.getNome().trim().isEmpty()) {
                        String label = f.getNome().trim();
                        if (f.getNumero() != null && !f.getNumero().trim().isEmpty()) {
                            label += " (" + f.getNumero().trim() + ")";
                        }
                        nomes.add(label);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao consultar funcionários no banco para login", e);
        }

        if (nomes.isEmpty()) {
            nomes.add("Administrador");
            nomes.add("Padrão");
        }

        String selecionadoAnterior = cmbOperadores != null ? cmbOperadores.getValue() : null;
        if (cmbOperadores != null) {
            cmbOperadores.setItems(FXCollections.observableArrayList(nomes));

            if (selecionadoAnterior != null && nomes.contains(selecionadoAnterior)) {
                cmbOperadores.getSelectionModel().select(selecionadoAnterior);
            } else {
                String respSessao = SessionManager.getInstance().getResponsavelAtual();
                if (respSessao != null && nomes.contains(respSessao)) {
                    cmbOperadores.getSelectionModel().select(respSessao);
                } else {
                    cmbOperadores.getSelectionModel().selectFirst();
                }
            }
        }
    }

    @FXML
    public void handleAcessarSistema() {
        String operador = (cmbOperadores != null) ? cmbOperadores.getValue() : null;
        if (operador == null || operador.trim().isEmpty()) {
            if (cmbOperadores != null && !cmbOperadores.getItems().isEmpty()) {
                operador = cmbOperadores.getItems().get(0);
            } else {
                operador = "Administrador";
            }
        }
        operador = operador.trim();

        LOGGER.info("Operador selecionado para a sessão: " + operador);
        SessionManager.getInstance().setResponsavelAtual(operador);
        SessionManager.getInstance().setLoginRealizado(true);

        if (loginStage != null) {
            loginStage.close();
        }

        if (onLoginSucesso != null) {
            onLoginSucesso.run();
        }
    }

    @FXML
    public void handleGerenciarFuncionarios() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/swstock/view/FuncionariosView.fxml"));
            Parent root = loader.load();

            FuncionariosController controller = loader.getController();
            Stage stage = new Stage();
            MainApp.applyAppIcon(stage);
            stage.setTitle("SWStock - Gestão de Operadores / Funcionários");
            stage.initModality(Modality.APPLICATION_MODAL);
            if (loginStage != null) {
                stage.initOwner(loginStage);
            }
            stage.setScene(new Scene(root));

            controller.setDialogStage(stage);
            controller.setFuncionarioDAO(this.funcionarioDAO != null ? this.funcionarioDAO : new FuncionarioDAO());

            stage.showAndWait();

            // Ao fechar a tela de funcionários, recarrega a lista para refletir inclusões/alterações
            carregarOperadores();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao abrir tela de gerenciamento de funcionários.", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            if (alert.getDialogPane().getScene() != null && alert.getDialogPane().getScene().getWindow() instanceof Stage s) {
                MainApp.applyAppIcon(s);
            }
            alert.setTitle("Erro");
            alert.setHeaderText("Não foi possível abrir o módulo de funcionários");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    public void handleSair() {
        LOGGER.info("Encerramento solicitado pelo operador na tela de login.");
        if (loginStage != null) {
            loginStage.close();
        }
        Platform.exit();
    }
}
