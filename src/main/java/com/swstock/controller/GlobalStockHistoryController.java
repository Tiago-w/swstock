package com.swstock.controller;

import com.swstock.database.HistoricoEstoqueDAO;
import com.swstock.database.ProdutoDAO;
import com.swstock.model.HistoricoEstoque;
import com.swstock.model.Produto;
import com.swstock.service.PdfReportService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller para a visualização do Calendário e Histórico Global de Estoque.
 * Consolida todas as entradas, saídas e movimentações de todos os produtos do armazém,
 * com suporte a filtro por Fabricante / Grupo e exportação de relatórios customizados em PDF.
 */
public class GlobalStockHistoryController {

    private static final Logger LOGGER = Logger.getLogger(GlobalStockHistoryController.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private DatePicker dpDataInicio;
    @FXML private DatePicker dpDataFim;
    @FXML private ComboBox<String> cbTipoOperacao;
    @FXML private ComboBox<String> cbGrupoFabricante;
    @FXML private TextField txtBuscaGlobal;

    @FXML private Label lblTotalEntradasGlobal;
    @FXML private Label lblTotalSaidasGlobal;
    @FXML private Label lblBalancoGlobal;
    @FXML private Label lblTotalRegistrosGlobal;
    @FXML private Label lblStatusFiltro;

    @FXML private TableView<HistoricoEstoque> tblHistoricoGlobal;
    @FXML private TableColumn<HistoricoEstoque, String> colDataHora;
    @FXML private TableColumn<HistoricoEstoque, String> colCodigo;
    @FXML private TableColumn<HistoricoEstoque, String> colProduto;
    @FXML private TableColumn<HistoricoEstoque, String> colGrupo;
    @FXML private TableColumn<HistoricoEstoque, String> colLocalizacao;
    @FXML private TableColumn<HistoricoEstoque, String> colTipo;
    @FXML private TableColumn<HistoricoEstoque, String> colQtd;
    @FXML private TableColumn<HistoricoEstoque, String> colSaldo;
    @FXML private TableColumn<HistoricoEstoque, String> colResponsavel;
    @FXML private TableColumn<HistoricoEstoque, String> colMotivo;
    @FXML private TableColumn<HistoricoEstoque, Void> colAcoes;

    private HistoricoEstoqueDAO historicoDAO;
    private ProdutoDAO produtoDAO;
    private Stage stage;
    private Consumer<Produto> onAbrirProdutoDetalhesCallback;

    private final ObservableList<HistoricoEstoque> movimentosList = FXCollections.observableArrayList();
    private final PdfReportService pdfReportService = new PdfReportService();

    @FXML
    public void initialize() {
        if (historicoDAO == null) {
            historicoDAO = new HistoricoEstoqueDAO();
        }
        if (produtoDAO == null) {
            produtoDAO = new ProdutoDAO();
        }

        cbTipoOperacao.setItems(FXCollections.observableArrayList(
                "Todos os Tipos (Entradas e Saídas)",
                "Apenas Entradas",
                "Apenas Saídas"
        ));
        cbTipoOperacao.setValue("Todos os Tipos (Entradas e Saídas)");

        carregarOpcoesGrupos();
        configurarTabela();
        configurarBuscaInstantanea();
    }

    public void carregarOpcoesGrupos() {
        if (cbGrupoFabricante == null) return;
        try {
            List<String> grupos = historicoDAO.getAllGrupos();
            ObservableList<String> itens = FXCollections.observableArrayList();
            itens.add("Todos os Grupos / Fabricantes");
            itens.addAll(grupos);
            cbGrupoFabricante.setItems(itens);
            cbGrupoFabricante.setValue("Todos os Grupos / Fabricantes");
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erro ao carregar lista de grupos de fabricantes.", e);
        }
    }

    public void setDialogStage(Stage stage) {
        this.stage = stage;
    }

    public void setProdutoDAO(ProdutoDAO produtoDAO) {
        this.produtoDAO = produtoDAO;
    }

    public void setHistoricoDAO(HistoricoEstoqueDAO historicoDAO) {
        this.historicoDAO = historicoDAO;
    }

    public void setOnAbrirProdutoDetalhesCallback(Consumer<Produto> callback) {
        this.onAbrirProdutoDetalhesCallback = callback;
    }

    private void configurarTabela() {
        colDataHora.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDataHoraFormatada()));
        colCodigo.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProdutoCodigo()));
        colProduto.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProdutoNome()));
        
        if (colGrupo != null) {
            colGrupo.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProdutoGrupo()));
            colGrupo.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item);
                        setAlignment(Pos.CENTER_LEFT);
                    }
                }
            });
        }

        colLocalizacao.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProdutoLocalizacao()));
        colTipo.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTipo()));
        colQtd.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getMovimentoFormatado()));
        colSaldo.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSaldoFormatado()));
        colResponsavel.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getResponsavel()));
        colMotivo.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getMotivo() != null ? data.getValue().getMotivo() : "-"));

        // Badge para Tipo
        colTipo.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(item.equalsIgnoreCase("ENTRADA") ? "ENTRADA" : (item.equalsIgnoreCase("SAIDA") ? "SAIDA" : "AJUSTE"));
                    badge.getStyleClass().add(item.equalsIgnoreCase("ENTRADA") ? "history-badge-in" : "history-badge-out");
                    HBox box = new HBox(badge);
                    box.setAlignment(Pos.CENTER);
                    setGraphic(box);
                    setText(null);
                }
            }
        });

        // Cor para Quantidade
        colQtd.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.startsWith("+")) {
                        setStyle("-fx-text-fill: #16A34A; -fx-font-weight: bold;");
                    } else if (item.startsWith("-")) {
                        setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #0F172A; -fx-font-weight: bold;");
                    }
                    setAlignment(Pos.CENTER);
                }
            }
        });

        // Destaque para Responsável
        colResponsavel.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B;");
                    setAlignment(Pos.CENTER_LEFT);
                }
            }
        });

        // Coluna de Ações
        colAcoes.setCellFactory(column -> new TableCell<>() {
            private final Button btnVer = new Button("Detalhes");

            {
                btnVer.getStyleClass().add("btn-action-details");
                btnVer.setStyle("-fx-font-size: 10px; -fx-padding: 3px 8px;");
                btnVer.setOnAction(event -> {
                    HistoricoEstoque h = getTableView().getItems().get(getIndex());
                    if (h != null && onAbrirProdutoDetalhesCallback != null && produtoDAO != null) {
                        try {
                            Produto p = produtoDAO.findById(h.getProdutoId());
                            if (p != null) {
                                onAbrirProdutoDetalhesCallback.accept(p);
                            }
                        } catch (SQLException e) {
                            LOGGER.log(Level.WARNING, "Erro ao buscar produto por ID.", e);
                        }
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(btnVer);
                    box.setAlignment(Pos.CENTER);
                    setGraphic(box);
                }
            }
        });

        tblHistoricoGlobal.setItems(movimentosList);
    }

    private void configurarBuscaInstantanea() {
        txtBuscaGlobal.textProperty().addListener((obs, oldVal, newVal) -> {
            carregarDados();
        });

        cbTipoOperacao.valueProperty().addListener((obs, oldVal, newVal) -> {
            carregarDados();
        });

        if (cbGrupoFabricante != null) {
            cbGrupoFabricante.valueProperty().addListener((obs, oldVal, newVal) -> {
                carregarDados();
            });
        }
    }

    /**
     * Consulta todas as movimentações consolidadas aplicando os filtros selecionados.
     */
    public void carregarDados() {
        if (historicoDAO == null) {
            historicoDAO = new HistoricoEstoqueDAO();
        }

        LocalDate dataInicio = dpDataInicio.getValue();
        LocalDate dataFim = dpDataFim.getValue();
        String tipo = cbTipoOperacao.getValue();
        String grupoFiltro = cbGrupoFabricante != null ? cbGrupoFabricante.getValue() : null;
        String busca = txtBuscaGlobal.getText();

        try {
            List<HistoricoEstoque> lista = historicoDAO.findAllGlobal(dataInicio, dataFim, tipo, grupoFiltro, busca);
            movimentosList.setAll(lista);

            int totalEntradas = 0;
            int totalSaidas = 0;

            for (HistoricoEstoque h : lista) {
                int delta = h.getQuantidadeAlterada() != null ? h.getQuantidadeAlterada() : 0;
                if (delta > 0) {
                    totalEntradas += delta;
                } else if (delta < 0) {
                    totalSaidas += Math.abs(delta);
                }
            }

            int balanco = totalEntradas - totalSaidas;

            lblTotalEntradasGlobal.setText("+" + totalEntradas + " un.");
            lblTotalSaidasGlobal.setText("-" + totalSaidas + " un.");
            lblBalancoGlobal.setText((balanco >= 0 ? "+" : "") + balanco + " un.");
            lblTotalRegistrosGlobal.setText(lista.size() + " movimento(s)");

            // Atualiza status descritivo
            StringBuilder status = new StringBuilder("Exibindo: ");
            if (dataInicio != null && dataFim != null) {
                status.append("Período de ").append(dataInicio.format(DATE_FORMATTER)).append(" até ").append(dataFim.format(DATE_FORMATTER));
            } else if (dataInicio != null) {
                status.append("Data: ").append(dataInicio.format(DATE_FORMATTER));
            } else {
                status.append("Todo o Histórico");
            }

            if (tipo != null && !tipo.toUpperCase().contains("TODOS")) {
                status.append(" | Tipo: ").append(tipo);
            }
            if (grupoFiltro != null && !grupoFiltro.toUpperCase().contains("TODOS")) {
                status.append(" | Fabricante: ").append(grupoFiltro);
            }
            if (busca != null && !busca.trim().isEmpty()) {
                status.append(" | Busca: '").append(busca.trim()).append("'");
            }
            lblStatusFiltro.setText(status.toString());

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao carregar extrato global de estoque.", e);
        }
    }

    /**
     * Exporta as movimentações de estoque conforme os filtros ativos na tela (tipo, fabricante/grupo, período) em PDF.
     */
    @FXML
    private void handleExportarPdf() {
        if (historicoDAO == null) {
            historicoDAO = new HistoricoEstoqueDAO();
        }

        LocalDate dataInicio = dpDataInicio.getValue();
        LocalDate dataFim = dpDataFim.getValue();
        String tipoFiltro = cbTipoOperacao.getValue();
        String grupoFiltro = cbGrupoFabricante != null ? cbGrupoFabricante.getValue() : null;
        String busca = txtBuscaGlobal.getText();

        try {
            List<HistoricoEstoque> registros = historicoDAO.findAllGlobal(dataInicio, dataFim, tipoFiltro, grupoFiltro, busca);

            if (registros.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Exportação PDF");
                alert.setHeaderText("Nenhum Registro Encontrado");
                alert.setContentText("Não há movimentações de estoque correspondentes aos filtros selecionados.");
                alert.showAndWait();
                return;
            }

            String sufixoTipo = "movimentacoes";
            if (tipoFiltro != null) {
                if (tipoFiltro.contains("Entrada")) {
                    sufixoTipo = "entradas";
                } else if (tipoFiltro.contains("Saída") || tipoFiltro.contains("Saida")) {
                    sufixoTipo = "saidas";
                }
            }

            String sufixoGrupo = "";
            if (grupoFiltro != null && !grupoFiltro.toUpperCase().contains("TODOS")) {
                sufixoGrupo = "_" + grupoFiltro.replaceAll("[^a-zA-Z0-9_-]", "_");
            }

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Salvar Relatório em PDF (SWStock)");
            String sufixoData = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            fileChooser.setInitialFileName("relatorio_" + sufixoTipo + sufixoGrupo + "_swstock_" + sufixoData + ".pdf");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF (*.pdf)", "*.pdf"));
            fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

            File destino = fileChooser.showSaveDialog(stage != null ? stage : tblHistoricoGlobal.getScene().getWindow());

            if (destino != null) {
                pdfReportService.gerarRelatorio(destino, registros, dataInicio, dataFim, tipoFiltro, grupoFiltro, busca);

                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Relatório PDF Gerado");
                confirm.setHeaderText("Relatório gerado com sucesso!");
                confirm.setContentText("Arquivo salvo em:\n" + destino.getAbsolutePath() + "\n\nDeseja abrir o arquivo PDF agora?");
                confirm.getButtonTypes().setAll(new ButtonType("Abrir PDF", ButtonBar.ButtonData.YES), new ButtonType("Concluir", ButtonBar.ButtonData.NO));

                Optional<ButtonType> opt = confirm.showAndWait();
                if (opt.isPresent() && opt.get().getButtonData() == ButtonBar.ButtonData.YES) {
                    try {
                        if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().open(destino);
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Não foi possível abrir o visualizador de PDF padrão.", ex);
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao gerar PDF de movimentações.", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erro na Exportação PDF");
            alert.setHeaderText("Falha ao gerar o arquivo PDF");
            alert.setContentText("Ocorreu um erro durante a criação do relatório:\n" + e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void handleFiltrar() {
        carregarDados();
    }

    @FXML
    private void handleAtalhoHoje() {
        LocalDate hoje = LocalDate.now();
        dpDataInicio.setValue(hoje);
        dpDataFim.setValue(hoje);
        carregarDados();
    }

    @FXML
    private void handleAtalhoUltimos7Dias() {
        LocalDate hoje = LocalDate.now();
        dpDataInicio.setValue(hoje.minusDays(7));
        dpDataFim.setValue(hoje);
        carregarDados();
    }

    @FXML
    private void handleAtalhoEsteMes() {
        LocalDate hoje = LocalDate.now();
        dpDataInicio.setValue(hoje.with(TemporalAdjusters.firstDayOfMonth()));
        dpDataFim.setValue(hoje.with(TemporalAdjusters.lastDayOfMonth()));
        carregarDados();
    }

    @FXML
    private void handleAtalhoTodos() {
        dpDataInicio.setValue(null);
        dpDataFim.setValue(null);
        carregarDados();
    }

    @FXML
    private void handleLimparFiltros() {
        dpDataInicio.setValue(null);
        dpDataFim.setValue(null);
        cbTipoOperacao.setValue("Todos os Tipos (Entradas e Saídas)");
        if (cbGrupoFabricante != null) {
            cbGrupoFabricante.setValue("Todos os Grupos / Fabricantes");
        }
        txtBuscaGlobal.clear();
        carregarDados();
    }

    @FXML
    private void handleRecarregar() {
        carregarOpcoesGrupos();
        carregarDados();
    }

    @FXML
    private void handleFechar() {
        if (stage != null) {
            stage.close();
        }
    }
}
