package com.swstock.controller;

import com.swstock.database.ProdutoCorDAO;
import com.swstock.database.ProdutoDAO;
import com.swstock.model.Produto;
import com.swstock.model.ProdutoCor;
import com.swstock.service.PdfReportService;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
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
 * Controller responsável pelo painel de Configurações e Avaliação Financeira de Valor de Estoque.
 */
public class ConfiguracoesController {

    private static final Logger LOGGER = Logger.getLogger(ConfiguracoesController.class.getName());

    @FXML private ComboBox<String> cbGrupo;
    @FXML private Button btnGeral;
    @FXML private CheckBox chkApenasComEstoque;
    @FXML private TextField txtBusca;

    @FXML private Label lblValorTotalVista;
    @FXML private Label lblValorTotalPrazo;
    @FXML private Label lblTotalItens;
    @FXML private Label lblTotalUnidades;
    @FXML private Label lblStatusFiltro;

    @FXML private TableView<PdfReportService.ItemValorEstoque> tblValorEstoque;
    @FXML private TableColumn<PdfReportService.ItemValorEstoque, String> colCodigo;
    @FXML private TableColumn<PdfReportService.ItemValorEstoque, String> colNome;
    @FXML private TableColumn<PdfReportService.ItemValorEstoque, String> colGrupo;
    @FXML private TableColumn<PdfReportService.ItemValorEstoque, String> colCor;
    @FXML private TableColumn<PdfReportService.ItemValorEstoque, Integer> colEstoque;
    @FXML private TableColumn<PdfReportService.ItemValorEstoque, Double> colPrecoVista;
    @FXML private TableColumn<PdfReportService.ItemValorEstoque, Double> colSubtotalVista;
    @FXML private TableColumn<PdfReportService.ItemValorEstoque, Double> colPrecoPrazo;
    @FXML private TableColumn<PdfReportService.ItemValorEstoque, Double> colSubtotalPrazo;

    private Stage stage;
    private ProdutoDAO produtoDAO;
    private ProdutoCorDAO produtoCorDAO;
    private final PdfReportService pdfReportService = new PdfReportService();

    private final ObservableList<PdfReportService.ItemValorEstoque> itensList = FXCollections.observableArrayList();
    private double totalGeralVista = 0.0;
    private double totalGeralPrazo = 0.0;

    @FXML
    public void initialize() {
        if (produtoDAO == null) {
            produtoDAO = new ProdutoDAO();
        }
        if (produtoCorDAO == null) {
            produtoCorDAO = new ProdutoCorDAO();
        }

        configurarTabela();
        configurarListeners();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setProdutoDAO(ProdutoDAO produtoDAO) {
        this.produtoDAO = produtoDAO;
    }

    public void setProdutoCorDAO(ProdutoCorDAO produtoCorDAO) {
        this.produtoCorDAO = produtoCorDAO;
    }

    public void carregarDados() {
        carregarOpcoesGrupos();
        recalcularValores();
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
            LOGGER.log(Level.WARNING, "Erro ao carregar lista de grupos em configurações.", e);
        }
    }

    private void configurarTabela() {
        colCodigo.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCodigo()));
        colNome.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getNome()));
        colGrupo.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getGrupo()));
        colCor.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCor()));
        colEstoque.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getEstoque()).asObject());
        colPrecoVista.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().getPrecoVista()));
        colSubtotalVista.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().getSubtotalVista()));
        colPrecoPrazo.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().getPrecoPrazo()));
        colSubtotalPrazo.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().getSubtotalPrazo()));

        // Formatação de Cores
        colCor.setCellFactory(column -> new TableCell<>() {
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

        // Formatação de Estoque
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

        // Formatação Preço à Vista Unitário
        colPrecoVista.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format(Locale.of("pt", "BR"), "R$ %.2f", item));
                    setStyle("-fx-text-fill: #334155;");
                    setAlignment(Pos.CENTER_RIGHT);
                }
            }
        });

        // Formatação Subtotal à Vista
        colSubtotalVista.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format(Locale.of("pt", "BR"), "R$ %.2f", item));
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #166534;");
                    setAlignment(Pos.CENTER_RIGHT);
                }
            }
        });

        // Formatação Preço a Prazo Unitário
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

        // Formatação Subtotal a Prazo
        colSubtotalPrazo.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format(Locale.of("pt", "BR"), "R$ %.2f", item));
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #1E40AF;");
                    setAlignment(Pos.CENTER_RIGHT);
                }
            }
        });

        tblValorEstoque.setItems(itensList);
    }

    private void configurarListeners() {
        cbGrupo.valueProperty().addListener((obs, oldVal, newVal) -> recalcularValores());
        chkApenasComEstoque.selectedProperty().addListener((obs, oldVal, newVal) -> recalcularValores());
        txtBusca.textProperty().addListener((obs, oldVal, newVal) -> recalcularValores());
    }

    @FXML
    public void handleSelecionarGeral() {
        cbGrupo.setValue("Todos os Grupos / Fabricantes");
        recalcularValores();
    }

    @FXML
    public void handleLimparFiltros() {
        cbGrupo.setValue("Todos os Grupos / Fabricantes");
        chkApenasComEstoque.setSelected(true);
        txtBusca.clear();
        recalcularValores();
    }

    public void recalcularValores() {
        if (produtoDAO == null) produtoDAO = new ProdutoDAO();
        if (produtoCorDAO == null) produtoCorDAO = new ProdutoCorDAO();

        String grupo = cbGrupo.getValue();
        boolean apenasComEstoque = chkApenasComEstoque.isSelected();
        String busca = txtBusca.getText();

        try {
            Map<Integer, List<ProdutoCor>> mapaCores = produtoCorDAO.getAllCoresAgrupadas();
            List<Produto> produtos = produtoDAO.findByGrupo(grupo, busca);

            List<PdfReportService.ItemValorEstoque> linhas = new ArrayList<>();
            double somaVista = 0.0;
            double somaPrazo = 0.0;
            int totalUnidades = 0;

            for (Produto p : produtos) {
                double pv = p.getPrecoVista() != null ? p.getPrecoVista() : 0.0;
                double pp = p.getPrecoPrazo() != null ? p.getPrecoPrazo() : 0.0;
                String cod = p.getCodigoLoja() != null && !p.getCodigoLoja().isEmpty() ? p.getCodigoLoja() : "-";
                String grp = p.getGrupo() != null && !p.getGrupo().isEmpty() ? p.getGrupo() : "GERAL";

                List<ProdutoCor> cores = (mapaCores != null && p.getId() != null) ? mapaCores.get(p.getId()) : null;

                if (cores != null && !cores.isEmpty()) {
                    for (ProdutoCor cor : cores) {
                        int qtd = cor.getQuantidade();
                        if (apenasComEstoque && qtd <= 0) continue;

                        double subV = qtd * pv;
                        double subP = qtd * pp;
                        somaVista += subV;
                        somaPrazo += subP;
                        totalUnidades += qtd;

                        linhas.add(new PdfReportService.ItemValorEstoque(
                                cod,
                                p.getNome(),
                                grp,
                                cor.getNomeCor(),
                                qtd,
                                pv,
                                subV,
                                pp,
                                subP
                        ));
                    }
                } else {
                    int qtd = p.getQuantidade() != null ? p.getQuantidade() : 0;
                    if (apenasComEstoque && qtd <= 0) continue;

                    double subV = qtd * pv;
                    double subP = qtd * pp;
                    somaVista += subV;
                    somaPrazo += subP;
                    totalUnidades += qtd;

                    linhas.add(new PdfReportService.ItemValorEstoque(
                            cod,
                            p.getNome(),
                            grp,
                            "ÚNICA",
                            qtd,
                            pv,
                            subV,
                            pp,
                            subP
                    ));
                }
            }

            this.totalGeralVista = somaVista;
            this.totalGeralPrazo = somaPrazo;

            itensList.setAll(linhas);

            lblValorTotalVista.setText(String.format(Locale.of("pt", "BR"), "R$ %,.2f", somaVista));
            lblValorTotalPrazo.setText(String.format(Locale.of("pt", "BR"), "R$ %,.2f", somaPrazo));
            lblTotalItens.setText(linhas.size() + " itens listados");
            lblTotalUnidades.setText(totalUnidades + " un.");

            StringBuilder sb = new StringBuilder("Exibindo: ");
            if (grupo != null && !grupo.toUpperCase().contains("TODOS") && !grupo.toUpperCase().contains("TODAS")) {
                sb.append("Grupo '").append(grupo).append("'");
            } else {
                sb.append("Geral (Todos os grupos)");
            }
            if (apenasComEstoque) {
                sb.append(" | Apenas com estoque (> 0)");
            }
            if (busca != null && !busca.trim().isEmpty()) {
                sb.append(" | Busca: '").append(busca.trim()).append("'");
            }
            lblStatusFiltro.setText(sb.toString());

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao calcular valores de estoque.", e);
        }
    }

    @FXML
    public void handleExportarPdf() {
        if (itensList.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Nenhum Item");
            alert.setHeaderText("Lista vazia");
            alert.setContentText("Não há produtos na lista para exportar o relatório de valor de estoque.");
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
            fileChooser.setTitle("Salvar Extrato de Avaliação de Valor de Estoque (PDF)");
            String sufixoData = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            fileChooser.setInitialFileName("extrato_valor_estoque" + sufixoGrupo + "_" + sufixoData + ".pdf");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF (*.pdf)", "*.pdf"));
            fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

            File destino = fileChooser.showSaveDialog(stage != null ? stage : tblValorEstoque.getScene().getWindow());

            if (destino != null) {
                pdfReportService.gerarRelatorioValorEstoque(destino, itensList, grupo, busca, totalGeralVista, totalGeralPrazo);

                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("PDF Gerado com Sucesso");
                confirm.setHeaderText("Extrato Financeiro de Estoque gerado!");
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
            LOGGER.log(Level.SEVERE, "Erro ao gerar PDF de valor de estoque.", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erro na Geração do PDF");
            alert.setHeaderText("Falha ao gerar o extrato de valor de estoque");
            alert.setContentText("Ocorreu um erro:\n" + e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    public void handleFechar() {
        if (stage != null) {
            stage.close();
        }
    }
}
