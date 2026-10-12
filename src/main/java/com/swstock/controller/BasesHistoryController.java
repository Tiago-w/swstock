package com.swstock.controller;

import com.swstock.database.HistoricoBaseDAO;
import com.swstock.model.HistoricoBase;
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
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller para a tela de Histórico Geral (Bases), separando as movimentações
 * das bases de tintas do estoque geral.
 */
public class BasesHistoryController {

    private static final Logger LOGGER = Logger.getLogger(BasesHistoryController.class.getName());
    private final PdfReportService pdfReportService = new PdfReportService();

    @FXML private Button btnResponsavelSessao;
    @FXML private DatePicker dpDataInicio;
    @FXML private DatePicker dpDataFim;
    @FXML private ComboBox<String> cbTipoBase;
    @FXML private ComboBox<String> cbTipoOperacao;
    @FXML private TextField txtBusca;

    @FXML private Label lblTotalEntradas;
    @FXML private Label lblTotalSaidas;
    @FXML private Label lblBalanco;
    @FXML private Label lblTotalRegistros;

    @FXML private TableView<HistoricoBase> tblHistoricoBases;
    @FXML private TableColumn<HistoricoBase, String> colDataHora;
    @FXML private TableColumn<HistoricoBase, String> colCodigo;
    @FXML private TableColumn<HistoricoBase, String> colNome;
    @FXML private TableColumn<HistoricoBase, String> colTipoBase;
    @FXML private TableColumn<HistoricoBase, String> colTipoOperacao;
    @FXML private TableColumn<HistoricoBase, String> colMovimento;
    @FXML private TableColumn<HistoricoBase, String> colSaldo;
    @FXML private TableColumn<HistoricoBase, String> colResponsavel;
    @FXML private TableColumn<HistoricoBase, String> colMotivo;

    private HistoricoBaseDAO historicoBaseDAO;
    private final ObservableList<HistoricoBase> historicoList = FXCollections.observableArrayList();
    private Stage stage;

    @FXML
    public void initialize() {
        if (historicoBaseDAO == null) {
            historicoBaseDAO = new HistoricoBaseDAO();
        }

        configurarBadgeResponsavel();
        com.swstock.util.DeadKeyHelper.attach(txtBusca);
        configurarComboBoxes();
        configurarTabela();
        recarregarDados();
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

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void filtrarPorBase(String codigoBase) {
        if (txtBusca != null && codigoBase != null) {
            txtBusca.setText(codigoBase);
            recarregarDados();
        }
    }

    private void configurarComboBoxes() {
        cbTipoBase.setItems(FXCollections.observableArrayList(
                "Todas as Bases",
                "Bases Automotivas",
                "Bases Prediais"
        ));
        cbTipoBase.setValue("Todas as Bases");
        cbTipoBase.valueProperty().addListener((obs, oldVal, newVal) -> recarregarDados());

        cbTipoOperacao.setItems(FXCollections.observableArrayList(
                "Todas as Operações",
                "Entradas (+)",
                "Saídas (-)",
                "Ajustes"
        ));
        cbTipoOperacao.setValue("Todas as Operações");
        cbTipoOperacao.valueProperty().addListener((obs, oldVal, newVal) -> recarregarDados());

        txtBusca.textProperty().addListener((obs, oldVal, newVal) -> recarregarDados());
    }

    private void configurarTabela() {
        colDataHora.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDataHoraFormatada()));
        colCodigo.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getBaseTamanho() != null ? cell.getValue().getBaseTamanho() : cell.getValue().getBaseCodigo()
        ));
        colNome.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getBaseNome()));

        colTipoBase.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getBaseTipoFormatado()));
        colTipoBase.setCellFactory(column -> new TableCell<>() {
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

        colTipoOperacao.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTipo()));
        colTipoOperacao.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    if ("ENTRADA".equalsIgnoreCase(item)) {
                        badge.getStyleClass().add("badge-stock-ok");
                    } else if ("SAIDA".equalsIgnoreCase(item)) {
                        badge.getStyleClass().add("badge-stock-empty");
                    } else {
                        badge.getStyleClass().add("badge-location");
                    }
                    HBox box = new HBox(badge);
                    box.setAlignment(Pos.CENTER);
                    setGraphic(box);
                    setText(null);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        colMovimento.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getMovimentoFormatado()));
        colMovimento.setCellFactory(column -> new TableCell<>() {
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

        colSaldo.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getSaldoFormatado()));
        colSaldo.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        colResponsavel.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getResponsavel()));
        colResponsavel.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B;");
                    setAlignment(Pos.CENTER_LEFT);
                }
            }
        });

        colMotivo.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getMotivo()));

        tblHistoricoBases.setItems(historicoList);
    }

    @FXML
    public void handleFiltrar() {
        recarregarDados();
    }

    @FXML
    public void handleAtalhoHoje() {
        dpDataInicio.setValue(LocalDate.now());
        dpDataFim.setValue(LocalDate.now());
        recarregarDados();
    }

    @FXML
    public void handleAtalhoUltimos7Dias() {
        dpDataInicio.setValue(LocalDate.now().minusDays(7));
        dpDataFim.setValue(LocalDate.now());
        recarregarDados();
    }

    @FXML
    public void handleAtalhoEsteMes() {
        LocalDate hoje = LocalDate.now();
        dpDataInicio.setValue(hoje.withDayOfMonth(1));
        dpDataFim.setValue(hoje.withDayOfMonth(hoje.lengthOfMonth()));
        recarregarDados();
    }

    @FXML
    public void handleAtalhoTodos() {
        dpDataInicio.setValue(null);
        dpDataFim.setValue(null);
        recarregarDados();
    }

    @FXML
    public void handleLimparFiltros() {
        dpDataInicio.setValue(null);
        dpDataFim.setValue(null);
        cbTipoBase.setValue("Todas as Bases");
        cbTipoOperacao.setValue("Todas as Operações");
        txtBusca.clear();
        recarregarDados();
    }

    private void recarregarDados() {
        LocalDate dtIni = dpDataInicio != null ? dpDataInicio.getValue() : null;
        LocalDate dtFim = dpDataFim != null ? dpDataFim.getValue() : null;

        String tipoBase = null;
        if (cbTipoBase != null && cbTipoBase.getValue() != null) {
            String val = cbTipoBase.getValue();
            if (val.contains("Automotivas")) tipoBase = "AUTOMOTIVA";
            else if (val.contains("Prediais")) tipoBase = "PREDIAL";
        }

        String op = null;
        if (cbTipoOperacao != null && cbTipoOperacao.getValue() != null) {
            String val = cbTipoOperacao.getValue();
            if (val.contains("Entrada")) op = "ENTRADA";
            else if (val.contains("Saída")) op = "SAIDA";
            else if (val.contains("Ajuste")) op = "AJUSTE";
        }

        String termo = txtBusca != null ? txtBusca.getText() : null;

        try {
            List<HistoricoBase> lista = historicoBaseDAO.findAllGlobal(dtIni, dtFim, tipoBase, op, termo);
            historicoList.setAll(lista);

            int totalEntradas = 0;
            int totalSaidas = 0;
            for (HistoricoBase h : lista) {
                if (h.getQuantidadeAlterada() != null) {
                    if (h.getQuantidadeAlterada() > 0) totalEntradas += h.getQuantidadeAlterada();
                    else if (h.getQuantidadeAlterada() < 0) totalSaidas += Math.abs(h.getQuantidadeAlterada());
                }
            }

            lblTotalEntradas.setText("+" + totalEntradas + " un.");
            lblTotalSaidas.setText("-" + totalSaidas + " un.");
            int balanco = totalEntradas - totalSaidas;
            lblBalanco.setText((balanco >= 0 ? "+" : "") + balanco + " un.");
            lblTotalRegistros.setText(lista.size() + " movimentos");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao carregar histórico geral de bases", e);
        }
    }

    @FXML
    public void handleExportarPdf() {
        if (historicoList.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Nenhum Registro");
            alert.setHeaderText("Lista vazia");
            alert.setContentText("Não há movimentações de bases na lista para gerar o relatório.");
            alert.showAndWait();
            return;
        }

        try {
            LocalDate dtIni = dpDataInicio != null ? dpDataInicio.getValue() : null;
            LocalDate dtFim = dpDataFim != null ? dpDataFim.getValue() : null;
            String tipoBase = cbTipoBase != null ? cbTipoBase.getValue() : "Todas as Bases";
            String tipoOp = cbTipoOperacao != null ? cbTipoOperacao.getValue() : "Todas as Operações";
            String termo = txtBusca != null ? txtBusca.getText() : null;

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Salvar Relatório de Histórico das Bases (PDF)");
            String sufixoData = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            fileChooser.setInitialFileName("relatorio_historico_bases_" + sufixoData + ".pdf");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF (*.pdf)", "*.pdf"));
            fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

            File destino = fileChooser.showSaveDialog(stage != null ? stage : tblHistoricoBases.getScene().getWindow());

            if (destino != null) {
                pdfReportService.gerarRelatorioBases(destino, historicoList, dtIni, dtFim, tipoBase, tipoOp, termo);

                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("PDF Gerado com Sucesso");
                confirm.setHeaderText("Relatório de Histórico de Bases gerado!");
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
            LOGGER.log(Level.SEVERE, "Erro ao gerar PDF de histórico de bases.", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erro na Geração do PDF");
            alert.setHeaderText("Falha ao gerar o arquivo PDF de bases");
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
