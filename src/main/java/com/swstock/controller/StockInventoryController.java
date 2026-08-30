package com.swstock.controller;

import com.swstock.database.ProdutoCorDAO;
import com.swstock.database.ProdutoDAO;
import com.swstock.model.Produto;
import com.swstock.model.ProdutoCor;
import com.swstock.service.PdfReportService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller para a tela de Levantamento de Estoque & Ficha de Pedido.
 * Cria uma linha individual para cada variação de cor dos produtos cadastrados.
 */
public class StockInventoryController {

    private static final Logger LOGGER = Logger.getLogger(StockInventoryController.class.getName());

    @FXML private ComboBox<String> cbGrupo;
    @FXML private ComboBox<String> cbFiltroEstoque;
    @FXML private TextField txtBusca;

    @FXML private Label lblTotalItens;
    @FXML private Label lblTotalUnidades;
    @FXML private Label lblValorTotalEstoque;
    @FXML private Label lblStatusFiltro;

    @FXML private TableView<ItemLevantamento> tblProdutos;
    @FXML private TableColumn<ItemLevantamento, String> colCodigo;
    @FXML private TableColumn<ItemLevantamento, String> colNome;
    @FXML private TableColumn<ItemLevantamento, String> colGrupo;
    @FXML private TableColumn<ItemLevantamento, String> colCores;
    @FXML private TableColumn<ItemLevantamento, Integer> colEstoque;
    @FXML private TableColumn<ItemLevantamento, Double> colPrecoVista;
    @FXML private TableColumn<ItemLevantamento, Double> colPrecoPrazo;

    private Stage stage;
    private ProdutoDAO produtoDAO;
    private ProdutoCorDAO produtoCorDAO;
    private final PdfReportService pdfReportService = new PdfReportService();

    private final ObservableList<ItemLevantamento> itensList = FXCollections.observableArrayList();
    private List<Produto> produtosBaseCarregados = new ArrayList<>();
    private Map<Integer, List<ProdutoCor>> mapaCores = new HashMap<>();

    public static class ItemLevantamento {
        private final Integer produtoId;
        private final String codigoLoja;
        private final String nome;
        private final String grupo;
        private final String cor;
        private final Integer estoque;
        private final Double precoVista;
        private final Double precoPrazo;

        public ItemLevantamento(Integer produtoId, String codigoLoja, String nome, String grupo, String cor, Integer estoque, Double precoVista, Double precoPrazo) {
            this.produtoId = produtoId;
            this.codigoLoja = codigoLoja;
            this.nome = nome;
            this.grupo = grupo;
            this.cor = cor;
            this.estoque = estoque;
            this.precoVista = precoVista;
            this.precoPrazo = precoPrazo;
        }

        public Integer getProdutoId() { return produtoId; }
        public String getCodigoLoja() { return codigoLoja; }
        public String getNome() { return nome; }
        public String getGrupo() { return grupo; }
        public String getCor() { return cor; }
        public Integer getEstoque() { return estoque; }
        public Double getPrecoVista() { return precoVista; }
        public Double getPrecoPrazo() { return precoPrazo; }
    }

    @FXML
    public void initialize() {
        if (produtoDAO == null) {
            produtoDAO = new ProdutoDAO();
        }
        if (produtoCorDAO == null) {
            produtoCorDAO = new ProdutoCorDAO();
        }

        cbFiltroEstoque.setItems(FXCollections.observableArrayList(
                "Todos os Produtos",
                "Apenas com Estoque (> 0)",
                "Apenas Zerados (= 0)"
        ));
        cbFiltroEstoque.setValue("Todos os Produtos");

        configurarTabela();
        configurarListeners();
    }

    public void setDialogStage(Stage stage) {
        this.stage = stage;
    }

    public void setProdutoDAO(ProdutoDAO produtoDAO) {
        this.produtoDAO = produtoDAO;
    }

    public void setProdutoCorDAO(ProdutoCorDAO produtoCorDAO) {
        this.produtoCorDAO = produtoCorDAO;
    }

    public void carregarDadosIniciais() {
        carregarOpcoesGrupos();
        carregarProdutos();
    }

    private void carregarOpcoesGrupos() {
        try {
            List<String> grupos = produtoDAO.getAllGrupos();
            ObservableList<String> itens = FXCollections.observableArrayList();
            itens.add("Todos os Grupos / Fabricantes");
            itens.addAll(grupos);
            cbGrupo.setItems(itens);
            cbGrupo.setValue("Todos os Grupos / Fabricantes");
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erro ao carregar lista de grupos para levantamento.", e);
        }
    }

    private void configurarTabela() {
        colCodigo.setCellValueFactory(new PropertyValueFactory<>("codigoLoja"));
        colNome.setCellValueFactory(new PropertyValueFactory<>("nome"));
        colGrupo.setCellValueFactory(new PropertyValueFactory<>("grupo"));
        colCores.setCellValueFactory(new PropertyValueFactory<>("cor"));
        colEstoque.setCellValueFactory(new PropertyValueFactory<>("estoque"));
        colPrecoVista.setCellValueFactory(new PropertyValueFactory<>("precoVista"));
        colPrecoPrazo.setCellValueFactory(new PropertyValueFactory<>("precoPrazo"));

        // Formatação de Cores
        colCores.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER);
                    if ("ÚNICA".equalsIgnoreCase(item)) {
                        setStyle("-fx-text-fill: #64748B;");
                    } else {
                        setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B;");
                    }
                }
            }
        });

        // Formatação da Coluna de Estoque
        colEstoque.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item + " un.");
                    setAlignment(Pos.CENTER);
                    if (item > 0) {
                        setStyle("-fx-font-weight: bold; -fx-text-fill: #16A34A;");
                    } else {
                        setStyle("-fx-font-weight: bold; -fx-text-fill: #DC2626;");
                    }
                }
            }
        });

        // Formatação de Preço à Vista
        colPrecoVista.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format(Locale.of("pt", "BR"), "R$ %.2f", item));
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #0F172A;");
                    setAlignment(Pos.CENTER_RIGHT);
                }
            }
        });

        // Formatação de Preço a Prazo
        colPrecoPrazo.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format(Locale.of("pt", "BR"), "R$ %.2f", item));
                    setStyle("-fx-text-fill: #64748B;");
                    setAlignment(Pos.CENTER_RIGHT);
                }
            }
        });

        tblProdutos.setItems(itensList);
    }

    private void configurarListeners() {
        cbGrupo.valueProperty().addListener((obs, oldVal, newVal) -> carregarProdutos());
        cbFiltroEstoque.valueProperty().addListener((obs, oldVal, newVal) -> carregarProdutos());
        txtBusca.textProperty().addListener((obs, oldVal, newVal) -> carregarProdutos());
    }

    public void carregarProdutos() {
        if (produtoDAO == null) {
            produtoDAO = new ProdutoDAO();
        }
        if (produtoCorDAO == null) {
            produtoCorDAO = new ProdutoCorDAO();
        }

        String grupo = cbGrupo.getValue();
        String filtroEstoque = cbFiltroEstoque.getValue();
        String busca = txtBusca.getText();

        try {
            // Carrega mapa de cores otimizado em lote
            mapaCores = produtoCorDAO.getAllCoresAgrupadas();

            List<Produto> todos = produtoDAO.findByGrupo(grupo, busca);
            this.produtosBaseCarregados = todos;

            List<ItemLevantamento> linhas = new ArrayList<>();

            for (Produto p : todos) {
                double pv = p.getPrecoVista() != null ? p.getPrecoVista() : 0.0;
                double pp = p.getPrecoPrazo() != null ? p.getPrecoPrazo() : 0.0;
                String cod = p.getCodigoLoja() != null && !p.getCodigoLoja().isEmpty() ? p.getCodigoLoja() : "-";
                String grp = p.getGrupo() != null && !p.getGrupo().isEmpty() ? p.getGrupo() : "GERAL";

                List<ProdutoCor> cores = (mapaCores != null && p.getId() != null) ? mapaCores.get(p.getId()) : null;

                if (cores != null && !cores.isEmpty()) {
                    for (ProdutoCor cor : cores) {
                        int qtdCor = cor.getQuantidade();
                        if ("Apenas com Estoque (> 0)".equals(filtroEstoque) && qtdCor <= 0) continue;
                        if ("Apenas Zerados (= 0)".equals(filtroEstoque) && qtdCor != 0) continue;

                        linhas.add(new ItemLevantamento(
                                p.getId(),
                                cod,
                                p.getNome(),
                                grp,
                                cor.getNomeCor(),
                                qtdCor,
                                pv,
                                pp
                        ));
                    }
                } else {
                    int qtd = p.getQuantidade() != null ? p.getQuantidade() : 0;
                    if ("Apenas com Estoque (> 0)".equals(filtroEstoque) && qtd <= 0) continue;
                    if ("Apenas Zerados (= 0)".equals(filtroEstoque) && qtd != 0) continue;

                    linhas.add(new ItemLevantamento(
                            p.getId(),
                            cod,
                            p.getNome(),
                            grp,
                            "ÚNICA",
                            qtd,
                            pv,
                            pp
                    ));
                }
            }

            itensList.setAll(linhas);

            // Calcula métricas sobre as linhas geradas
            int totalItens = linhas.size();
            int totalQtd = linhas.stream().mapToInt(ItemLevantamento::getEstoque).sum();
            double valorTotal = linhas.stream()
                    .mapToDouble(item -> item.getEstoque() * item.getPrecoVista())
                    .sum();

            lblTotalItens.setText(totalItens + " variações / itens");
            lblTotalUnidades.setText(totalQtd + " un.");
            lblValorTotalEstoque.setText(String.format(Locale.of("pt", "BR"), "R$ %,.2f", valorTotal));

            // Status
            StringBuilder status = new StringBuilder("Exibindo: ");
            if (grupo != null && !grupo.toUpperCase().contains("TODOS") && !grupo.toUpperCase().contains("TODAS")) {
                status.append("Grupo '").append(grupo).append("'");
            } else {
                status.append("Todos os grupos");
            }
            if (filtroEstoque != null && !"Todos os Produtos".equals(filtroEstoque)) {
                status.append(" | ").append(filtroEstoque);
            }
            if (busca != null && !busca.trim().isEmpty()) {
                status.append(" | Busca: '").append(busca.trim()).append("'");
            }
            lblStatusFiltro.setText(status.toString());

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao carregar produtos para levantamento de estoque.", e);
        }
    }

    @FXML
    private void handleLimparFiltros() {
        cbGrupo.setValue("Todos os Grupos / Fabricantes");
        cbFiltroEstoque.setValue("Todos os Produtos");
        txtBusca.clear();
        carregarProdutos();
    }

    @FXML
    private void handleGerarPdf() {
        if (itensList.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Nenhum Item");
            alert.setHeaderText("Lista vazia");
            alert.setContentText("Não há itens selecionados para gerar o relatório de levantamento.");
            alert.showAndWait();
            return;
        }

        try {
            String grupo = cbGrupo.getValue();
            String busca = txtBusca.getText();

            String sufixoGrupo = "";
            if (grupo != null && !grupo.toUpperCase().contains("TODOS") && !grupo.toUpperCase().contains("TODAS")) {
                sufixoGrupo = "_" + grupo.replaceAll("[^a-zA-Z0-9_-]", "_");
            }

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Salvar Levantamento de Estoque & Ficha de Pedido (PDF)");
            String sufixoData = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            fileChooser.setInitialFileName("levantamento_estoque" + sufixoGrupo + "_" + sufixoData + ".pdf");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF (*.pdf)", "*.pdf"));
            fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

            File destino = fileChooser.showSaveDialog(stage != null ? stage : tblProdutos.getScene().getWindow());

            if (destino != null) {
                pdfReportService.gerarRelatorioLevantamentoEstoque(destino, produtosBaseCarregados, mapaCores, grupo, busca);

                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("PDF Gerado com Sucesso");
                confirm.setHeaderText("Relatório de Levantamento gerado!");
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
            LOGGER.log(Level.SEVERE, "Erro ao gerar PDF de levantamento de estoque.", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erro na Geração do PDF");
            alert.setHeaderText("Falha ao gerar o arquivo PDF de levantamento");
            alert.setContentText("Ocorreu um erro:\n" + e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void handleFechar() {
        if (stage != null) {
            stage.close();
        }
    }
}
