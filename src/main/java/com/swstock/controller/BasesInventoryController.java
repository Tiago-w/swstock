package com.swstock.controller;

import com.swstock.database.BaseDAO;
import com.swstock.database.DatabaseManager;
import com.swstock.model.Base;
import com.swstock.service.PdfReportService;
import com.swstock.util.TablePreferencesHelper;
import javafx.beans.property.SimpleObjectProperty;
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
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller para o Levantamento de Bases de Tintas (Automotivas & Prediais).
 * Oferece filtros completos, resumo métrico e exportação em PDF para contagem física e pedidos.
 */
public class BasesInventoryController {

    private static final Logger LOGGER = Logger.getLogger(BasesInventoryController.class.getName());

    @FXML private ComboBox<String> cbTipoBase;
    @FXML private ComboBox<String> cbFabricante;
    @FXML private ComboBox<String> cbTamanho;
    @FXML private ComboBox<String> cbFiltroEstoque;
    @FXML private TextField txtBusca;

    @FXML private Label lblTotalItens;
    @FXML private Label lblTotalUnidades;
    @FXML private Label lblTotalValorCompra;

    @FXML private TableView<Base> tblBases;
    @FXML private TableColumn<Base, String> colTamanho;
    @FXML private TableColumn<Base, String> colNome;
    @FXML private TableColumn<Base, String> colTipo;
    @FXML private TableColumn<Base, String> colFabricante;
    @FXML private TableColumn<Base, Integer> colEstoque;
    @FXML private TableColumn<Base, Double> colValorCompra;
    @FXML private TableColumn<Base, Double> colSubtotalCompra;

    @FXML private Label lblStatusFiltro;
    @FXML private Button btnResponsavelSessao;

    private Stage stage;
    private BaseDAO baseDAO;
    private final PdfReportService pdfReportService = new PdfReportService();
    private final ObservableList<Base> basesList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        if (baseDAO == null) {
            baseDAO = new BaseDAO();
        }

        cbTipoBase.setItems(FXCollections.observableArrayList(
                "Todos os Tipos",
                "Automotivas",
                "Prediais"
        ));
        cbTipoBase.setValue("Todos os Tipos");

        cbFiltroEstoque.setItems(FXCollections.observableArrayList(
                "Todas as Bases",
                "Apenas com Estoque (> 0)",
                "Apenas Zeradas (= 0)"
        ));
        cbFiltroEstoque.setValue("Todas as Bases");

        configurarBadgeResponsavel();
        configurarTabela();
        configurarListeners();
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

    public void setBaseDAO(BaseDAO baseDAO) {
        this.baseDAO = baseDAO;
    }

    public void carregarDadosIniciais() {
        carregarOpcoesFabricantes();
        carregarOpcoesTamanhos();
        carregarBases();
    }

    private void carregarOpcoesFabricantes() {
        if (cbFabricante == null) return;
        try {
            String tipoSel = getTipoFiltroDb();
            List<String> fabs = baseDAO.getAllFabricantes(tipoSel);
            ObservableList<String> itens = FXCollections.observableArrayList();
            itens.add("Todos os Fabricantes");
            itens.addAll(fabs);

            String valorAtual = cbFabricante.getValue();
            cbFabricante.setItems(itens);
            if (valorAtual != null && itens.contains(valorAtual)) {
                cbFabricante.setValue(valorAtual);
            } else {
                cbFabricante.setValue("Todos os Fabricantes");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erro ao carregar lista de fabricantes de bases.", e);
        }
    }

    private void carregarOpcoesTamanhos() {
        if (cbTamanho == null) return;
        try {
            String tipoSel = getTipoFiltroDb();
            List<String> tamanhos = baseDAO.getAllTamanhos(tipoSel);
            ObservableList<String> itens = FXCollections.observableArrayList();
            itens.add("Todos os Tamanhos");
            itens.addAll(tamanhos);

            String valorAtual = cbTamanho.getValue();
            cbTamanho.setItems(itens);
            if (valorAtual != null && itens.contains(valorAtual)) {
                cbTamanho.setValue(valorAtual);
            } else {
                cbTamanho.setValue("Todos os Tamanhos");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erro ao carregar lista de tamanhos de bases.", e);
        }
    }

    private String getTipoFiltroDb() {
        if (cbTipoBase == null || cbTipoBase.getValue() == null) return null;
        String v = cbTipoBase.getValue().toUpperCase();
        if (v.contains("AUTOMOTIV")) return "AUTOMOTIVA";
        if (v.contains("PREDIAL")) return "PREDIAL";
        return null;
    }

    public void carregarBases() {
        try {
            String tipo = getTipoFiltroDb();
            String fab = cbFabricante != null ? cbFabricante.getValue() : null;
            String tam = cbTamanho != null ? cbTamanho.getValue() : null;
            String estoqueFiltro = cbFiltroEstoque != null ? cbFiltroEstoque.getValue() : null;
            String busca = txtBusca != null ? txtBusca.getText() : null;

            List<Base> lista = baseDAO.searchLevantamento(tipo, fab, tam, estoqueFiltro, busca);
            basesList.setAll(lista);

            int totalUnidades = lista.stream().mapToInt(b -> b.getQuantidade() != null ? b.getQuantidade() : 0).sum();
            double totalValor = lista.stream().mapToDouble(b -> (b.getQuantidade() != null ? b.getQuantidade() : 0) * (b.getValorCompra() != null ? b.getValorCompra() : 0.0)).sum();

            if (lblTotalItens != null) {
                lblTotalItens.setText(lista.size() + (lista.size() == 1 ? " base" : " bases"));
            }
            if (lblTotalUnidades != null) {
                lblTotalUnidades.setText(totalUnidades + " un.");
            }
            if (lblTotalValorCompra != null) {
                lblTotalValorCompra.setText(String.format(Locale.of("pt", "BR"), "R$ %.2f", totalValor));
            }

            if (lblStatusFiltro != null) {
                String descTipo = (tipo != null) ? (tipo.equalsIgnoreCase("AUTOMOTIVA") ? "Automotivas" : "Prediais") : "Todos os tipos";
                String descFab = (fab != null && !fab.contains("Todos")) ? " | Fab: " + fab : "";
                String descTam = (tam != null && !tam.contains("Todos")) ? " | Tam: " + tam : "";
                lblStatusFiltro.setText("Exibindo: " + descTipo + descFab + descTam + " (" + lista.size() + " bases)");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao carregar bases para levantamento.", e);
        }
    }

    private void configurarTabela() {
        colTamanho.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTamanho()));
        colNome.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getNome()));

        colTipo.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTipoFormatado()));
        colTipo.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    if ("Automotiva".equalsIgnoreCase(item)) {
                        badge.setStyle("-fx-background-color: #DBEAFE; -fx-text-fill: #1D4ED8; -fx-font-weight: bold; -fx-padding: 3px 8px; -fx-background-radius: 12px; -fx-font-size: 11px;");
                    } else {
                        badge.setStyle("-fx-background-color: #FEF3C7; -fx-text-fill: #B45309; -fx-font-weight: bold; -fx-padding: 3px 8px; -fx-background-radius: 12px; -fx-font-size: 11px;");
                    }
                    HBox box = new HBox(badge);
                    box.setAlignment(Pos.CENTER);
                    setGraphic(box);
                    setText(null);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        colFabricante.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getFabricante()));

        colEstoque.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().getQuantidade()));
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

        colValorCompra.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().getValorCompra()));
        colValorCompra.setCellFactory(column -> new TableCell<>() {
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

        colSubtotalCompra.setCellValueFactory(cell -> {
            int q = cell.getValue().getQuantidade() != null ? cell.getValue().getQuantidade() : 0;
            double v = cell.getValue().getValorCompra() != null ? cell.getValue().getValorCompra() : 0.0;
            return new SimpleObjectProperty<>(q * v);
        });
        colSubtotalCompra.setCellFactory(column -> new TableCell<>() {
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

        tblBases.setItems(basesList);

        DatabaseManager dbManager = (baseDAO != null && baseDAO.getDatabaseManager() != null)
                ? baseDAO.getDatabaseManager()
                : DatabaseManager.getInstance();
        TablePreferencesHelper.vincular(tblBases, "inventory_bases", dbManager);
    }

    private boolean isUpdatingFiltros = false;

    private void configurarListeners() {
        cbTipoBase.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (isUpdatingFiltros) return;
            isUpdatingFiltros = true;
            try {
                carregarOpcoesFabricantes();
                carregarOpcoesTamanhos();
            } finally {
                isUpdatingFiltros = false;
            }
            carregarBases();
        });

        cbFabricante.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (isUpdatingFiltros) return;
            carregarBases();
        });

        cbTamanho.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (isUpdatingFiltros) return;
            carregarBases();
        });

        cbFiltroEstoque.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (isUpdatingFiltros) return;
            carregarBases();
        });

        txtBusca.textProperty().addListener((obs, oldVal, newVal) -> carregarBases());
    }

    @FXML
    private void handleLimparFiltros() {
        isUpdatingFiltros = true;
        try {
            cbTipoBase.setValue("Todos os Tipos");
            carregarOpcoesFabricantes();
            carregarOpcoesTamanhos();
            cbFabricante.setValue("Todos os Fabricantes");
            cbTamanho.setValue("Todos os Tamanhos");
            cbFiltroEstoque.setValue("Todas as Bases");
            txtBusca.clear();
        } finally {
            isUpdatingFiltros = false;
        }
        carregarBases();
    }

    @FXML
    private void handleGerarPdf() {
        if (basesList.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Atenção", "Nenhuma base encontrada para exportar o relatório com os filtros atuais.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Salvar Relatório de Levantamento & Pedido de Bases");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF (*.pdf)", "*.pdf"));

        String dataHoje = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        fileChooser.setInitialFileName("Levantamento_Bases_" + dataHoje + ".pdf");

        File destino = fileChooser.showSaveDialog(stage != null ? stage : tblBases.getScene().getWindow());
        if (destino != null) {
            try {
                String tipo = cbTipoBase.getValue();
                String fab = cbFabricante.getValue();
                String tam = cbTamanho.getValue();
                String busca = txtBusca.getText();

                pdfReportService.gerarRelatorioLevantamentoBases(
                        destino,
                        new ArrayList<>(basesList),
                        tipo,
                        fab,
                        tam,
                        busca
                );

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Relatório Gerado com Sucesso");
                alert.setHeaderText("PDF de Levantamento de Bases Criado!");
                alert.setContentText("Arquivo salvo com sucesso em:\n" + destino.getAbsolutePath());
                if (stage != null) alert.initOwner(stage);
                alert.showAndWait();

                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(destino);
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Erro ao gerar PDF de levantamento de bases.", e);
                mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Não foi possível gerar o PDF: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleFechar() {
        if (stage != null) {
            stage.close();
        } else if (tblBases != null && tblBases.getScene() != null && tblBases.getScene().getWindow() != null) {
            ((Stage) tblBases.getScene().getWindow()).close();
        }
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String mensagem) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensagem);
        if (stage != null) alert.initOwner(stage);
        alert.showAndWait();
    }
}
