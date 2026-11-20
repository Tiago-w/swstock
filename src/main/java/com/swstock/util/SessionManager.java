package com.swstock.util;

import com.swstock.database.FuncionarioDAO;
import com.swstock.model.Funcionario;
import javafx.animation.FadeTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gerenciador de Sessão do Usuário / Operador Ativo.
 * Mantém o funcionário selecionado no login inicial e evita
 * a repetição de diálogos a cada alteração no sistema.
 */
public class SessionManager {

    private static final SessionManager INSTANCE = new SessionManager();
    private final StringProperty responsavelAtual = new SimpleStringProperty("Administrador");
    private boolean loginRealizado = false;

    private SessionManager() {}

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    public String getResponsavelAtual() {
        String resp = responsavelAtual.get();
        return (resp != null && !resp.trim().isEmpty()) ? resp.trim() : "Administrador";
    }

    public void setResponsavelAtual(String responsavel) {
        if (responsavel != null && !responsavel.trim().isEmpty()) {
            this.responsavelAtual.set(responsavel.trim());
        }
    }

    public StringProperty responsavelAtualProperty() {
        return responsavelAtual;
    }

    public boolean isLoginRealizado() {
        return loginRealizado;
    }

    public void setLoginRealizado(boolean loginRealizado) {
        this.loginRealizado = loginRealizado;
    }

    /**
     * Exibe o diálogo de login inicial quando a aplicação é aberta.
     */
    public void exibirLoginInicial(Window owner) {
        if (loginRealizado) return;

        List<String> listaCarregada = carregarListaFuncionarios();
        final List<String> nomes = listaCarregada.isEmpty() ? List.of("Administrador", "Padrão") : listaCarregada;

        Dialog<String> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() instanceof Stage s) {
            com.swstock.MainApp.applyAppIcon(s);
        }
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("SWStock - Login do Operador");
        dialog.setHeaderText("Identificação do Responsável");

        ButtonType btnEntrar = new ButtonType("Entrar no Sistema", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnEntrar);

        ComboBox<String> cmb = new ComboBox<>(FXCollections.observableArrayList(nomes));
        cmb.getSelectionModel().selectFirst();
        cmb.setMaxWidth(Double.MAX_VALUE);
        cmb.setStyle("-fx-font-size: 14px; -fx-padding: 6px;");

        Label lblInfo = new Label("Selecione quem está iniciando as operações do estoque:");
        lblInfo.setStyle("-fx-font-size: 13px; -fx-text-fill: #334155; -fx-font-weight: bold;");

        Label lblSub = new Label("Este responsável será registrado automaticamente nas movimentações de estoque.");
        lblSub.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        VBox content = new VBox(12, lblInfo, cmb, lblSub);
        content.setPadding(new Insets(10, 10, 10, 10));
        content.setPrefWidth(420);

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(btn -> {
            if (btn == btnEntrar) {
                String sel = cmb.getValue();
                return (sel != null && !sel.trim().isEmpty()) ? sel.trim() : nomes.get(0);
            }
            return nomes.get(0);
        });

        Optional<String> res = dialog.showAndWait();
        if (res.isPresent()) {
            setResponsavelAtual(res.get());
        } else {
            setResponsavelAtual(nomes.get(0));
        }
        this.loginRealizado = true;
    }

    /**
     * Janela modal elegante para alternar o responsável ativo a qualquer momento.
     */
    public boolean trocarResponsavel(Window owner) {
        List<String> listaCarregada = carregarListaFuncionarios();
        final List<String> nomes = listaCarregada.isEmpty() ? List.of("Administrador", "Padrão") : listaCarregada;
        final String atual = getResponsavelAtual();

        Stage stage = new Stage();
        com.swstock.MainApp.applyAppIcon(stage);
        stage.setTitle("SWStock - Alternar Operador");
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setResizable(false);

        VBox root = new VBox(0);
        root.getStyleClass().add("switch-operator-root");

        // 1. Cabeçalho executivo em azul-marinho
        SVGPath iconSwitch = new SVGPath();
        iconSwitch.setContent("M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z");
        iconSwitch.setFill(Color.WHITE);
        iconSwitch.setScaleX(0.92);
        iconSwitch.setScaleY(0.92);

        StackPane iconBadge = new StackPane(new Group(iconSwitch));
        iconBadge.getStyleClass().add("switch-operator-icon-badge");

        Label lblTitle = new Label("Alternar Operador Ativo");
        lblTitle.getStyleClass().add("switch-operator-title");

        Label lblSubtitle = new Label("Escolha o operador das próximas movimentações.");
        lblSubtitle.getStyleClass().add("switch-operator-subtitle");
        lblSubtitle.setWrapText(true);
        lblSubtitle.setMaxWidth(290);
        lblSubtitle.setMinHeight(Region.USE_PREF_SIZE);

        VBox headerTexts = new VBox(3, lblTitle, lblSubtitle);
        headerTexts.setAlignment(Pos.CENTER_LEFT);

        HBox header = new HBox(14, iconBadge, headerTexts);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("switch-operator-header");

        // 2. Corpo limpo em branco
        VBox body = new VBox(18);
        body.setPadding(new Insets(24, 26, 24, 26));

        // Indicador do operador atual
        Circle statusDot = new Circle(4.0, Color.web("#22C55E"));
        Label lblAtualTag = new Label("Operador atual:");
        lblAtualTag.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #64748B;");
        Label lblAtualVal = new Label(atual);
        lblAtualVal.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        HBox currentBox = new HBox(8, statusDot, lblAtualTag, lblAtualVal);
        currentBox.setAlignment(Pos.CENTER_LEFT);
        currentBox.getStyleClass().add("switch-operator-current-box");

        // Seletor de novo operador
        SVGPath iconPerson = new SVGPath();
        iconPerson.setContent("M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z");
        iconPerson.setFill(Color.web("#64748B"));
        iconPerson.setScaleX(0.58);
        iconPerson.setScaleY(0.58);

        Label lblField = new Label("SELECIONE O NOVO OPERADOR");
        lblField.getStyleClass().add("login-field-label");

        HBox fieldLabelBox = new HBox(6, new Group(iconPerson), lblField);
        fieldLabelBox.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> cmb = new ComboBox<>(FXCollections.observableArrayList(nomes));
        if (nomes.contains(atual)) {
            cmb.getSelectionModel().select(atual);
        } else {
            cmb.getSelectionModel().selectFirst();
        }
        cmb.setMaxWidth(Double.MAX_VALUE);
        cmb.getStyleClass().add("login-combobox");

        VBox selectorBox = new VBox(8, fieldLabelBox, cmb);

        // Botões de ação
        Button btnCancelar = new Button("Cancelar");
        btnCancelar.setCancelButton(true);
        btnCancelar.getStyleClass().add("switch-btn-cancel");

        SVGPath iconCheck = new SVGPath();
        iconCheck.setContent("M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z");
        iconCheck.setFill(Color.WHITE);
        iconCheck.setScaleX(0.72);
        iconCheck.setScaleY(0.72);

        Button btnConfirmar = new Button("Confirmar");
        btnConfirmar.setGraphic(new Group(iconCheck));
        btnConfirmar.setGraphicTextGap(8.0);
        btnConfirmar.setDefaultButton(true);
        btnConfirmar.getStyleClass().add("switch-btn-confirm");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(10, spacer, btnCancelar, btnConfirmar);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(4, 0, 0, 0));

        body.getChildren().addAll(currentBox, selectorBox, actions);
        root.getChildren().addAll(header, body);

        AtomicBoolean confirmado = new AtomicBoolean(false);

        btnCancelar.setOnAction(e -> stage.close());
        btnConfirmar.setOnAction(e -> {
            String val = cmb.getValue();
            String escolhido = (val != null && !val.trim().isEmpty()) ? val.trim() : atual;
            setResponsavelAtual(escolhido);
            confirmado.set(true);
            stage.close();
        });

        Scene scene = new Scene(root, 460, 340);
        var cssUrl = SessionManager.class.getResource("/com/swstock/css/styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        stage.setScene(scene);
        stage.centerOnScreen();

        root.setOpacity(0.0);
        FadeTransition fade = new FadeTransition(Duration.millis(180), root);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.play();

        stage.showAndWait();
        return confirmado.get();
    }

    private List<String> carregarListaFuncionarios() {
        try {
            FuncionarioDAO fdao = new FuncionarioDAO();
            List<Funcionario> lista = fdao.findAll();
            List<String> nomes = new ArrayList<>();
            for (Funcionario f : lista) {
                if (f.getNome() != null && !f.getNome().trim().isEmpty()) {
                    String label = f.getNome().trim();
                    if (f.getNumero() != null && !f.getNumero().trim().isEmpty()) {
                        label += " (" + f.getNumero().trim() + ")";
                    }
                    nomes.add(label);
                }
            }
            if (!nomes.isEmpty()) return nomes;
        } catch (Exception ignored) {}
        return new ArrayList<>(List.of("Administrador", "Padrão"));
    }
}
