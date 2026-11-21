package com.swstock.controller;

import com.swstock.database.BaseDAO;
import com.swstock.database.HistoricoBaseDAO;
import com.swstock.model.Base;
import com.swstock.util.DialogHelper;
import javafx.animation.FadeTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller para a tela de Gestão de Bases de Tintas (Bases Automotivas e Bases Prediais).
 */
public class BasesController {

    private static final Logger LOGGER = Logger.getLogger(BasesController.class.getName());

    @FXML private ImageView imgLogoEmpresa;
    @FXML private Button btnTabAutomotivas;
    @FXML private Button btnTabPrediais;
    @FXML private Label lblHeaderSubtitulo;
    @FXML private Label lblTotalBasesExibidas;

    @FXML private TextField txtBusca;
    @FXML private ComboBox<String> cbFabricanteFiltro;

    @FXML private TableView<Base> tblBases;
    @FXML private TableColumn<Base, String> colTamanho;
    @FXML private TableColumn<Base, String> colNome;
    @FXML private TableColumn<Base, String> colTipo;
    @FXML private TableColumn<Base, String> colFabricante;
    @FXML private TableColumn<Base, String> colValorCompra;
    @FXML private TableColumn<Base, Base> colQuantidade;
    @FXML private TableColumn<Base, Void> colAcoes;
    @FXML private Button btnResponsavelSessao;

    private BaseDAO baseDAO;
    private HistoricoBaseDAO historicoBaseDAO;
    private final ObservableList<Base> basesList = FXCollections.observableArrayList();
    private String tipoAtivo = "AUTOMOTIVA"; // "AUTOMOTIVA" ou "PREDIAL"
    private Stage stage;

    @FXML
    public void initialize() {
        if (baseDAO == null) {
            baseDAO = new BaseDAO();
        }
        if (historicoBaseDAO == null) {
            historicoBaseDAO = new HistoricoBaseDAO();
        }

        configurarLogoEmpresa();
        configurarBadgeResponsavel();
        com.swstock.util.DeadKeyHelper.attach(txtBusca);
        configurarTabela();
        configurarFiltros();
        atualizarEstiloTabs();
        carregarFabricantes();
        recarregarDados();
    }

    private void configurarLogoEmpresa() {
        atualizarLogoCabecalho();
        com.swstock.util.LogoManager.getInstance().logoVersionProperty().addListener((obs, oldVal, newVal) -> {
            atualizarLogoCabecalho();
        });
    }

    private void atualizarLogoCabecalho() {
        if (imgLogoEmpresa == null) return;
        Image logo = com.swstock.util.LogoManager.getInstance().getLogoFxImage();
        if (logo != null && !logo.isError()) {
            imgLogoEmpresa.setImage(logo);
            imgLogoEmpresa.setVisible(true);
            imgLogoEmpresa.setManaged(true);
        } else {
            imgLogoEmpresa.setImage(null);
            imgLogoEmpresa.setVisible(false);
            imgLogoEmpresa.setManaged(false);
        }
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
        javafx.stage.Window owner = stage != null ? stage : (tblBases != null && tblBases.getScene() != null ? tblBases.getScene().getWindow() : null);
        com.swstock.util.SessionManager.getInstance().trocarResponsavel(owner);
    }

    private boolean isUpdatingFiltros = false;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setBaseDAO(BaseDAO baseDAO) {
        this.baseDAO = baseDAO;
        if (baseDAO != null && baseDAO.getDatabaseManager() != null) {
            this.historicoBaseDAO = new HistoricoBaseDAO(baseDAO.getDatabaseManager());
        }
    }

    public void selecionarTipo(String tipo) {
        if (tipo != null && tipo.toUpperCase().contains("PREDIA")) {
            handleSelecionarPrediais();
        } else {
            handleSelecionarAutomotivas();
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

        colValorCompra.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getValorCompraFormatado()));
        colValorCompra.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER_RIGHT);
                }
            }
        });

        colQuantidade.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue()));
        colQuantidade.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Base b, boolean empty) {
                super.updateItem(b, empty);
                if (empty || b == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    int qtd = b.getQuantidade();
                    Label badge = new Label(qtd + " un.");
                    if (qtd == 0) {
                        badge.getStyleClass().add("badge-stock-empty");
                    } else if (qtd <= 3) {
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

        colAcoes.setCellFactory(param -> new TableCell<>() {
            private final Button btnEditar = new Button("Editar");
            private final Button btnHistorico = new Button("Histórico");
            private final Button btnExcluir = new Button("Excluir");

            {
                btnEditar.setStyle("-fx-background-color: #EFF6FF; -fx-text-fill: #1D4ED8; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4px 10px; -fx-background-radius: 4px; -fx-cursor: hand;");
                btnEditar.setOnAction(e -> {
                    Base b = getTableView().getItems().get(getIndex());
                    abrirModalEditarBase(b);
                });

                btnHistorico.setStyle("-fx-background-color: #FEF3C7; -fx-text-fill: #92400E; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4px 10px; -fx-background-radius: 4px; -fx-cursor: hand;");
                btnHistorico.setOnAction(e -> {
                    Base b = getTableView().getItems().get(getIndex());
                    abrirHistoricoEspecifico(b);
                });

                btnExcluir.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #DC2626; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4px 10px; -fx-background-radius: 4px; -fx-cursor: hand;");
                btnExcluir.setOnAction(e -> {
                    Base b = getTableView().getItems().get(getIndex());
                    handleExcluirBase(b);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(6, btnEditar, btnHistorico, btnExcluir);
                    box.setAlignment(Pos.CENTER);
                    setGraphic(box);
                }
            }
        });

        tblBases.setRowFactory(tv -> {
            TableRow<Base> row = new TableRow<>();

            ContextMenu contextMenu = new ContextMenu();
            MenuItem itemEditar = new MenuItem("Modificar Base / Estoque");
            itemEditar.setOnAction(e -> {
                if (!row.isEmpty()) {
                    abrirModalEditarBase(row.getItem());
                }
            });
            MenuItem itemHistorico = new MenuItem("Ver Histórico da Base");
            itemHistorico.setOnAction(e -> {
                if (!row.isEmpty()) {
                    abrirHistoricoEspecifico(row.getItem());
                }
            });
            MenuItem itemExcluir = new MenuItem("Excluir Base");
            itemExcluir.setOnAction(e -> {
                if (!row.isEmpty()) {
                    handleExcluirBase(row.getItem());
                }
            });
            contextMenu.getItems().addAll(itemEditar, itemHistorico, new SeparatorMenuItem(), itemExcluir);

            row.contextMenuProperty().bind(
                    Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(contextMenu)
            );

            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2
                        && !row.isEmpty()
                        && !isClickEmBotao(event.getTarget())) {
                    abrirModalEditarBase(row.getItem());
                }
            });

            return row;
        });

        tblBases.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                Base selecionada = tblBases.getSelectionModel().getSelectedItem();
                if (selecionada != null) {
                    abrirModalEditarBase(selecionada);
                    event.consume();
                }
            }
        });

        tblBases.setItems(basesList);

        com.swstock.database.DatabaseManager dbManager = (baseDAO != null && baseDAO.getDatabaseManager() != null)
                ? baseDAO.getDatabaseManager()
                : com.swstock.database.DatabaseManager.getInstance();
        com.swstock.util.TablePreferencesHelper.vincular(tblBases, "bases_tintas", dbManager);
    }

    private boolean isClickEmBotao(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        Node atual = node;
        while (atual != null) {
            if (atual instanceof ButtonBase) {
                return true;
            }
            if (atual instanceof TableRow<?>) {
                return false;
            }
            atual = atual.getParent();
        }
        return false;
    }

    private void configurarFiltros() {
        txtBusca.textProperty().addListener((obs, oldVal, newVal) -> {
            if (isUpdatingFiltros) return;
            recarregarDados();
        });
        cbFabricanteFiltro.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (isUpdatingFiltros) return;
            recarregarDados();
        });
    }

    @FXML
    public void handleSelecionarAutomotivas() {
        this.tipoAtivo = "AUTOMOTIVA";
        atualizarEstiloTabs();
        isUpdatingFiltros = true;
        try {
            carregarFabricantes();
        } finally {
            isUpdatingFiltros = false;
        }
        recarregarDados();
    }

    @FXML
    public void handleSelecionarPrediais() {
        this.tipoAtivo = "PREDIAL";
        atualizarEstiloTabs();
        isUpdatingFiltros = true;
        try {
            carregarFabricantes();
        } finally {
            isUpdatingFiltros = false;
        }
        recarregarDados();
    }

    private void atualizarEstiloTabs() {
        btnTabAutomotivas.getStyleClass().removeAll("bases-seg-btn-active-auto", "bases-seg-btn-active-predial", "bases-seg-btn-inactive");
        btnTabPrediais.getStyleClass().removeAll("bases-seg-btn-active-auto", "bases-seg-btn-active-predial", "bases-seg-btn-inactive");
        btnTabAutomotivas.setStyle("");
        btnTabPrediais.setStyle("");

        if ("AUTOMOTIVA".equalsIgnoreCase(tipoAtivo)) {
            btnTabAutomotivas.getStyleClass().add("bases-seg-btn-active-auto");
            btnTabPrediais.getStyleClass().add("bases-seg-btn-inactive");
            lblHeaderSubtitulo.setText("Gestão de Bases de Tintas: Linha Automotiva");
        } else {
            btnTabAutomotivas.getStyleClass().add("bases-seg-btn-inactive");
            btnTabPrediais.getStyleClass().add("bases-seg-btn-active-predial");
            lblHeaderSubtitulo.setText("Gestão de Bases de Tintas: Linha Predial");
        }
    }

    private void carregarFabricantes() {
        String selecionadoAnterior = cbFabricanteFiltro != null ? cbFabricanteFiltro.getValue() : null;
        try {
            List<String> fabs = baseDAO.getAllFabricantes(tipoAtivo);
            ObservableList<String> items = FXCollections.observableArrayList();
            items.add("Todos os Fabricantes");
            items.addAll(fabs);
            cbFabricanteFiltro.setItems(items);
            if (selecionadoAnterior != null && items.contains(selecionadoAnterior)) {
                cbFabricanteFiltro.setValue(selecionadoAnterior);
            } else {
                cbFabricanteFiltro.getSelectionModel().selectFirst();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erro ao carregar fabricantes de bases", e);
        }
    }

    @FXML
    public void handleRecarregar() {
        recarregarDados();
    }

    private void recarregarDados() {
        String termo = txtBusca.getText();
        String fab = cbFabricanteFiltro.getValue();
        if ("Todos os Fabricantes".equalsIgnoreCase(fab)) {
            fab = null;
        }

        try {
            List<Base> lista = baseDAO.search(tipoAtivo, termo, fab);
            basesList.setAll(lista);
            lblTotalBasesExibidas.setText(lista.size() + ("AUTOMOTIVA".equalsIgnoreCase(tipoAtivo) ? " bases automotivas" : " bases prediais"));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao carregar bases", e);
            mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Falha ao carregar bases: " + e.getMessage());
        }
    }

    private Optional<Base> exibirModalBase(Base baseParaEditar) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/swstock/view/BaseFormModal.fxml"));
            Parent root = loader.load();

            BaseFormController controller = loader.getController();
            Stage modalStage = new Stage();
            com.swstock.MainApp.applyAppIcon(modalStage);
            modalStage.setTitle(baseParaEditar == null
                    ? "SWStock - Cadastrar Nova Base"
                    : "SWStock - Modificar Base: " + baseParaEditar.getNome());
            modalStage.initModality(Modality.APPLICATION_MODAL);
            Window owner = stage != null ? stage : (tblBases != null && tblBases.getScene() != null ? tblBases.getScene().getWindow() : null);
            if (owner != null) {
                modalStage.initOwner(owner);
            }
            modalStage.setResizable(false);

            controller.setStage(modalStage);
            if (baseParaEditar == null) {
                controller.configurarModoCadastro(tipoAtivo);
            } else {
                controller.setOnAbrirHistorico(b -> abrirHistoricoEspecifico(b, modalStage));
                controller.configurarModoEdicao(baseParaEditar);
            }

            Scene scene = new Scene(root);
            modalStage.setScene(scene);
            modalStage.sizeToScene();
            modalStage.centerOnScreen();

            root.setOpacity(0.0);
            FadeTransition fade = new FadeTransition(Duration.millis(180), root);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);
            fade.play();

            modalStage.showAndWait();
            return controller.getResultado();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao abrir modal de base", e);
            mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Falha ao abrir painel de base: " + e.getMessage());
            return Optional.empty();
        }
    }

    @FXML
    private void handleNovaBase() {
        Optional<Base> resultado = exibirModalBase(null);
        resultado.ifPresent(novaBase -> {
            Optional<String> optResp = DialogHelper.solicitarResponsavel(
                    tblBases.getScene().getWindow(),
                    "Responsável pelo Cadastro",
                    "Informe quem está cadastrando a base '" + novaBase.getNome() + "':"
            );
            if (optResp.isEmpty()) return;

            try {
                baseDAO.insert(novaBase, optResp.get());
                isUpdatingFiltros = true;
                try {
                    carregarFabricantes();
                } finally {
                    isUpdatingFiltros = false;
                }
                recarregarDados();
                mostrarAlerta(Alert.AlertType.INFORMATION, "Sucesso", "Base cadastrada com sucesso!");
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Erro ao cadastrar base", e);
                mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Falha ao salvar base: " + e.getMessage());
            }
        });
    }

    private void abrirModalEditarBase(Base base) {
        if (base == null) return;
        Optional<Base> resultado = exibirModalBase(base);
        resultado.ifPresent(baseEditada -> {
            Optional<String> optResp = DialogHelper.solicitarResponsavel(
                    tblBases.getScene().getWindow(),
                    "Responsável pela Alteração",
                    "Informe quem está alterando a base '" + baseEditada.getNome() + "':"
            );
            if (optResp.isEmpty()) return;

            try {
                baseDAO.update(baseEditada, optResp.get(), "Edição cadastral e ajuste de estoque da base");
                isUpdatingFiltros = true;
                try {
                    carregarFabricantes();
                } finally {
                    isUpdatingFiltros = false;
                }
                recarregarDados();
                mostrarAlerta(Alert.AlertType.INFORMATION, "Sucesso", "Base atualizada com sucesso!");
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Erro ao atualizar base", e);
                mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Falha ao salvar base: " + e.getMessage());
            }
        });
    }

    private void handleExcluirBase(Base base) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Excluir Base");
        confirm.setHeaderText("Deseja realmente excluir a base '" + base.getNome() + "'?");
        confirm.setContentText("Esta ação é definitiva e removerá a base do catálogo.");

        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            Optional<String> optResp = DialogHelper.solicitarResponsavel(
                    tblBases.getScene().getWindow(),
                    "Responsável pela Exclusão",
                    "Informe quem está autorizando a exclusão da base '" + base.getNome() + "':"
            );
            if (optResp.isEmpty()) return;

            try {
                baseDAO.delete(base.getId(), optResp.get());
                recarregarDados();
                mostrarAlerta(Alert.AlertType.INFORMATION, "Sucesso", "Base excluída com sucesso!");
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Erro ao excluir base", e);
                mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Falha ao excluir base: " + e.getMessage());
            }
        }
    }

    private void abrirHistoricoEspecifico(Base base) {
        abrirHistoricoEspecifico(base, null);
    }

    private void abrirHistoricoEspecifico(Base base, Window ownerOverride) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/swstock/view/BasesHistoryView.fxml"));
            Parent root = loader.load();

            BasesHistoryController controller = loader.getController();
            Stage histStage = new Stage();
            com.swstock.MainApp.applyAppIcon(histStage);
            histStage.setTitle("Histórico da Base: " + base.getNome());
            histStage.initModality(Modality.APPLICATION_MODAL);
            Window owner = ownerOverride != null ? ownerOverride : (tblBases != null && tblBases.getScene() != null ? tblBases.getScene().getWindow() : stage);
            if (owner != null) {
                histStage.initOwner(owner);
            }
            histStage.setScene(new Scene(root));

            controller.setStage(histStage);
            controller.filtrarPorBase(base.getCodigo());

            histStage.showAndWait();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao abrir histórico específico da base", e);
            mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Falha ao abrir histórico da base: " + e.getMessage());
        }
    }

    @FXML
    public void handleAbrirHistoricoBases() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/swstock/view/BasesHistoryView.fxml"));
            Parent root = loader.load();

            BasesHistoryController controller = loader.getController();
            Stage histStage = new Stage();
            com.swstock.MainApp.applyAppIcon(histStage);
            histStage.setTitle("Histórico Geral (Bases Automotivas & Prediais)");
            histStage.initModality(Modality.APPLICATION_MODAL);
            histStage.initOwner(tblBases.getScene().getWindow());
            histStage.setScene(new Scene(root));

            controller.setStage(histStage);
            histStage.showAndWait();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao abrir histórico geral de bases", e);
            mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Falha ao abrir histórico de bases: " + e.getMessage());
        }
    }

    @FXML
    public void handleFechar() {
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
