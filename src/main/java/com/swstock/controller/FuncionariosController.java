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

    @FXML private Button btnResponsavelSessao;
    @FXML private TextField txtNovoNome;
    @FXML private TextField txtNovoNumero;
    @FXML private Button btnAdicionar;
    @FXML private Label lblFeedback;
    @FXML private Label lblTotalFuncionarios;

    @FXML private TableView<Funcionario> tblFuncionarios;
    @FXML private TableColumn<Funcionario, Number> colId;
    @FXML private TableColumn<Funcionario, String> colNumero;
    @FXML private TableColumn<Funcionario, String> colNome;
    @FXML private TableColumn<Funcionario, String> colDataCriacao;
    @FXML private TableColumn<Funcionario, Void> colAcoes;

    private FuncionarioDAO funcionarioDAO;
    private final ObservableList<Funcionario> funcionariosList = FXCollections.observableArrayList();
    private Stage stage;
    private Runnable onFuncionariosChangedCallback;

    @FXML
    public void initialize() {
        configurarBadgeResponsavel();
        com.swstock.util.DeadKeyHelper.attach(txtNovoNome);
        com.swstock.util.DeadKeyHelper.attach(txtNovoNumero);
        configurarTabela();
    }

    private void configurarBadgeResponsavel() {
        if (btnResponsavelSessao != null) {
            btnResponsavelSessao.setText("👤 Responsável: " + com.swstock.util.SessionManager.getInstance().getResponsavelAtual());
            com.swstock.util.SessionManager.getInstance().responsavelAtualProperty().addListener((obs, oldVal, newVal) -> {
                btnResponsavelSessao.setText("👤 Responsável: " + (newVal != null ? newVal : "Administrador"));
            });
            btnResponsavelSessao.setTooltip(new Tooltip("Responsável ativo na sessão. Clique para alternar."));
        }
    }

    @FXML
    private void handleTrocarResponsavel() {
        javafx.stage.Window owner = stage != null ? stage : (btnResponsavelSessao != null && btnResponsavelSessao.getScene() != null ? btnResponsavelSessao.getScene().getWindow() : null);
        com.swstock.util.SessionManager.getInstance().trocarResponsavel(owner);
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
        colNumero.setCellValueFactory(data -> new SimpleStringProperty(
                (data.getValue().getNumero() != null && !data.getValue().getNumero().trim().isEmpty()) ? data.getValue().getNumero() : "-"
        ));
        colNome.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNome()));
        colDataCriacao.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getCreatedAt() != null ? data.getValue().getCreatedAt() : "-"
        ));

        // Botões de Ações (Editar e Remover)
        colAcoes.setCellFactory(param -> new TableCell<>() {
            private final Button btnEditar = new Button("Editar");
            private final Button btnExcluir = new Button("Remover");

            {
                btnEditar.setStyle("-fx-background-color: #EFF6FF; -fx-text-fill: #1D4ED8; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3px 10px; -fx-background-radius: 4px; -fx-cursor: hand;");
                btnEditar.setOnAction(event -> {
                    Funcionario f = getTableView().getItems().get(getIndex());
                    if (f != null) {
                        abrirDialogEdicao(f);
                    }
                });

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
                    HBox box = new HBox(6, btnEditar, btnExcluir);
                    box.setAlignment(Pos.CENTER);
                    setGraphic(box);
                }
            }
        });

        tblFuncionarios.setItems(funcionariosList);

        com.swstock.database.DatabaseManager dbManager = (funcionarioDAO != null && funcionarioDAO.getDatabaseManager() != null)
                ? funcionarioDAO.getDatabaseManager()
                : com.swstock.database.DatabaseManager.getInstance();
        com.swstock.util.TablePreferencesHelper.vincular(tblFuncionarios, "funcionarios", dbManager);
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
        String numero = txtNovoNumero.getText() != null ? txtNovoNumero.getText().trim() : "";

        try {
            Funcionario f = new Funcionario(nomeFormatado, numero);
            funcionarioDAO.insert(f);

            txtNovoNome.clear();
            txtNovoNumero.clear();
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

    private void abrirDialogEdicao(Funcionario funcionario) {
        Dialog<Funcionario> dialog = new Dialog<>();
        dialog.setTitle("Editar Funcionário");
        dialog.setHeaderText("Alterar dados do funcionário ID #" + funcionario.getId());

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new javafx.geometry.Insets(20, 20, 10, 10));

        TextField txtNome = new TextField(funcionario.getNome());
        com.swstock.util.DeadKeyHelper.attach(txtNome);

        TextField txtNumero = new TextField(funcionario.getNumero() != null ? funcionario.getNumero() : "");
        txtNumero.setPromptText("Número / Matrícula");
        com.swstock.util.DeadKeyHelper.attach(txtNumero);

        grid.add(new Label("Nome do Funcionário:"), 0, 0);
        grid.add(txtNome, 1, 0);
        grid.add(new Label("Número / Matrícula:"), 0, 1);
        grid.add(txtNumero, 1, 1);

        dialogPane.setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                String nome = txtNome.getText();
                if (nome == null || nome.trim().isEmpty()) {
                    mostrarFeedback("O nome do funcionário não pode ser vazio.", true);
                    return null;
                }
                funcionario.setNome(formatarNome(nome.trim()));
                funcionario.setNumero(txtNumero.getText() != null ? txtNumero.getText().trim() : "");
                return funcionario;
            }
            return null;
        });

        Optional<Funcionario> res = dialog.showAndWait();
        res.ifPresent(editado -> {
            try {
                funcionarioDAO.update(editado);
                mostrarFeedback("Funcionário atualizado com sucesso!", false);
                carregarFuncionarios();
                if (onFuncionariosChangedCallback != null) {
                    onFuncionariosChangedCallback.run();
                }
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Erro ao atualizar funcionário", e);
                mostrarFeedback("Erro ao atualizar no banco: " + e.getMessage(), true);
            }
        });
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
