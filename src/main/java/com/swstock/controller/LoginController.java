package com.swstock.controller;

import com.swstock.database.FuncionarioDAO;
import com.swstock.model.Funcionario;
import com.swstock.util.LogoManager;
import com.swstock.util.SessionManager;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller da Tela de Inicialização / Seleção de Operador (Login).
 * Apresenta a identidade visual do sistema e a logo da empresa no topo,
 * permitindo selecionar quem vai operar o sistema antes de abrir a janela principal.
 */
public class LoginController {

    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());

    @FXML private HBox rootPane;
    @FXML private ImageView imgLogo;
    @FXML private ComboBox<String> cmbOperadores;
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
        if (rootPane != null) {
            rootPane.setOpacity(0.0);
            FadeTransition fade = new FadeTransition(Duration.millis(220), rootPane);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);
            fade.play();
        }
    }

    private void carregarLogo() {
        if (imgLogo == null) return;
        try {
            Image logo = LogoManager.getInstance().getLogoFxImageOrDefault();
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
    public void handleSair() {
        LOGGER.info("Encerramento solicitado pelo operador na tela de login.");
        if (loginStage != null) {
            loginStage.close();
        }
        Platform.exit();
    }
}
