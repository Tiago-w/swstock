package com.swstock.util;

import com.swstock.database.FuncionarioDAO;
import com.swstock.model.Funcionario;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
     * Diálogo para alternar o responsável ativo a qualquer momento.
     */
    public boolean trocarResponsavel(Window owner) {
        List<String> listaCarregada = carregarListaFuncionarios();
        final List<String> nomes = listaCarregada.isEmpty() ? List.of("Administrador", "Padrão") : listaCarregada;

        Dialog<String> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle("Trocar Responsável");
        dialog.setHeaderText("Alternar Operador Ativo");

        ButtonType btnConfirmar = new ButtonType("Confirmar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnConfirmar, ButtonType.CANCEL);

        ComboBox<String> cmb = new ComboBox<>(FXCollections.observableArrayList(nomes));
        String atual = getResponsavelAtual();
        if (nomes.contains(atual)) {
            cmb.getSelectionModel().select(atual);
        } else {
            cmb.getSelectionModel().selectFirst();
        }
        cmb.setMaxWidth(Double.MAX_VALUE);
        cmb.setStyle("-fx-font-size: 13px; -fx-padding: 4px;");

        VBox content = new VBox(10);
        content.getChildren().addAll(
                new Label("Selecione o novo funcionário responsável pelas operações:"),
                cmb
        );
        content.setPrefWidth(380);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(btn -> {
            if (btn == btnConfirmar) {
                String val = cmb.getValue();
                return (val != null && !val.trim().isEmpty()) ? val.trim() : atual;
            }
            return null;
        });

        Optional<String> res = dialog.showAndWait();
        if (res.isPresent()) {
            setResponsavelAtual(res.get());
            return true;
        }
        return false;
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
