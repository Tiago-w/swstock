package com.swstock.controller;

import com.swstock.database.DatabaseManager;
import com.swstock.database.ProdutoDAO;
import com.swstock.model.Produto;
import com.swstock.service.XmlService;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller principal do SWStock.
 * Gerencia a janela central (BorderPane), menu lateral retrátil,
 * tabela de produtos com alinhamentos padronizados, grupos de fabricantes, filtros e integrações.
 */
public class MainController {

    private static final Logger LOGGER = Logger.getLogger(MainController.class.getName());

    @FXML private BorderPane rootPane;
    @FXML private VBox sidebar;
    @FXML private Button btnToggleSidebar;
    @FXML private Label lblHeaderStats;

    @FXML private TextField txtSearch;
    @FXML private Button btnNovoProduto;
    @FXML private Button btnRecarregar;
    @FXML private Label lblFiltroAtivo;
    @FXML private Button btnLimparFiltro;

    @FXML private TableView<Produto> tblProdutos;
    @FXML private TableColumn<Produto, String> colCodigoLoja;
    @FXML private TableColumn<Produto, String> colNome;
    @FXML private TableColumn<Produto, String> colGrupo;
    @FXML private TableColumn<Produto, String> colPrecoVista;
    @FXML private TableColumn<Produto, String> colPrecoPrazo;
    @FXML private TableColumn<Produto, String> colLocalizacao;
    @FXML private TableColumn<Produto, Produto> colQuantidade;
    @FXML private TableColumn<Produto, Void> colAcoes;

    @FXML private Label lblStatus;
    @FXML private Label lblTotalItens;

    private final ProdutoDAO produtoDAO = new ProdutoDAO();
    private final XmlService xmlService = new XmlService(produtoDAO);
    private final ObservableList<Produto> produtosList = FXCollections.observableArrayList();

    private String localizacaoFiltroAtiva = null;

    @FXML
    public void initialize() {
        if (sidebar != null) {
            sidebar.setVisible(false);
            sidebar.setManaged(false);
        }
        configurarTabela();
        configurarBuscaInstantanea();
        carregarProdutos();
    }

    /**
     * Configura as colunas da TableView com formatação, alinhamentos coerentes e células personalizadas.
     */
    private void configurarTabela() {
        // CÓD. LOJA (Alinhamento à Esquerda)
        colCodigoLoja.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getCodigoLoja() != null ? cell.getValue().getCodigoLoja() : "-"
        ));
        colCodigoLoja.setCellFactory(column -> new TableCell<Produto, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER_LEFT);
                }
            }
        });

        // NOME DO PRODUTO (Alinhamento à Esquerda)
        colNome.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getNome() != null ? cell.getValue().getNome() : "-"
        ));
        colNome.setCellFactory(column -> new TableCell<Produto, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER_LEFT);
                }
            }
        });

        // GRUPO / FABRICANTE (Alinhamento à Esquerda)
        colGrupo.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getGrupo() != null && !cell.getValue().getGrupo().trim().isEmpty()
                        ? cell.getValue().getGrupo()
                        : "GERAL"
        ));
        colGrupo.setCellFactory(column -> new TableCell<Produto, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER_LEFT);
                }
            }
        });

        // VALOR À VISTA (Alinhamento à Direita - Padrão Financeiro)
        colPrecoVista.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getPrecoVistaFormatado()
        ));
        colPrecoVista.setCellFactory(column -> new TableCell<Produto, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER_RIGHT);
                }
            }
        });

        // VALOR A PRAZO (Alinhamento à Direita - Padrão Financeiro)
        colPrecoPrazo.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getPrecoPrazoFormatado()
        ));
        colPrecoPrazo.setCellFactory(column -> new TableCell<Produto, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER_RIGHT);
                }
            }
        });

        // LOCALIZAÇÃO (Alinhamento à Esquerda)
        colLocalizacao.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getLocalizacao() != null && !cell.getValue().getLocalizacao().trim().isEmpty()
                        ? cell.getValue().getLocalizacao()
                        : "-"
        ));
        colLocalizacao.setCellFactory(column -> new TableCell<Produto, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER_LEFT);
                }
            }
        });

        // QUANTIDADE (Alinhamento Centralizado com Badges)
        colQuantidade.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue()));
        colQuantidade.setCellFactory(column -> new TableCell<Produto, Produto>() {
            @Override
            protected void updateItem(Produto p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    int qtd = p.getQuantidade() != null ? p.getQuantidade() : 0;
                    Label badge = new Label(qtd + " un.");
                    if (qtd == 0) {
                        badge.getStyleClass().add("badge-stock-empty");
                    } else if (qtd <= 5) {
                        badge.getStyleClass().add("badge-stock-low");
                    } else {
                        badge.getStyleClass().add("badge-stock-ok");
                    }
                    HBox container = new HBox(badge);
                    container.setAlignment(Pos.CENTER);
                    setGraphic(container);
                    setText(null);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        // AÇÕES (Alinhamento Centralizado)
        colAcoes.setCellFactory(param -> new TableCell<>() {
            private final Button btnDetalhes = new Button("Detalhes");
            private final Button btnMapa = new Button("Local");

            {
                btnDetalhes.getStyleClass().add("btn-action-details");
                btnDetalhes.setOnAction(event -> {
                    Produto p = getTableView().getItems().get(getIndex());
                    abrirModalDetalhes(p);
                });

                btnMapa.setStyle("-fx-background-color: #FEF3C7; -fx-text-fill: #92400E; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4px 8px; -fx-background-radius: 4px; -fx-cursor: hand;");
                btnMapa.setOnAction(event -> {
                    Produto p = getTableView().getItems().get(getIndex());
                    if (p != null && p.getLocalizacao() != null && !p.getLocalizacao().trim().isEmpty()) {
                        abrirMapaComDestaque(p.getLocalizacao());
                    } else {
                        mostrarAlerta(Alert.AlertType.INFORMATION, "Sem Localização", "Este produto não possui localização cadastrada no depósito.");
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox container = new HBox(6, btnDetalhes, btnMapa);
                    container.setAlignment(Pos.CENTER);
                    setGraphic(container);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        // Configuração de Menu de Contexto (Clique Direito) e Duplo Clique
        tblProdutos.setRowFactory(tv -> {
            TableRow<Produto> row = new TableRow<>();
            ContextMenu contextMenu = new ContextMenu();

            MenuItem itemDetalhes = new MenuItem("Ver / Editar Detalhes");
            itemDetalhes.setOnAction(event -> {
                Produto p = row.getItem();
                if (p != null) abrirModalDetalhes(p);
            });

            MenuItem itemMapa = new MenuItem("Ver Localização no Depósito (Piscando)");
            itemMapa.setOnAction(event -> {
                Produto p = row.getItem();
                if (p != null) {
                    if (p.getLocalizacao() != null && !p.getLocalizacao().trim().isEmpty()) {
                        abrirMapaComDestaque(p.getLocalizacao());
                    } else {
                        mostrarAlerta(Alert.AlertType.INFORMATION, "Sem Localização", "Este produto não possui localização cadastrada.");
                    }
                }
            });

            contextMenu.getItems().addAll(itemDetalhes, itemMapa);

            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(contextMenu)
            );

            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    abrirModalDetalhes(row.getItem());
                }
            });

            return row;
        });

        tblProdutos.setItems(produtosList);
    }

    private void configurarBuscaInstantanea() {
        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            carregarProdutos();
        });
    }

    @FXML
    private void handleToggleSidebar() {
        boolean visivel = sidebar.isVisible();
        sidebar.setVisible(!visivel);
        sidebar.setManaged(!visivel);
    }

    public void carregarProdutos() {
        try {
            String termo = txtSearch.getText();
            List<Produto> lista = produtoDAO.findByFilter(termo, localizacaoFiltroAtiva);
            produtosList.setAll(lista);

            int totalUnidades = lista.stream()
                    .mapToInt(p -> p.getQuantidade() != null ? p.getQuantidade() : 0)
                    .sum();

            lblTotalItens.setText(String.format("Total: %d produtos cadastrados (%d unidades no total)",
                    lista.size(), totalUnidades));
            lblHeaderStats.setText(String.format("📦 %d Itens (%d un.)", lista.size(), totalUnidades));
            lblStatus.setText("Banco de Dados SQLite: Sincronizado e Operacional");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao carregar produtos do banco.", e);
            lblStatus.setText("⚠ Erro ao consultar o banco de dados.");
        }
    }

    @FXML
    private void handleAbrirMapa2D() {
        abrirMapaComDestaque(null);
    }

    public void abrirMapaComDestaque(String localizacao) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/swstock/view/Map2DView.fxml"));
            Parent root = loader.load();

            Map2DController controller = loader.getController();
            Stage stage = new Stage();
            stage.setTitle("SWStock - Planta Baixa do Depósito (Mapa 2D)");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(rootPane.getScene().getWindow());
            stage.setScene(new Scene(root));

            controller.setDialogStage(stage);
            controller.setProdutoDAO(produtoDAO);
            controller.setOnLocationSelectedCallback(this::aplicarFiltroLocalizacao);
            controller.carregarMapa();

            if (localizacao != null && !localizacao.trim().isEmpty()) {
                controller.destacarLocalizacao(localizacao.trim());
            }

            stage.setMaximized(true);
            stage.show();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao abrir tela do Mapa 2D.", e);
            mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Não foi possível carregar o Mapa 2D: " + e.getMessage());
        }
    }

    @FXML
    private void handleAbrirHistoricoGeral() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/swstock/view/GlobalStockHistoryView.fxml"));
            Parent root = loader.load();

            GlobalStockHistoryController controller = loader.getController();
            Stage stage = new Stage();
            stage.setTitle("SWStock - Calendário & Extrato Geral de Movimentações");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(rootPane.getScene().getWindow());
            stage.setScene(new Scene(root));

            controller.setDialogStage(stage);
            controller.setProdutoDAO(produtoDAO);
            controller.setOnAbrirProdutoDetalhesCallback(this::abrirModalDetalhes);
            controller.carregarDados();

            stage.setMaximized(true);
            stage.show();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao abrir histórico global de estoque.", e);
            mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Não foi possível carregar o histórico geral: " + e.getMessage());
        }
    }

    @FXML
    private void handleAbrirFuncionarios() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/swstock/view/FuncionariosView.fxml"));
            Parent root = loader.load();

            FuncionariosController controller = loader.getController();
            Stage stage = new Stage();
            stage.setTitle("SWStock - Gestão de Funcionários");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(rootPane.getScene().getWindow());
            stage.setScene(new Scene(root));

            controller.setDialogStage(stage);
            controller.setFuncionarioDAO(new com.swstock.database.FuncionarioDAO());

            stage.show();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao abrir tela de gestão de funcionários.", e);
            mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Não foi possível abrir o módulo de funcionários: " + e.getMessage());
        }
    }

    public void aplicarFiltroLocalizacao(String localizacao) {
        this.localizacaoFiltroAtiva = localizacao;
        if (localizacao != null && !localizacao.trim().isEmpty()) {
            lblFiltroAtivo.setText("Filtrado por: " + localizacao.toUpperCase());
            btnLimparFiltro.setVisible(true);
            btnLimparFiltro.setManaged(true);
        } else {
            lblFiltroAtivo.setText("Exibindo: Todos os itens");
            btnLimparFiltro.setVisible(false);
            btnLimparFiltro.setManaged(false);
        }
        carregarProdutos();
    }

    @FXML
    private void handleLimparFiltro() {
        aplicarFiltroLocalizacao(null);
    }

    @FXML
    private void handleNovoProduto() {
        abrirModalDetalhes(null);
    }

    @FXML
    private void handleRecarregar() {
        carregarProdutos();
    }

    private void abrirModalDetalhes(Produto produto) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/swstock/view/ProductDetailModal.fxml"));
            Parent root = loader.load();

            ProductDetailController controller = loader.getController();
            Stage stage = new Stage();
            stage.setTitle(produto == null ? "SWStock - Novo Produto" : "SWStock - Detalhes do Produto: " + produto.getNome());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(rootPane.getScene().getWindow());
            stage.setScene(new Scene(root));

            controller.setDialogStage(stage);
            controller.setProdutoDAO(produtoDAO);
            controller.setOnUpdateCallback(this::carregarProdutos);
            controller.setOnOpenMapCallback(loc -> abrirMapaComDestaque(loc));
            controller.setProduto(produto);

            stage.show();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao abrir modal de detalhes.", e);
            mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Não foi possível abrir os detalhes do produto: " + e.getMessage());
        }
    }

    @FXML
    private void handleImportarXml() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Importar XML de Produtos (SWStock)");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Arquivos XML (*.xml)", "*.xml"));

        File sampleDir = new File("/home/tiagowolowski/Documentos/swstock/sample_data");
        if (sampleDir.exists() && sampleDir.isDirectory()) {
            fileChooser.setInitialDirectory(sampleDir);
        } else {
            File mediaDir = new File("/media");
            if (mediaDir.exists() && mediaDir.isDirectory()) {
                fileChooser.setInitialDirectory(mediaDir);
            } else {
                fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
            }
        }

        File selectedFile = fileChooser.showOpenDialog(rootPane.getScene().getWindow());
        if (selectedFile != null) {
            try {
                XmlService.ImportResult result = xmlService.importarProdutos(selectedFile);
                carregarProdutos();

                StringBuilder msg = new StringBuilder();
                msg.append("Sincronização Inteligente Concluída com Sucesso!\n\n");
                msg.append("Resumo da Sincronização:\n");
                msg.append(String.format("• Total de itens lidos do XML: %d\n", result.totalLidos()));
                msg.append(String.format("• Produtos já existentes atualizados: %d\n", result.atualizadosPreservados()));
                msg.append(String.format("• Novos produtos cadastrados no catálogo: %d\n", result.novosInseridos()));
                msg.append(String.format("• Preços reajustados: %d\n", result.precosAlterados()));
                msg.append(String.format("• Nomes alterados no catálogo: %d\n", result.nomesAlterados()));
                msg.append(String.format("• Itens do estoque mantidos (não presentes no XML): %d\n\n", result.itensMantidosNoBancoNaoPresentesNoXml()));
                msg.append("PROTEÇÃO DE ESTOQUE:\n");
                msg.append("100% das quantidades físicas de estoque e posições de estantes foram congeladas e preservadas com segurança.");

                if (!result.erros().isEmpty()) {
                    msg.append("\n\nOcorrências/Avisos:\n").append(String.join("\n", result.erros()));
                }

                mostrarAlerta(Alert.AlertType.INFORMATION, "Sincronização de XML Concluída", msg.toString());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Erro ao importar XML.", e);
                mostrarAlerta(Alert.AlertType.ERROR, "Erro de Importação",
                        "Falha ao ler o arquivo XML: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleExportarXml() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exportar Estoque para XML");
        fileChooser.setInitialFileName("estoque_swstock_" + System.currentTimeMillis() + ".xml");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Arquivos XML (*.xml)", "*.xml"));
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        File destino = fileChooser.showSaveDialog(rootPane.getScene().getWindow());
        if (destino != null) {
            try {
                xmlService.exportarProdutos(destino);
                mostrarAlerta(Alert.AlertType.INFORMATION, "Exportação Concluída",
                        "Arquivo XML gerado com sucesso em:\n" + destino.getAbsolutePath());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Erro ao exportar XML.", e);
                mostrarAlerta(Alert.AlertType.ERROR, "Erro de Importação",
                        "Falha ao salvar o arquivo XML: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleSair() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Encerrar SWStock");
        confirm.setHeaderText("Deseja realmente sair do sistema?");
        confirm.setContentText("A conexão com o banco SQLite será encerrada com segurança.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            LOGGER.info("Encerramento solicitado pelo usuário. Fechando SQLite...");
            DatabaseManager.getInstance().close();
            Platform.exit();
            System.exit(0);
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
