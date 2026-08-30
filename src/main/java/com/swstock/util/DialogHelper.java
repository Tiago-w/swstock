package com.swstock.util;

import com.swstock.database.FuncionarioDAO;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Utilitário central para diálogos e confirmação de responsável em operações de salvamento e alteração.
 */
public class DialogHelper {

    /**
     * Exibe um modal solicitando a identificação do funcionário responsável pela alteração.
     * Retorna Optional vazio se o usuário cancelar, impedindo o salvamento.
     */
    public static Optional<String> solicitarResponsavel(Window owner, String titulo, String header) {
        Dialog<String> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(titulo != null ? titulo : "Identificação do Responsável");
        dialog.setHeaderText(header != null ? header : "Informe o funcionário responsável por esta alteração:");

        ButtonType btnConfirmar = new ButtonType("Confirmar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnConfirmar, ButtonType.CANCEL);

        List<String> funcionarios = new ArrayList<>();
        try {
            FuncionarioDAO fdao = new FuncionarioDAO();
            funcionarios = fdao.getNomesFuncionarios();
        } catch (Exception ignored) {}

        if (funcionarios.isEmpty()) {
            funcionarios = List.of("Tiago", "Denise", "Lucas", "Maurício", "Éder", "Gustavo");
        }

        ComboBox<String> cmbFuncionarios = new ComboBox<>(FXCollections.observableArrayList(funcionarios));
        cmbFuncionarios.setEditable(true);
        cmbFuncionarios.getSelectionModel().selectFirst();
        cmbFuncionarios.setMaxWidth(Double.MAX_VALUE);
        cmbFuncionarios.setStyle("-fx-font-size: 13px; -fx-padding: 4px;");

        VBox content = new VBox(10);
        content.getChildren().addAll(
                new Label("Selecione ou digite o nome do funcionário:"),
                cmbFuncionarios
        );
        content.setPrefWidth(350);

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton != null && (dialogButton == btnConfirmar || dialogButton.getButtonData() == ButtonBar.ButtonData.OK_DONE)) {
                String val = cmbFuncionarios.getEditor().getText();
                if (val == null || val.trim().isEmpty()) {
                    val = cmbFuncionarios.getValue();
                }
                return (val != null && !val.trim().isEmpty()) ? val.trim() : "Tiago";
            }
            return null;
        });

        return dialog.showAndWait();
    }
}
