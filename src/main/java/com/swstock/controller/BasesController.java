package com.swstock.controller;

import com.swstock.database.BaseDAO;
import com.swstock.database.HistoricoBaseDAO;
import com.swstock.model.Base;
import com.swstock.model.HistoricoBase;
import com.swstock.util.DialogHelper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

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

    @FXML private Button btnTabAutomotivas;
    @FXML private Button btnTabPrediais;
    @FXML private Label lblHeaderSubtitulo;
    @FXML private Label lblTotalBasesExibidas;

    @FXML private TextField txtBusca;
    @FXML private ComboBox<String> cbFabricanteFiltro;

    @FXML private TableView<Base> tblBases;
    @FXML private TableColumn<Base, String> colCodigo;
    @FXML private TableColumn<Base, String> colNome;
    @FXML private TableColumn<Base, String> colTipo;
    @FXML private TableColumn<Base, String> colFabricante;
    @FXML private TableColumn<Base, String> colValorCompra;
    @FXML private TableColumn<Base, Base> colQuantidade;
    @FXML private TableColumn<Base, Void> colAcoes;

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

        configurarTabela();
        configurarFiltros();
        atualizarEstiloTabs();
        carregarFabricantes();
        recarregarDados();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void selecionarTipo(String tipo) {
        if ("PREDIAL".equalsIgnoreCase(tipo)) {
            handleSelecionarPrediais();
        } else {
            handleSelecionarAutomotivas();
        }
    }

    private void configurarTabela() {
        colCodigo.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCodigo()));
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

        tblBases.setItems(basesList);
    }

    private void configurarFiltros() {
        txtBusca.textProperty().addListener((obs, oldVal, newVal) -> recarregarDados());
        cbFabricanteFiltro.valueProperty().addListener((obs, oldVal, newVal) -> recarregarDados());
    }

    @FXML
    public void handleSelecionarAutomotivas() {
        this.tipoAtivo = "AUTOMOTIVA";
        atualizarEstiloTabs();
        carregarFabricantes();
        recarregarDados();
    }

    @FXML
    public void handleSelecionarPrediais() {
        this.tipoAtivo = "PREDIAL";
        atualizarEstiloTabs();
        carregarFabricantes();
        recarregarDados();
    }

    private void atualizarEstiloTabs() {
        if ("AUTOMOTIVA".equalsIgnoreCase(tipoAtivo)) {
            btnTabAutomotivas.setStyle("-fx-background-color: #1565C0; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-padding: 7px 18px; -fx-background-radius: 6px 0 0 6px; -fx-cursor: hand; -fx-font-size: 12px;");
            btnTabPrediais.setStyle("-fx-background-color: transparent; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-padding: 7px 18px; -fx-background-radius: 0 6px 6px 0; -fx-cursor: hand; -fx-font-size: 12px;");
            lblHeaderSubtitulo.setText("Gestão de Bases de Tintas: Linha Automotiva");
        } else {
            btnTabAutomotivas.setStyle("-fx-background-color: transparent; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-padding: 7px 18px; -fx-background-radius: 6px 0 0 6px; -fx-cursor: hand; -fx-font-size: 12px;");
            btnTabPrediais.setStyle("-fx-background-color: #D97706; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-padding: 7px 18px; -fx-background-radius: 0 6px 6px 0; -fx-cursor: hand; -fx-font-size: 12px;");
            lblHeaderSubtitulo.setText("Gestão de Bases de Tintas: Linha Predial");
        }
    }

    private void carregarFabricantes() {
        try {
            List<String> fabs = baseDAO.getAllFabricantes(tipoAtivo);
            ObservableList<String> items = FXCollections.observableArrayList();
            items.add("Todos os Fabricantes");
            items.addAll(fabs);
            cbFabricanteFiltro.setItems(items);
            cbFabricanteFiltro.getSelectionModel().selectFirst();
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

    @FXML
    private void handleNovaBase() {
        Dialog<Base> dialog = new Dialog<>();
        dialog.setTitle("Cadastrar Nova Base");
        dialog.setHeaderText("Cadastrar nova base (" + ("AUTOMOTIVA".equalsIgnoreCase(tipoAtivo) ? "Automotiva" : "Predial") + ")");

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 20, 10, 10));

        TextField txtCod = new TextField();
        txtCod.setPromptText("Ex: B-01 ou 53-100");

        TextField txtNomeBase = new TextField();
        txtNomeBase.setPromptText("Ex: Base Branco Puro ou Amarelo Ouro");

        ComboBox<String> cbTipo = new ComboBox<>(FXCollections.observableArrayList("AUTOMOTIVA", "PREDIAL"));
        cbTipo.setValue(tipoAtivo);

        TextField txtFab = new TextField();
        txtFab.setPromptText("Ex: Coral, Suvinil, Wanda, Lazzuril...");
        txtFab.setText("Geral");

        TextField txtQtd = new TextField("0");
        txtQtd.setPromptText("Quantidade de latas/galões");

        TextField txtPreco = new TextField("0.00");
        txtPreco.setPromptText("Valor de compra (R$)");

        grid.add(new Label("Código da Base:"), 0, 0);
        grid.add(txtCod, 1, 0);
        grid.add(new Label("Nome da Base:"), 0, 1);
        grid.add(txtNomeBase, 1, 1);
        grid.add(new Label("Linha / Tipo:"), 0, 2);
        grid.add(cbTipo, 1, 2);
        grid.add(new Label("Fabricante / Marca:"), 0, 3);
        grid.add(txtFab, 1, 3);
        grid.add(new Label("Quantidade em Estoque:"), 0, 4);
        grid.add(txtQtd, 1, 4);
        grid.add(new Label("Valor de Compra (R$):"), 0, 5);
        grid.add(txtPreco, 1, 5);

        dialogPane.setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                if (txtCod.getText().trim().isEmpty() || txtNomeBase.getText().trim().isEmpty()) {
                    mostrarAlerta(Alert.AlertType.WARNING, "Campos Obrigatórios", "Código e Nome da base são obrigatórios.");
                    return null;
                }
                int qtd = 0;
                try { qtd = Integer.parseInt(txtQtd.getText().trim()); } catch (NumberFormatException ignored) {}
                double preco = 0.0;
                try { preco = Double.parseDouble(txtPreco.getText().trim().replace(",", ".")); } catch (NumberFormatException ignored) {}

                return new Base(
                        txtCod.getText().trim().toUpperCase(),
                        txtNomeBase.getText().trim(),
                        cbTipo.getValue(),
                        txtFab.getText().trim(),
                        qtd,
                        preco
                );
            }
            return null;
        });

        Optional<Base> resultado = dialog.showAndWait();
        resultado.ifPresent(novaBase -> {
            Optional<String> optResp = DialogHelper.solicitarResponsavel(
                    tblBases.getScene().getWindow(),
                    "Responsável pelo Cadastro",
                    "Informe quem está cadastrando a base '" + novaBase.getNome() + "':"
            );
            if (optResp.isEmpty()) return;

            try {
                baseDAO.insert(novaBase, optResp.get());
                carregarFabricantes();
                recarregarDados();
                mostrarAlerta(Alert.AlertType.INFORMATION, "Sucesso", "Base cadastrada com sucesso!");
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Erro ao cadastrar base", e);
                mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Falha ao salvar base: " + e.getMessage());
            }
        });
    }

    private void abrirModalEditarBase(Base base) {
        Dialog<Base> dialog = new Dialog<>();
        dialog.setTitle("Editar Base");
        dialog.setHeaderText("Editar dados e estoque da base: " + base.getNome());

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 20, 10, 10));

        TextField txtCod = new TextField(base.getCodigo());
        TextField txtNomeBase = new TextField(base.getNome());
        ComboBox<String> cbTipo = new ComboBox<>(FXCollections.observableArrayList("AUTOMOTIVA", "PREDIAL"));
        cbTipo.setValue(base.getTipo());
        TextField txtFab = new TextField(base.getFabricante());
        TextField txtQtd = new TextField(String.valueOf(base.getQuantidade()));
        TextField txtPreco = new TextField(String.format("%.2f", base.getValorCompra()).replace(",", "."));

        grid.add(new Label("Código da Base:"), 0, 0);
        grid.add(txtCod, 1, 0);
        grid.add(new Label("Nome da Base:"), 0, 1);
        grid.add(txtNomeBase, 1, 1);
        grid.add(new Label("Linha / Tipo:"), 0, 2);
        grid.add(cbTipo, 1, 2);
        grid.add(new Label("Fabricante / Marca:"), 0, 3);
        grid.add(txtFab, 1, 3);
        grid.add(new Label("Quantidade em Estoque:"), 0, 4);
        grid.add(txtQtd, 1, 4);
        grid.add(new Label("Valor de Compra (R$):"), 0, 5);
        grid.add(txtPreco, 1, 5);

        dialogPane.setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                if (txtCod.getText().trim().isEmpty() || txtNomeBase.getText().trim().isEmpty()) {
                    mostrarAlerta(Alert.AlertType.WARNING, "Campos Obrigatórios", "Código e Nome da base são obrigatórios.");
                    return null;
                }
                int qtd = 0;
                try { qtd = Integer.parseInt(txtQtd.getText().trim()); } catch (NumberFormatException ignored) {}
                double preco = 0.0;
                try { preco = Double.parseDouble(txtPreco.getText().trim().replace(",", ".")); } catch (NumberFormatException ignored) {}

                base.setCodigo(txtCod.getText().trim().toUpperCase());
                base.setNome(txtNomeBase.getText().trim());
                base.setTipo(cbTipo.getValue());
                base.setFabricante(txtFab.getText().trim());
                base.setQuantidade(qtd);
                base.setValorCompra(preco);
                return base;
            }
            return null;
        });

        Optional<Base> resultado = dialog.showAndWait();
        resultado.ifPresent(baseEditada -> {
            Optional<String> optResp = DialogHelper.solicitarResponsavel(
                    tblBases.getScene().getWindow(),
                    "Responsável pela Alteração",
                    "Informe quem está alterando a base '" + baseEditada.getNome() + "':"
            );
            if (optResp.isEmpty()) return;

            try {
                baseDAO.update(baseEditada, optResp.get(), "Edição cadastral e ajuste de estoque da base");
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
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/swstock/view/BasesHistoryView.fxml"));
            Parent root = loader.load();

            BasesHistoryController controller = loader.getController();
            Stage histStage = new Stage();
            histStage.setTitle("Histórico da Base: " + base.getNome());
            histStage.initModality(Modality.APPLICATION_MODAL);
            histStage.initOwner(tblBases.getScene().getWindow());
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
