package com.swstock.controller;

import com.swstock.database.FuncionarioDAO;
import com.swstock.model.Funcionario;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller para o modal de gestão e cadastro/remoção de funcionários do sistema.
 */
public class FuncionariosController {

    private static final Logger LOGGER = Logger.getLogger(FuncionariosController.class.getName());

    @FXML private TextField txtNovoNome;
    @FXML private Button btnAdicionar;
    @FXML private Label lblFeedback;
    @FXML private Label lblTotalFuncionarios;

    @FXML private TableView<Funcionario> tblFuncionarios;
    @FXML private TableColumn<Funcionario, Number> colId;
    @FXML private TableColumn<Funcionario, String> colNome;
    @FXML private TableColumn<Funcionario, String> colDataCriacao;
    @FXML private TableColumn<Funcionario, Void> colAcoes;

    private FuncionarioDAO funcionarioDAO;
    private final ObservableList<Funcionario> funcionariosList = FXCollections.observableArrayList();
    private Stage stage;
    private Runnable onFuncionariosChangedCallback;

    @FXML
    public void initialize() {
        configurarTabela();
    }

    public void setDialogStage(Stage stage) {
        this.stage = stage;
    }

    public void setFuncionarioDAO(FuncionarioDAO funcionarioDAO) {
        this.funcionarioDAO = funcionarioDAO;
        carregarFuncionarios();
    }

    public void setOnFuncionariosChangedCallback(Runnable callback) {
        this.onFuncionariosChangedCallback = callback;
    }

    private void configurarTabela() {
        colId.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getId() != null ? data.getValue().getId() : 0));
        colNome.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNome()));
        colDataCriacao.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getCreatedAt() != null ? data.getValue().getCreatedAt() : "-"
        ));

        // Botão de Excluir
        colAcoes.setCellFactory(param -> new TableCell<>() {
            private final Button btnExcluir = new Button("Remover");

            {
                btnExcluir.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #DC2626; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3px 10px; -fx-background-radius: 4px; -fx-cursor: hand;");
                btnExcluir.setOnAction(event -> {
                    Funcionario f = getTableView().getItems().get(getIndex());
                    if (f != null) {
                        confirmarExclusao(f);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(btnExcluir);
                    box.setAlignment(Pos.CENTER);
                    setGraphic(box);
                }
            }
        });

        tblFuncionarios.setItems(funcionariosList);
    }

    public void carregarFuncionarios() {
        if (funcionarioDAO == null) {
            funcionarioDAO = new FuncionarioDAO();
        }

        try {
            List<Funcionario> lista = funcionarioDAO.findAll();
            funcionariosList.setAll(lista);
            lblTotalFuncionarios.setText(lista.size() + " funcionário(s) cadastrado(s)");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao carregar lista de funcionários.", e);
            mostrarFeedback("Erro ao carregar dados do banco.", true);
        }
    }

    @FXML
    private void handleAdicionar() {
        String nome = txtNovoNome.getText();
        if (nome == null || nome.trim().isEmpty()) {
            mostrarFeedback("Digite o nome do funcionário para cadastrar.", true);
            txtNovoNome.requestFocus();
            return;
        }

        String nomeFormatado = formatarNome(nome.trim());

        try {
            Funcionario f = new Funcionario(nomeFormatado);
            funcionarioDAO.insert(f);

            txtNovoNome.clear();
            mostrarFeedback("Funcionário '" + nomeFormatado + "' cadastrado com sucesso!", false);
            carregarFuncionarios();

            if (onFuncionariosChangedCallback != null) {
                onFuncionariosChangedCallback.run();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erro ao cadastrar funcionário.", e);
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                mostrarFeedback("O funcionário '" + nomeFormatado + "' já está cadastrado.", true);
            } else {
                mostrarFeedback("Falha ao salvar no banco: " + e.getMessage(), true);
            }
        }
    }

    private void confirmarExclusao(Funcionario funcionario) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar Remoção");
        confirm.setHeaderText("Remover funcionário '" + funcionario.getNome() + "'?");
        confirm.setContentText("Ele não aparecerá mais como opção para novas movimentações.");

        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            try {
                funcionarioDAO.delete(funcionario.getId());
                mostrarFeedback("Funcionário removido com sucesso.", false);
                carregarFuncionarios();

                if (onFuncionariosChangedCallback != null) {
                    onFuncionariosChangedCallback.run();
                }
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Erro ao excluir funcionário.", e);
                mostrarFeedback("Erro ao excluir: " + e.getMessage(), true);
            }
        }
    }

    private void mostrarFeedback(String msg, boolean isError) {
        lblFeedback.setText(msg);
        lblFeedback.setStyle(isError ? "-fx-text-fill: #DC2626;" : "-fx-text-fill: #16A34A;");
        lblFeedback.setVisible(true);
        lblFeedback.setManaged(true);
    }

    private String formatarNome(String nome) {
        String[] partes = nome.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : partes) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0)));
                if (p.length() > 1) {
                    sb.append(p.substring(1).toLowerCase());
                }
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }

    @FXML
    private void handleFechar() {
        if (stage != null) {
            stage.close();
        }
    }
}
