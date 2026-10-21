package com.swstock.controller;

import com.swstock.database.ProdutoDAO;
import com.swstock.database.ProdutoSubcategoriaDAO;
import com.swstock.model.Produto;
import com.swstock.model.ProdutoSubcategoria;
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
 * Controlador para o modal de gerenciamento de subcategorias/variações de um produto.
 */
public class ProductSubcategoriesController {

    private static final Logger LOGGER = Logger.getLogger(ProductSubcategoriesController.class.getName());

    @FXML private Label lblTituloProduto;
    @FXML private Label lblSubtituloCodigo;
    @FXML private Label lblTotalSubcategoriasContador;
    @FXML private Button btnResponsavelSessao;

    @FXML private ComboBox<String> cbNomeSubcategoria;
    @FXML private TextField txtQtdInicial;
    @FXML private Button btnAdicionar;

    @FXML private TableView<ProdutoSubcategoria> tblSubcategorias;
    @FXML private TableColumn<ProdutoSubcategoria, String> colNomeSubcategoria;
    @FXML private TableColumn<ProdutoSubcategoria, String> colQtdSubcategoria;
    @FXML private TableColumn<ProdutoSubcategoria, String> colStatusSubcategoria;
    @FXML private TableColumn<ProdutoSubcategoria, Void> colAcoesSubcategoria;

    @FXML private Label lblTotalEstoqueSoma;
    @FXML private Button btnDefinirAtiva;

    private Produto produto;
    private ProdutoSubcategoriaDAO produtoSubcategoriaDAO;
    private ProdutoDAO produtoDAO;
    private Stage stage;
    private Consumer<ProdutoSubcategoria> onSubcategoriaSelecionadaCallback;
    private Runnable onModificacaoCallback;

    private final ObservableList<ProdutoSubcategoria> subcategoriasList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        if (txtQtdInicial != null) {
            txtQtdInicial.textProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal.matches("\\d*")) {
                    txtQtdInicial.setText(newVal.replaceAll("[^\\d]", ""));
                }
            });
        }

        configurarBadgeResponsavel();
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

    private void configurarTabela() {
        colNomeSubcategoria.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getNomeSubcategoria()));
        colNomeSubcategoria.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B;");
                }
            }
        });

        colQtdSubcategoria.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getQuantidade() + " un."));
        colQtdSubcategoria.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER);
                    setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #0F172A;");
                }
            }
        });

        colStatusSubcategoria.setCellValueFactory(cell -> {
            int q = cell.getValue().getQuantidade();
            return new SimpleStringProperty(q > 0 ? "EM ESTOQUE" : "ESGOTADO");
        });
        colStatusSubcategoria.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER);
                    if ("EM ESTOQUE".equals(item)) {
                        setStyle("-fx-text-fill: #16A34A; -fx-font-weight: bold; -fx-font-size: 11px;");
                    } else {
                        setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold; -fx-font-size: 11px;");
                    }
                }
            }
        });

        colAcoesSubcategoria.setCellFactory(param -> new TableCell<>() {
            private final Button btnAdd = new Button("+1");
            private final Button btnSub = new Button("-1");
            private final Button btnEdit = new Button("✏️");
            private final Button btnDel = new Button("🗑️");
            private final HBox pane = new HBox(4, btnAdd, btnSub, btnEdit, btnDel);

            {
                pane.setAlignment(Pos.CENTER);
                btnAdd.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #15803D; -fx-font-weight: bold; -fx-font-size: 10px; -fx-cursor: hand; -fx-padding: 3 6;");
                btnSub.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #B91C1C; -fx-font-weight: bold; -fx-font-size: 10px; -fx-cursor: hand; -fx-padding: 3 6;");
                btnEdit.setStyle("-fx-background-color: #E2E8F0; -fx-font-size: 10px; -fx-cursor: hand; -fx-padding: 3 6;");
                btnDel.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #DC2626; -fx-font-size: 10px; -fx-cursor: hand; -fx-padding: 3 6;");

                btnAdd.setOnAction(e -> {
                    ProdutoSubcategoria sub = getTableView().getItems().get(getIndex());
                    ajustarEstoque(sub, sub.getQuantidade() + 1);
                });

                btnSub.setOnAction(e -> {
                    ProdutoSubcategoria sub = getTableView().getItems().get(getIndex());
                    if (sub.getQuantidade() > 0) {
                        ajustarEstoque(sub, sub.getQuantidade() - 1);
                    }
                });

                btnEdit.setOnAction(e -> {
                    ProdutoSubcategoria sub = getTableView().getItems().get(getIndex());
                    editarEstoqueDireto(sub);
                });

                btnDel.setOnAction(e -> {
                    ProdutoSubcategoria sub = getTableView().getItems().get(getIndex());
                    confirmarExclusao(sub);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });

        tblSubcategorias.setItems(subcategoriasList);

        com.swstock.database.DatabaseManager dbManager = (produtoSubcategoriaDAO != null && produtoSubcategoriaDAO.getDatabaseManager() != null)
                ? produtoSubcategoriaDAO.getDatabaseManager()
                : (produtoDAO != null && produtoDAO.getDatabaseManager() != null ? produtoDAO.getDatabaseManager() : com.swstock.database.DatabaseManager.getInstance());
        com.swstock.util.TablePreferencesHelper.vincular(tblSubcategorias, "produto_subcategorias", dbManager);
    }

    public void setDialogStage(Stage stage) {
        this.stage = stage;
    }

    public void setProdutoSubcategoriaDAO(ProdutoSubcategoriaDAO dao) {
        this.produtoSubcategoriaDAO = dao;
    }

    public void setProdutoDAO(ProdutoDAO dao) {
        this.produtoDAO = dao;
    }

    public void setOnSubcategoriaSelecionadaCallback(Consumer<ProdutoSubcategoria> callback) {
        this.onSubcategoriaSelecionadaCallback = callback;
    }

    public void setOnModificacaoCallback(Runnable callback) {
        this.onModificacaoCallback = callback;
    }

    public void setProduto(Produto produto) {
        this.produto = produto;
        if (produtoSubcategoriaDAO == null) {
            produtoSubcategoriaDAO = new ProdutoSubcategoriaDAO();
        }
        if (produtoDAO == null) {
            produtoDAO = new ProdutoDAO();
        }

        if (produto != null) {
            lblTituloProduto.setText("Subcategorias: " + produto.getNome());
            lblSubtituloCodigo.setText("CÓDIGO: #" + (produto.getCodigoLoja() != null ? produto.getCodigoLoja() : "N/A"));
            carregarCatalogo();
            carregarSubcategorias();
        }
    }

    public void carregarCatalogo() {
        if (cbNomeSubcategoria != null && produtoSubcategoriaDAO != null) {
            List<String> catalogo = produtoSubcategoriaDAO.getCatalogoSubcategorias();
            cbNomeSubcategoria.setItems(FXCollections.observableArrayList(catalogo));
        }
    }

    public void carregarSubcategorias() {
        if (produto == null || produto.getId() == null) {
            subcategoriasList.clear();
            return;
        }

        try {
            List<ProdutoSubcategoria> lista = produtoSubcategoriaDAO.findByProdutoId(produto.getId());
            subcategoriasList.setAll(lista);

            int totalUnidades = lista.stream().mapToInt(ProdutoSubcategoria::getQuantidade).sum();
            lblTotalSubcategoriasContador.setText(lista.size() + " Subcategoria(s) cadastrada(s)");
            lblTotalEstoqueSoma.setText("Total acumulado em subcategorias: " + totalUnidades + " un.");

            // Sincroniza estoque total do produto
            produto.setQuantidade(totalUnidades);
            produtoDAO.updateSemHistorico(produto);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao carregar subcategorias do produto.", e);
        }
    }

    @FXML
    private void handleAdicionarSubcategoria() {
        if (produto == null || produto.getId() == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Salvar Primeiro", "Salve o cadastro básico do produto antes de adicionar subcategorias.");
            return;
        }

        String nome = null;
        if (cbNomeSubcategoria != null) {
            if (cbNomeSubcategoria.getEditor() != null && cbNomeSubcategoria.getEditor().getText() != null && !cbNomeSubcategoria.getEditor().getText().trim().isEmpty()) {
                nome = cbNomeSubcategoria.getEditor().getText().trim();
            } else if (cbNomeSubcategoria.getValue() != null && !cbNomeSubcategoria.getValue().trim().isEmpty()) {
                nome = cbNomeSubcategoria.getValue().trim();
            }
        }

        if (nome == null || nome.trim().isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Campo Vazio", "Informe ou selecione o nome da subcategoria.");
            if (cbNomeSubcategoria != null) cbNomeSubcategoria.requestFocus();
            return;
        }

        int qtd = 0;
        try {
            String qStr = txtQtdInicial.getText().trim();
            if (!qStr.isEmpty()) {
                qtd = Math.max(0, Integer.parseInt(qStr));
            }
        } catch (NumberFormatException ignored) {}

        String nomeLimpo = nome.trim().toUpperCase();

        Optional<String> optResp = DialogHelper.solicitarResponsavel(
                stage != null ? stage : tblSubcategorias.getScene().getWindow(),
                "Responsável pelo Cadastro da Subcategoria",
                "Informe quem está cadastrando a subcategoria '" + nomeLimpo + "':"
        );
        if (optResp.isEmpty()) {
            return;
        }
        String responsavel = optResp.get();

        try {
            boolean ok = produtoSubcategoriaDAO.addSubcategoria(produto.getId(), nomeLimpo, qtd, responsavel);
            if (ok) {
                produto.setTemSubcategorias(true);
                produtoDAO.updateSemHistorico(produto);

                if (cbNomeSubcategoria != null) {
                    if (cbNomeSubcategoria.getEditor() != null) cbNomeSubcategoria.getEditor().clear();
                    cbNomeSubcategoria.setValue(null);
                }
                txtQtdInicial.setText("0");
                carregarCatalogo();
                carregarSubcategorias();
                if (onModificacaoCallback != null) onModificacaoCallback.run();
                mostrarAlerta(Alert.AlertType.INFORMATION, "Sucesso", "Subcategoria '" + nomeLimpo + "' cadastrada com sucesso!");
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                mostrarAlerta(Alert.AlertType.WARNING, "Subcategoria Duplicada", "Esta subcategoria já está cadastrada para este produto.");
            } else {
                LOGGER.log(Level.SEVERE, "Erro ao adicionar subcategoria.", e);
                mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Falha ao cadastrar: " + e.getMessage());
            }
        }
    }

    private void ajustarEstoque(ProdutoSubcategoria sub, int novaQtd) {
        Optional<String> optResp = DialogHelper.solicitarResponsavel(
                stage != null ? stage : tblSubcategorias.getScene().getWindow(),
                "Alteração de Estoque",
                "Informe quem está alterando o estoque da subcategoria '" + sub.getNomeSubcategoria() + "':"
        );
        if (optResp.isEmpty()) return;

        try {
            produtoSubcategoriaDAO.updateQuantidade(sub.getId(), novaQtd, "Modificação de estoque [" + sub.getNomeSubcategoria() + "]", optResp.get());
            carregarSubcategorias();
            if (onModificacaoCallback != null) onModificacaoCallback.run();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao ajustar quantidade.", e);
            mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Falha ao atualizar: " + e.getMessage());
        }
    }

    private void editarEstoqueDireto(ProdutoSubcategoria sub) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(sub.getQuantidade()));
        dialog.setTitle("Editar Estoque");
        dialog.setHeaderText("Editar estoque da subcategoria: " + sub.getNomeSubcategoria());
        dialog.setContentText("Nova quantidade em estoque:");

        Optional<String> res = dialog.showAndWait();
        if (res.isPresent()) {
            try {
                int novaQtd = Integer.parseInt(res.get().trim());
                if (novaQtd < 0) novaQtd = 0;
                ajustarEstoque(sub, novaQtd);
            } catch (NumberFormatException e) {
                mostrarAlerta(Alert.AlertType.WARNING, "Valor Inválido", "Digite um número inteiro válido.");
            }
        }
    }

    private void confirmarExclusao(ProdutoSubcategoria sub) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Excluir Subcategoria");
        confirm.setHeaderText("Deseja realmente excluir a subcategoria '" + sub.getNomeSubcategoria() + "'?");
        confirm.setContentText("Esta ação registrará a saída de " + sub.getQuantidade() + " un. no histórico de movimentações.");

        Optional<ButtonType> opt = confirm.showAndWait();
        if (opt.isPresent() && opt.get() == ButtonType.OK) {
            Optional<String> optResp = DialogHelper.solicitarResponsavel(
                    stage != null ? stage : tblSubcategorias.getScene().getWindow(),
                    "Responsável pela Exclusão",
                    "Informe quem está excluindo a subcategoria '" + sub.getNomeSubcategoria() + "':"
            );
            if (optResp.isEmpty()) return;

            try {
                boolean ok = produtoSubcategoriaDAO.deleteSubcategoria(sub.getId(), optResp.get());
                if (ok) {
                    carregarSubcategorias();
                    if (onModificacaoCallback != null) onModificacaoCallback.run();
                }
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Erro ao excluir subcategoria.", e);
                mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Falha ao excluir: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleDefinirAtiva() {
        ProdutoSubcategoria selecionada = tblSubcategorias.getSelectionModel().getSelectedItem();
        if (selecionada == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Seleção Necessária", "Selecione uma subcategoria na tabela para definir como ativa.");
            return;
        }
        if (onSubcategoriaSelecionadaCallback != null) {
            onSubcategoriaSelecionadaCallback.accept(selecionada);
        }
        handleFechar();
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
