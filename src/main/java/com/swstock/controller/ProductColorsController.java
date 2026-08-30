package com.swstock.controller;

import com.swstock.database.ProdutoCorDAO;
import com.swstock.database.ProdutoDAO;
import com.swstock.model.Produto;
import com.swstock.model.ProdutoCor;
import com.swstock.util.DialogHelper;
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
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador para o modal de gerenciamento de cores/variações de um produto.
 */
public class ProductColorsController {

    private static final Logger LOGGER = Logger.getLogger(ProductColorsController.class.getName());

    @FXML private Label lblTituloProduto;
    @FXML private Label lblSubtituloCodigo;
    @FXML private Label lblTotalCoresContador;

    @FXML private TextField txtNomeCor;
    @FXML private TextField txtQtdInicial;
    @FXML private Button btnAdicionar;

    @FXML private TableView<ProdutoCor> tblCores;
    @FXML private TableColumn<ProdutoCor, String> colNomeCor;
    @FXML private TableColumn<ProdutoCor, String> colQtdCor;
    @FXML private TableColumn<ProdutoCor, String> colStatusCor;
    @FXML private TableColumn<ProdutoCor, Void> colAcoesCor;

    @FXML private Label lblTotalEstoqueSoma;
    @FXML private Button btnDefinirAtiva;

    private Produto produto;
    private ProdutoCorDAO produtoCorDAO;
    private ProdutoDAO produtoDAO;
    private Stage stage;
    private Consumer<ProdutoCor> onCorSelecionadaCallback;
    private Runnable onModificacaoCallback;

    private final ObservableList<ProdutoCor> coresList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        if (txtQtdInicial != null) {
            txtQtdInicial.textProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal.matches("\\d*")) {
                    txtQtdInicial.setText(newVal.replaceAll("[^\\d]", ""));
                }
            });
        }

        configurarTabela();
    }

    private void configurarTabela() {
        colNomeCor.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getNomeCor()));
        colNomeCor.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER_LEFT);
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B;");
                }
            }
        });

        colQtdCor.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getQuantidade() + " un."));
        colQtdCor.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER);
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #16A34A;");
                }
            }
        });

        colStatusCor.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getQuantidade() > 0 ? "EM ESTOQUE" : "SEM ESTOQUE"
        ));
        colStatusCor.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().add(item.equals("EM ESTOQUE") ? "badge-stock-ok" : "badge-stock-empty");
                    HBox box = new HBox(badge);
                    box.setAlignment(Pos.CENTER);
                    setGraphic(box);
                    setText(null);
                }
            }
        });

        colAcoesCor.setCellFactory(column -> new TableCell<>() {
            private final Button btnMinus = new Button("-");
            private final Button btnPlus = new Button("+");
            private final Button btnExcluir = new Button("Excluir");
            private final HBox container = new HBox(6, btnMinus, btnPlus, btnExcluir);

            {
                container.setAlignment(Pos.CENTER);
                btnMinus.setStyle("-fx-background-color: #E2E8F0; -fx-font-weight: bold; -fx-padding: 3px 8px; -fx-cursor: hand; -fx-background-radius: 4px;");
                btnPlus.setStyle("-fx-background-color: #DBEAFE; -fx-text-fill: #1E40AF; -fx-font-weight: bold; -fx-padding: 3px 8px; -fx-cursor: hand; -fx-background-radius: 4px;");
                btnExcluir.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #DC2626; -fx-font-weight: bold; -fx-font-size: 10px; -fx-padding: 4px 8px; -fx-cursor: hand; -fx-background-radius: 4px;");

                btnMinus.setOnAction(e -> {
                    ProdutoCor cor = getItemCor();
                    if (cor != null && cor.getQuantidade() > 0) {
                        Optional<String> optResp = DialogHelper.solicitarResponsavel(
                                stage != null ? stage : getTableView().getScene().getWindow(),
                                "Responsável pela Alteração",
                                "Informe quem está diminuindo o estoque de '" + cor.getNomeCor() + "':"
                        );
                        if (optResp.isPresent()) {
                            atualizarEstoqueCor(cor, cor.getQuantidade() - 1, optResp.get());
                        }
                    }
                });

                btnPlus.setOnAction(e -> {
                    ProdutoCor cor = getItemCor();
                    if (cor != null) {
                        Optional<String> optResp = DialogHelper.solicitarResponsavel(
                                stage != null ? stage : getTableView().getScene().getWindow(),
                                "Responsável pela Alteração",
                                "Informe quem está aumentando o estoque de '" + cor.getNomeCor() + "':"
                        );
                        if (optResp.isPresent()) {
                            atualizarEstoqueCor(cor, cor.getQuantidade() + 1, optResp.get());
                        }
                    }
                });

                btnExcluir.setOnAction(e -> {
                    ProdutoCor cor = getItemCor();
                    if (cor != null) {
                        confirmarExclusaoCor(cor);
                    }
                });
            }

            private ProdutoCor getItemCor() {
                int index = getIndex();
                if (index >= 0 && index < getTableView().getItems().size()) {
                    return getTableView().getItems().get(index);
                }
                return null;
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(container);
                }
            }
        });

        tblCores.setItems(coresList);
    }

    public void setDialogStage(Stage stage) {
        this.stage = stage;
    }

    public void setProdutoCorDAO(ProdutoCorDAO produtoCorDAO) {
        this.produtoCorDAO = produtoCorDAO;
    }

    public void setProdutoDAO(ProdutoDAO produtoDAO) {
        this.produtoDAO = produtoDAO;
    }

    public void setOnCorSelecionadaCallback(Consumer<ProdutoCor> callback) {
        this.onCorSelecionadaCallback = callback;
    }

    public void setOnModificacaoCallback(Runnable callback) {
        this.onModificacaoCallback = callback;
    }

    public void setProduto(Produto produto) {
        this.produto = produto;
        if (this.produtoCorDAO == null) {
            this.produtoCorDAO = new ProdutoCorDAO();
        }
        if (this.produtoDAO == null) {
            this.produtoDAO = new ProdutoDAO();
        }

        if (produto != null) {
            lblTituloProduto.setText(produto.getNome() != null ? produto.getNome() : "Sem Nome");
            lblSubtituloCodigo.setText("CÓDIGO: #" + (produto.getCodigoLoja() != null ? produto.getCodigoLoja() : "N/A"));
            recarregarCores();
        }
    }

    public void recarregarCores() {
        if (produto == null || produto.getId() == null) {
            return;
        }
        try {
            List<ProdutoCor> lista = produtoCorDAO.findByProdutoId(produto.getId());
            coresList.setAll(lista);

            int totalQtd = lista.stream().mapToInt(ProdutoCor::getQuantidade).sum();
            lblTotalCoresContador.setText(lista.size() + " Cor(es) cadastrada(s)");
            lblTotalEstoqueSoma.setText("Total acumulado em cores: " + totalQtd + " un.");

            // Sincroniza a quantidade total do produto sem gerar entrada redundante no histórico
            if (produto.getId() != null && !lista.isEmpty()) {
                produtoDAO.updateQuantidadeSemHistorico(produto.getId(), totalQtd);
                produto.setQuantidade(totalQtd);
            }

            if (onModificacaoCallback != null) {
                onModificacaoCallback.run();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao carregar cores do produto.", e);
            mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Falha ao carregar lista de cores: " + e.getMessage());
        }
    }

    @FXML
    private void handleAdicionarCor() {
        if (produto == null || produto.getId() == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Aviso", "Salve o produto antes de cadastrar cores.");
            return;
        }

        String nome = txtNomeCor.getText() != null ? txtNomeCor.getText().trim() : "";
        if (nome.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Campo Obrigatório", "Informe o nome da cor.");
            txtNomeCor.requestFocus();
            return;
        }

        int qtd = 0;
        try {
            if (txtQtdInicial.getText() != null && !txtQtdInicial.getText().trim().isEmpty()) {
                qtd = Integer.parseInt(txtQtdInicial.getText().trim());
            }
        } catch (NumberFormatException ignored) {}

        // Solicita o responsável
        Optional<String> optResp = DialogHelper.solicitarResponsavel(
                stage != null ? stage : tblCores.getScene().getWindow(),
                "Responsável pelo Cadastro da Cor",
                "Informe quem está adicionando a cor '" + nome + "':"
        );
        if (optResp.isEmpty()) {
            return; // Salvamento cancelado
        }
        String responsavel = optResp.get();

        try {
            boolean inserido = produtoCorDAO.addCor(produto.getId(), nome, qtd, responsavel);
            if (inserido) {
                txtNomeCor.clear();
                txtQtdInicial.setText("0");
                recarregarCores();
            } else {
                mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Não foi possível cadastrar a cor.");
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                mostrarAlerta(Alert.AlertType.WARNING, "Cor Duplicada", "Esta cor já está cadastrada para este produto.");
            } else {
                LOGGER.log(Level.SEVERE, "Erro ao salvar nova cor.", e);
                mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Falha ao salvar cor: " + e.getMessage());
            }
        }
    }

    private void atualizarEstoqueCor(ProdutoCor cor, int novaQtd, String responsavel) {
        try {
            boolean ok = produtoCorDAO.updateQuantidade(cor.getId(), novaQtd, "Modificação de estoque [" + cor.getNomeCor() + "]", responsavel);
            if (ok) {
                cor.setQuantidade(novaQtd);
                recarregarCores();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao atualizar estoque da cor.", e);
            mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Falha ao atualizar estoque da cor: " + e.getMessage());
        }
    }

    private void confirmarExclusaoCor(ProdutoCor cor) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Excluir Cor");
        alert.setHeaderText("Excluir cor '" + cor.getNomeCor() + "'?");
        alert.setContentText("O saldo de " + cor.getQuantidade() + " un. desta cor será removido do estoque.");

        Optional<ButtonType> res = alert.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            Optional<String> optResp = DialogHelper.solicitarResponsavel(
                    stage != null ? stage : tblCores.getScene().getWindow(),
                    "Responsável pela Exclusão da Cor",
                    "Informe quem está excluindo a cor '" + cor.getNomeCor() + "':"
            );
            if (optResp.isEmpty()) {
                return;
            }
            try {
                produtoCorDAO.deleteCor(cor.getId(), optResp.get());
                recarregarCores();
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Erro ao excluir cor.", e);
                mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Falha ao excluir cor: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleDefinirAtiva() {
        ProdutoCor selecionada = tblCores.getSelectionModel().getSelectedItem();
        if (selecionada != null) {
            if (onCorSelecionadaCallback != null) {
                onCorSelecionadaCallback.accept(selecionada);
            }
            if (stage != null) {
                stage.close();
            }
        } else {
            mostrarAlerta(Alert.AlertType.INFORMATION, "Seleção", "Selecione uma cor na tabela para definir como ativa.");
        }
    }

    @FXML
    private void handleFechar() {
        if (stage != null) {
            stage.close();
        }
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String msg) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
