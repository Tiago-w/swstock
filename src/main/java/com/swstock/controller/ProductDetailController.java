package com.swstock.controller;

import com.swstock.database.HistoricoEstoqueDAO;
import com.swstock.database.ProdutoCorDAO;
import com.swstock.database.ProdutoDAO;
import com.swstock.model.HistoricoEstoque;
import com.swstock.model.Produto;
import com.swstock.model.ProdutoCor;
import com.swstock.service.ProductEnrichmentService;
import com.swstock.util.DialogHelper;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador para o modal de detalhes, edição, variações de cores e histórico de estoque do produto.
 */
public class ProductDetailController {

    private static final Logger LOGGER = Logger.getLogger(ProductDetailController.class.getName());
    private static final DateTimeFormatter DATE_DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private Label lblModalNome;
    @FXML private Label lblModalCodigo;
    @FXML private Label lblAutoSaveStatus;

    @FXML private TabPane tabPanePrincipal;
    @FXML private TextField txtQuantidade;
    @FXML private Button btnDecrement;
    @FXML private Button btnIncrement;
    @FXML private Label lblEstoqueInicial;
    @FXML private Label lblQuantidadeDesc;

    @FXML private ComboBox<String> cbCorAtual;
    @FXML private Button btnAdicionarCor;
    @FXML private Button btnVerCores;

    @FXML private HBox boxLoadingWeb;
    @FXML private Label lblLoadingWebText;

    @FXML private TextField txtNome;
    @FXML private TextField txtCodigoLoja;
    @FXML private TextField txtGrupo;
    @FXML private TextField txtPrecoVista;
    @FXML private TextField txtPrecoPrazo;
    @FXML private TextField txtLocalizacao;
    @FXML private Button btnVerLocalMapa;
    @FXML private TextField txtUrlImagem;

    @FXML private ImageView imgProduto;
    @FXML private Label lblFotoPlaceholder;
    @FXML private HBox boxImageNav;
    @FXML private Label lblFotoCounter;
    @FXML private TextArea txtDescricaoBreve;
    @FXML private Button btnBuscarWebNome;

    // Componentes da Aba de Histórico de Movimentações & Calendário
    @FXML private DatePicker dpFiltroData;
    @FXML private Label lblFiltroHistoricoStatus;
    @FXML private Label lblTotalAdicionado;
    @FXML private Label lblTotalSubtraido;
    @FXML private Label lblSaldoAtualHistorico;

    @FXML private TableView<HistoricoEstoque> tblHistorico;
    @FXML private TableColumn<HistoricoEstoque, String> colDataHora;
    @FXML private TableColumn<HistoricoEstoque, String> colTipo;
    @FXML private TableColumn<HistoricoEstoque, String> colQtdAlterada;
    @FXML private TableColumn<HistoricoEstoque, String> colSaldo;
    @FXML private TableColumn<HistoricoEstoque, String> colResponsavel;
    @FXML private TableColumn<HistoricoEstoque, String> colMotivo;

    private Produto produto;
    private ProdutoDAO produtoDAO;
    private HistoricoEstoqueDAO historicoEstoqueDAO;
    private ProdutoCorDAO produtoCorDAO = new ProdutoCorDAO();
    private final ObservableList<HistoricoEstoque> historicoList = FXCollections.observableArrayList();
    private final ObservableList<ProdutoCor> coresDoProduto = FXCollections.observableArrayList();
    private ProdutoCor corAtiva = null;

    private Runnable onUpdateCallback;
    private java.util.function.Consumer<String> onOpenMapCallback;
    private Stage stage;
    private int estoqueInicial = 0;

    private final ProductEnrichmentService enrichmentService = new ProductEnrichmentService();
    private List<String> imagensEncontradas = new ArrayList<>();
    private int indiceImagemAtual = 0;
    private boolean isUpdatingProgrammatically = false;

    @FXML
    public void initialize() {
        txtUrlImagem.textProperty().addListener((obs, oldVal, newVal) -> {
            carregarImagem(newVal);
        });

        configurarCampoQuantidade();
        configurarTabelaHistorico();
        configurarSeletorCores();
    }

    private void configurarSeletorCores() {
        if (cbCorAtual != null) {
            cbCorAtual.getSelectionModel().selectedIndexProperty().addListener((obs, oldVal, newVal) -> {
                if (isUpdatingProgrammatically || newVal == null) return;
                int idx = newVal.intValue();
                if (idx <= 0 || idx > coresDoProduto.size()) {
                    selecionarCorAtiva(null);
                } else {
                    selecionarCorAtiva(coresDoProduto.get(idx - 1));
                }
            });
        }
    }

    private void configurarTabelaHistorico() {
        colDataHora.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getDataHoraFormatada()));
        colTipo.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getTipo()));
        colQtdAlterada.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getMovimentoFormatado()));
        colSaldo.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getSaldoFormatado()));
        colResponsavel.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getResponsavel()));
        colMotivo.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getMotivo() != null ? cellData.getValue().getMotivo() : "-"));

        // Badge estilizado para Tipo
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

        // Destaque colorido para a quantidade movimentada
        colQtdAlterada.setCellFactory(column -> new TableCell<>() {
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

        // Responsável com destaque em negrito
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

        tblHistorico.setItems(historicoList);
    }

    private void configurarCampoQuantidade() {
        txtQuantidade.textProperty().addListener((observable, oldValue, newValue) -> {
            if (isUpdatingProgrammatically) return;

            if (!newValue.matches("\\d*")) {
                txtQuantidade.setText(newValue.replaceAll("[^\\d]", ""));
                return;
            }

            if (!newValue.isEmpty()) {
                try {
                    int novaQtd = Integer.parseInt(newValue);
                    if (corAtiva != null) {
                        corAtiva.setQuantidade(novaQtd);
                        lblAutoSaveStatus.setText("Alteração pendente de salvamento (" + corAtiva.getNomeCor() + ")");
                        lblAutoSaveStatus.setStyle("-fx-text-fill: #D97706; -fx-font-weight: bold; -fx-font-size: 11px;");
                    } else {
                        produto.setQuantidade(novaQtd);
                        if (novaQtd != estoqueInicial) {
                            lblAutoSaveStatus.setText("Alteração pendente de salvamento");
                            lblAutoSaveStatus.setStyle("-fx-text-fill: #D97706; -fx-font-weight: bold; -fx-font-size: 11px;");
                        } else {
                            atualizarStatusSincronizado();
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        txtQuantidade.focusedProperty().addListener((obs, oldVal, hasFocus) -> {
            if (!hasFocus) {
                if (txtQuantidade.getText() == null || txtQuantidade.getText().trim().isEmpty()) {
                    setQuantidadeDisplay(0);
                    if (corAtiva != null) {
                        corAtiva.setQuantidade(0);
                    } else {
                        produto.setQuantidade(0);
                    }
                }
            }
        });
    }

    public void setDialogStage(Stage stage) {
        this.stage = stage;
    }

    public void setProdutoDAO(ProdutoDAO produtoDAO) {
        this.produtoDAO = produtoDAO;
        this.historicoEstoqueDAO = new HistoricoEstoqueDAO();
        this.produtoCorDAO = new ProdutoCorDAO();
    }

    public void setOnUpdateCallback(Runnable onUpdateCallback) {
        this.onUpdateCallback = onUpdateCallback;
    }

    public void setOnOpenMapCallback(java.util.function.Consumer<String> onOpenMapCallback) {
        this.onOpenMapCallback = onOpenMapCallback;
    }

    /**
     * Inicializa o modal com os dados do produto especificado.
     */
    public void setProduto(Produto produto) {
        this.produto = produto;
        if (this.produtoDAO == null) {
            this.produtoDAO = new ProdutoDAO();
        }
        if (this.historicoEstoqueDAO == null) {
            this.historicoEstoqueDAO = new HistoricoEstoqueDAO();
        }
        if (this.produtoCorDAO == null) {
            this.produtoCorDAO = new ProdutoCorDAO();
        }

        if (produto == null) {
            this.produto = new Produto();
            this.estoqueInicial = 0;
            lblModalNome.setText("Novo Produto");
            lblModalCodigo.setText("CÓDIGO: [Pendente de Cadastro]");
            setQuantidadeDisplay(0);
            if (lblEstoqueInicial != null) {
                lblEstoqueInicial.setText("0 un.");
            }
            lblAutoSaveStatus.setText("Modo Criação");
            lblAutoSaveStatus.setStyle("-fx-text-fill: #2563EB; -fx-font-weight: bold; -fx-font-size: 11px;");
            txtPrecoVista.setText("0.00");
            txtPrecoPrazo.setText("0.00");
            txtCodigoLoja.setText("SKU-" + (int)(Math.random() * 900000 + 100000));
            if (txtGrupo != null) txtGrupo.setText("GERAL");
            carregarHistorico();
            carregarCoresProduto();
        } else {
            this.estoqueInicial = produto.getQuantidade() != null ? produto.getQuantidade() : 0;
            lblModalNome.setText(produto.getNome() != null ? produto.getNome() : "Sem Nome");
            lblModalCodigo.setText("CÓDIGO: #" + (produto.getCodigoLoja() != null ? produto.getCodigoLoja() : "N/A"));
            setQuantidadeDisplay(this.estoqueInicial);
            if (lblEstoqueInicial != null) {
                lblEstoqueInicial.setText(this.estoqueInicial + " un.");
            }

            txtNome.setText(produto.getNome());
            txtCodigoLoja.setText(produto.getCodigoLoja());
            if (txtGrupo != null) {
                txtGrupo.setText(produto.getGrupo() != null ? produto.getGrupo() : "GERAL");
            }
            txtPrecoVista.setText(produto.getPrecoVista() != null ? String.format("%.2f", produto.getPrecoVista()) : "0.00");
            txtPrecoPrazo.setText(produto.getPrecoPrazo() != null ? String.format("%.2f", produto.getPrecoPrazo()) : "0.00");
            txtLocalizacao.setText(produto.getLocalizacao());
            txtUrlImagem.setText(produto.getUrlImagem());
            txtDescricaoBreve.setText(produto.getDescricaoBreve());

            carregarImagem(produto.getUrlImagem());
            atualizarStatusSincronizado();
            carregarHistorico();
            carregarCoresProduto();
        }
    }

    public void carregarCoresProduto() {
        if (cbCorAtual == null) return;
        isUpdatingProgrammatically = true;
        coresDoProduto.clear();
        List<String> items = new ArrayList<>();
        items.add("Nenhuma Cor (Estoque Geral)");

        if (produto != null && produto.getId() != null) {
            try {
                List<ProdutoCor> cores = produtoCorDAO.findByProdutoId(produto.getId());
                coresDoProduto.addAll(cores);
                for (ProdutoCor cor : cores) {
                    items.add(cor.getNomeCor() + " (" + cor.getQuantidade() + " un.)");
                }
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Erro ao buscar cores do produto.", e);
            }
        }

        cbCorAtual.setItems(FXCollections.observableArrayList(items));

        if (corAtiva != null) {
            int idx = -1;
            for (int i = 0; i < coresDoProduto.size(); i++) {
                if (coresDoProduto.get(i).getId().equals(corAtiva.getId())) {
                    idx = i + 1;
                    corAtiva = coresDoProduto.get(i);
                    break;
                }
            }
            if (idx > 0) {
                cbCorAtual.getSelectionModel().select(idx);
                if (lblQuantidadeDesc != null) lblQuantidadeDesc.setText("ESTOQUE COR: " + corAtiva.getNomeCor());
                setQuantidadeDisplay(corAtiva.getQuantidade());
                if (lblEstoqueInicial != null) lblEstoqueInicial.setText(corAtiva.getQuantidade() + " un.");
            } else {
                corAtiva = null;
                cbCorAtual.getSelectionModel().select(0);
                if (lblQuantidadeDesc != null) lblQuantidadeDesc.setText("UNIDADES EM ESTOQUE");
                setQuantidadeDisplay(produto != null && produto.getQuantidade() != null ? produto.getQuantidade() : 0);
                if (lblEstoqueInicial != null) lblEstoqueInicial.setText((produto != null && produto.getQuantidade() != null ? produto.getQuantidade() : 0) + " un.");
            }
        } else {
            cbCorAtual.getSelectionModel().select(0);
            if (lblQuantidadeDesc != null) lblQuantidadeDesc.setText("UNIDADES EM ESTOQUE");
            setQuantidadeDisplay(produto != null && produto.getQuantidade() != null ? produto.getQuantidade() : 0);
            if (lblEstoqueInicial != null) lblEstoqueInicial.setText((produto != null && produto.getQuantidade() != null ? produto.getQuantidade() : 0) + " un.");
        }
        isUpdatingProgrammatically = false;
    }

    public void selecionarCorAtiva(ProdutoCor cor) {
        this.corAtiva = cor;
        if (cor != null) {
            if (lblQuantidadeDesc != null) lblQuantidadeDesc.setText("ESTOQUE COR: " + cor.getNomeCor());
            setQuantidadeDisplay(cor.getQuantidade());
            if (lblEstoqueInicial != null) lblEstoqueInicial.setText(cor.getQuantidade() + " un.");
        } else {
            if (lblQuantidadeDesc != null) lblQuantidadeDesc.setText("UNIDADES EM ESTOQUE");
            int q = produto != null && produto.getQuantidade() != null ? produto.getQuantidade() : 0;
            setQuantidadeDisplay(q);
            if (lblEstoqueInicial != null) lblEstoqueInicial.setText(q + " un.");
        }
    }

    @FXML
    private void handleAbrirAdicionarCor() {
        if (produto == null || produto.getId() == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Salvar Primeiro", "Salve o cadastro básico do produto antes de adicionar cores.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Adicionar Cor");
        dialog.setHeaderText("Cadastrar nova cor para o produto:\n" + produto.getNome());
        dialog.setContentText("Nome da cor (ex: Azul, Vermelho, Preto Fosco):");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().trim().isEmpty()) {
            String nomeCor = result.get().trim().toUpperCase();

            // Pede o responsável pela alteração
            Optional<String> optResp = DialogHelper.solicitarResponsavel(
                    tabPanePrincipal.getScene().getWindow(),
                    "Responsável pelo Cadastro da Cor",
                    "Informe quem está cadastrando a cor '" + nomeCor + "':"
            );
            if (optResp.isEmpty()) {
                return;
            }
            String responsavel = optResp.get();

            try {
                boolean ok = produtoCorDAO.addCor(produto.getId(), nomeCor, 0, responsavel);
                if (ok) {
                    List<ProdutoCor> cores = produtoCorDAO.findByProdutoId(produto.getId());
                    for (ProdutoCor c : cores) {
                        if (c.getNomeCor().equalsIgnoreCase(nomeCor)) {
                            this.corAtiva = c;
                            break;
                        }
                    }
                    carregarCoresProduto();
                    carregarHistorico();
                    notificarAtualizacao();
                    mostrarAlerta(Alert.AlertType.INFORMATION, "Sucesso", "Cor '" + nomeCor + "' cadastrada com sucesso!");
                }
            } catch (SQLException e) {
                if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                    mostrarAlerta(Alert.AlertType.WARNING, "Cor Duplicada", "Esta cor já está cadastrada para este produto.");
                } else {
                    LOGGER.log(Level.SEVERE, "Erro ao adicionar cor.", e);
                    mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Falha ao cadastrar cor: " + e.getMessage());
                }
            }
        }
    }

    @FXML
    private void handleAbrirGerenciarCores() {
        if (produto == null || produto.getId() == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Salvar Primeiro", "Salve o cadastro básico do produto antes de gerenciar cores.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/swstock/view/ProductColorsModal.fxml"));
            Parent root = loader.load();

            ProductColorsController controller = loader.getController();
            Stage colorStage = new Stage();
            colorStage.setTitle("Cores do Produto: " + produto.getNome());
            colorStage.initModality(Modality.APPLICATION_MODAL);
            colorStage.initOwner(tabPanePrincipal.getScene().getWindow());
            colorStage.setScene(new Scene(root));

            controller.setDialogStage(colorStage);
            controller.setProdutoCorDAO(produtoCorDAO);
            controller.setProdutoDAO(produtoDAO);
            controller.setProduto(produto);

            controller.setOnCorSelecionadaCallback(cor -> {
                this.corAtiva = cor;
                carregarCoresProduto();
            });

            controller.setOnModificacaoCallback(() -> {
                carregarCoresProduto();
                carregarHistorico();
                notificarAtualizacao();
            });

            colorStage.showAndWait();
            carregarCoresProduto();
            carregarHistorico();
            notificarAtualizacao();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao abrir modal de cores.", e);
            mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Não foi possível abrir o gerenciamento de cores: " + e.getMessage());
        }
    }

    /**
     * Carrega os registros de movimentação de estoque e atualiza os cartões de métricas.
     */
    public void carregarHistorico() {
        if (produto == null || produto.getId() == null) {
            historicoList.clear();
            if (lblTotalAdicionado != null) lblTotalAdicionado.setText("+0 un.");
            if (lblTotalSubtraido != null) lblTotalSubtraido.setText("-0 un.");
            if (lblSaldoAtualHistorico != null) lblSaldoAtualHistorico.setText("0 un.");
            if (lblFiltroHistoricoStatus != null) lblFiltroHistoricoStatus.setText("Produto não cadastrado ainda.");
            return;
        }

        try {
            LocalDate dataFiltro = dpFiltroData != null ? dpFiltroData.getValue() : null;
            List<HistoricoEstoque> lista;

            if (dataFiltro != null) {
                lista = historicoEstoqueDAO.findByProdutoAndDate(produto.getId(), dataFiltro);
                if (lblFiltroHistoricoStatus != null) {
                    lblFiltroHistoricoStatus.setText("Exibindo: " + dataFiltro.format(DATE_DISPLAY_FORMATTER) + " (" + lista.size() + " movimentos)");
                }
            } else {
                lista = historicoEstoqueDAO.findByProduto(produto.getId());
                if (lblFiltroHistoricoStatus != null) {
                    lblFiltroHistoricoStatus.setText("Exibindo: Todos os Registros (" + lista.size() + " movimentos)");
                }
            }

            historicoList.setAll(lista);

            int totalAdd = historicoEstoqueDAO.getTotalAdicionado(produto.getId());
            int totalSub = historicoEstoqueDAO.getTotalSubtraido(produto.getId());
            int saldo = produto.getQuantidade() != null ? produto.getQuantidade() : 0;

            if (lblTotalAdicionado != null) lblTotalAdicionado.setText("+" + totalAdd + " un.");
            if (lblTotalSubtraido != null) lblTotalSubtraido.setText("-" + totalSub + " un.");
            if (lblSaldoAtualHistorico != null) lblSaldoAtualHistorico.setText(saldo + " un.");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao carregar histórico de movimentações de estoque.", e);
        }
    }

    @FXML
    private void handleFiltrarData() {
        carregarHistorico();
    }

    @FXML
    private void handleFiltrarHoje() {
        if (dpFiltroData != null) {
            dpFiltroData.setValue(LocalDate.now());
        }
        carregarHistorico();
    }

    @FXML
    private void handleLimparFiltroData() {
        if (dpFiltroData != null) {
            dpFiltroData.setValue(null);
        }
        carregarHistorico();
    }

    @FXML
    private void handleVerLocalMapa() {
        String loc = txtLocalizacao.getText();
        if (loc == null || loc.trim().isEmpty()) {
            mostrarAlerta(Alert.AlertType.INFORMATION, "Localização Não Cadastrada",
                    "Este produto ainda não possui localização definida.\nDigite uma estante (ex: 'Estante A1') para visualizá-la no mapa.");
            txtLocalizacao.requestFocus();
            return;
        }

        if (onOpenMapCallback != null) {
            onOpenMapCallback.accept(loc.trim());
        }
    }

    private void setQuantidadeDisplay(int qtd) {
        isUpdatingProgrammatically = true;
        txtQuantidade.setText(String.valueOf(qtd));
        isUpdatingProgrammatically = false;
    }

    @FXML
    private void handleBuscarNaWeb() {
        String termo = txtNome.getText();
        if (termo == null || termo.trim().isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Aviso", "Digite o nome do produto antes de buscar na internet.");
            txtNome.requestFocus();
            return;
        }

        exibirLoadingWeb(true, "Buscando fotos e dados na internet para: '" + termo.trim() + "'...");

        CompletableFuture.supplyAsync(() -> enrichmentService.buscarEEnriquecer(termo))
                .thenAccept(resultado -> Platform.runLater(() -> {
                    exibirLoadingWeb(false, "");

                    if (!resultado.success()) {
                        mostrarAlerta(Alert.AlertType.WARNING, "Busca Web",
                                "Não foi possível localizar dados na internet para este termo.\n" + resultado.statusMessage());
                        return;
                    }

                    if (resultado.generatedDescription() != null && !resultado.generatedDescription().isEmpty()) {
                        txtDescricaoBreve.setText(resultado.generatedDescription());
                    }

                    imagensEncontradas = resultado.candidateImageUrls();
                    if (!imagensEncontradas.isEmpty()) {
                        indiceImagemAtual = 0;
                        txtUrlImagem.setText(imagensEncontradas.get(0));
                        carregarImagem(imagensEncontradas.get(0));

                        if (imagensEncontradas.size() > 1) {
                            boxImageNav.setVisible(true);
                            boxImageNav.setManaged(true);
                            atualizarContadorFotos();
                        } else {
                            boxImageNav.setVisible(false);
                            boxImageNav.setManaged(false);
                        }
                    } else {
                        boxImageNav.setVisible(false);
                        boxImageNav.setManaged(false);
                    }

                    lblAutoSaveStatus.setText("Dados preenchidos pela busca web");
                    lblAutoSaveStatus.setStyle("-fx-text-fill: #16A34A; -fx-font-weight: bold; -fx-font-size: 11px;");
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        exibirLoadingWeb(false, "");
                        LOGGER.log(Level.SEVERE, "Erro inesperado na busca web.", ex);
                        mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Erro ao executar busca na web: " + ex.getMessage());
                    });
                    return null;
                });
    }

    @FXML
    private void handleFotoAnterior() {
        if (imagensEncontradas != null && !imagensEncontradas.isEmpty()) {
            indiceImagemAtual--;
            if (indiceImagemAtual < 0) {
                indiceImagemAtual = imagensEncontradas.size() - 1;
            }
            String url = imagensEncontradas.get(indiceImagemAtual);
            txtUrlImagem.setText(url);
            carregarImagem(url);
            atualizarContadorFotos();
        }
    }

    @FXML
    private void handleFotoProxima() {
        if (imagensEncontradas != null && !imagensEncontradas.isEmpty()) {
            indiceImagemAtual++;
            if (indiceImagemAtual >= imagensEncontradas.size()) {
                indiceImagemAtual = 0;
            }
            String url = imagensEncontradas.get(indiceImagemAtual);
            txtUrlImagem.setText(url);
            carregarImagem(url);
            atualizarContadorFotos();
        }
    }

    private void atualizarContadorFotos() {
        lblFotoCounter.setText((indiceImagemAtual + 1) + " / " + imagensEncontradas.size());
    }

    private void exibirLoadingWeb(boolean exibindo, String mensagem) {
        boxLoadingWeb.setVisible(exibindo);
        boxLoadingWeb.setManaged(exibindo);
        lblLoadingWebText.setText(mensagem);
        if (btnBuscarWebNome != null) {
            btnBuscarWebNome.setDisable(exibindo);
        }
    }

    @FXML
    private void handleIncrement() {
        int atual = parseQuantidadeAtual();
        int novo = atual + 1;
        setQuantidadeDisplay(novo);
        if (corAtiva != null) {
            corAtiva.setQuantidade(novo);
            lblAutoSaveStatus.setText("Alteração pendente de salvamento (" + corAtiva.getNomeCor() + ")");
            lblAutoSaveStatus.setStyle("-fx-text-fill: #D97706; -fx-font-weight: bold; -fx-font-size: 11px;");
        } else {
            produto.setQuantidade(novo);
            lblAutoSaveStatus.setText("Alteração pendente de salvamento");
            lblAutoSaveStatus.setStyle("-fx-text-fill: #D97706; -fx-font-weight: bold; -fx-font-size: 11px;");
        }
    }

    @FXML
    private void handleDecrement() {
        int atual = parseQuantidadeAtual();
        if (atual > 0) {
            int novo = atual - 1;
            setQuantidadeDisplay(novo);
            if (corAtiva != null) {
                corAtiva.setQuantidade(novo);
                lblAutoSaveStatus.setText("Alteração pendente de salvamento (" + corAtiva.getNomeCor() + ")");
                lblAutoSaveStatus.setStyle("-fx-text-fill: #D97706; -fx-font-weight: bold; -fx-font-size: 11px;");
            } else {
                produto.setQuantidade(novo);
                lblAutoSaveStatus.setText("Alteração pendente de salvamento");
                lblAutoSaveStatus.setStyle("-fx-text-fill: #D97706; -fx-font-weight: bold; -fx-font-size: 11px;");
            }
        }
    }

    private int parseQuantidadeAtual() {
        try {
            String txt = txtQuantidade.getText();
            return (txt == null || txt.trim().isEmpty()) ? 0 : Integer.parseInt(txt.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void atualizarStatusSincronizado() {
        lblAutoSaveStatus.setText("Sincronizado com SQLite");
        lblAutoSaveStatus.setStyle("-fx-text-fill: #16A34A; -fx-font-weight: bold; -fx-font-size: 11px;");
    }

    @FXML
    private void handleSalvarAlteracoes() {
        if (!validarFormulario()) {
            return;
        }

        // QUALQUER SALVAMENTO PEDE QUEM ALTEROU
        Optional<String> optResp = DialogHelper.solicitarResponsavel(
                tabPanePrincipal.getScene().getWindow(),
                "Confirmação de Responsável",
                "Informe o funcionário responsável por salvar as alterações deste produto:"
        );
        if (optResp.isEmpty()) {
            return; // Salvamento abortado
        }
        String responsavel = optResp.get();

        int qtdFinal = parseQuantidadeAtual();

        // Se estiver editando uma cor específica
        if (corAtiva != null) {
            try {
                produtoCorDAO.updateQuantidade(corAtiva.getId(), qtdFinal, "Modificação de estoque [" + corAtiva.getNomeCor() + "]", responsavel);
                corAtiva.setQuantidade(qtdFinal);

                // Recalcula total do produto
                int totalQtd = produtoCorDAO.getTotalQuantidadeCores(produto.getId());
                produto.setQuantidade(totalQtd);

                produto.setNome(txtNome.getText().trim());
                produto.setCodigoLoja(txtCodigoLoja.getText().trim());
                if (txtGrupo != null) {
                    produto.setGrupo(txtGrupo.getText() != null && !txtGrupo.getText().trim().isEmpty() ? txtGrupo.getText().trim() : "GERAL");
                }
                produto.setPrecoVista(Double.parseDouble(txtPrecoVista.getText().trim().replace(",", ".")));
                produto.setPrecoPrazo(Double.parseDouble(txtPrecoPrazo.getText().trim().replace(",", ".")));
                produto.setLocalizacao(txtLocalizacao.getText().trim());
                produto.setUrlImagem(txtUrlImagem.getText().trim());
                produto.setDescricaoBreve(txtDescricaoBreve.getText().trim());

                produtoDAO.updateSemHistorico(produto);
                mostrarAlerta(Alert.AlertType.INFORMATION, "Sucesso", "Estoque da cor '" + corAtiva.getNomeCor() + "' e dados salvos no SQLite!");

                atualizarStatusSincronizado();
                carregarCoresProduto();
                carregarHistorico();
                notificarAtualizacao();
                fecharModal();
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Erro ao salvar alteração da cor.", e);
                mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Falha ao salvar cor: " + e.getMessage());
            }
            return;
        }

        // Caso padrão (Estoque Geral)
        produto.setNome(txtNome.getText().trim());
        produto.setCodigoLoja(txtCodigoLoja.getText().trim());
        if (txtGrupo != null) {
            produto.setGrupo(txtGrupo.getText() != null && !txtGrupo.getText().trim().isEmpty() ? txtGrupo.getText().trim() : "GERAL");
        }
        produto.setPrecoVista(Double.parseDouble(txtPrecoVista.getText().trim().replace(",", ".")));
        produto.setPrecoPrazo(Double.parseDouble(txtPrecoPrazo.getText().trim().replace(",", ".")));
        produto.setLocalizacao(txtLocalizacao.getText().trim());
        produto.setQuantidade(qtdFinal);
        produto.setUrlImagem(txtUrlImagem.getText().trim());
        produto.setDescricaoBreve(txtDescricaoBreve.getText().trim());

        try {
            if (produto.getId() == null) {
                produtoDAO.upsert(produto);
                if (produto.getId() != null) {
                    HistoricoEstoque h = new HistoricoEstoque(
                            produto.getId(),
                            qtdFinal > 0 ? "ENTRADA" : "AJUSTE",
                            qtdFinal,
                            0,
                            qtdFinal,
                            "Modificação de estoque",
                            responsavel
                    );
                    historicoEstoqueDAO.insert(h);
                }
                mostrarAlerta(Alert.AlertType.INFORMATION, "Sucesso", "Produto cadastrado com sucesso!");
            } else {
                produtoDAO.update(produto, responsavel, "Modificação de estoque");
                this.estoqueInicial = qtdFinal;
                mostrarAlerta(Alert.AlertType.INFORMATION, "Sucesso", "Alterações salvas no SQLite!");
            }
            atualizarStatusSincronizado();
            carregarCoresProduto();
            carregarHistorico();
            notificarAtualizacao();
            fecharModal();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao salvar produto.", e);
            mostrarAlerta(Alert.AlertType.ERROR, "Erro de Banco de Dados",
                    "Falha ao persistir alterações: " + e.getMessage());
        }
    }

    @FXML
    private void handleExcluirProduto() {
        if (produto.getId() == null) {
            fecharModal();
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar Exclusão");
        confirm.setHeaderText("Excluir o produto '" + produto.getNome() + "'?");
        confirm.setContentText("Esta ação é irreversível e removerá o item e suas cores do banco SQLite.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Optional<String> optResp = DialogHelper.solicitarResponsavel(
                    tabPanePrincipal.getScene().getWindow(),
                    "Responsável pela Exclusão",
                    "Informe quem está excluindo o produto '" + produto.getNome() + "':"
            );
            if (optResp.isEmpty()) {
                return;
            }

            try {
                produtoDAO.delete(produto.getId());
                notificarAtualizacao();
                fecharModal();
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Erro ao excluir produto.", e);
                mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Falha ao excluir produto: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleFechar() {
        fecharModal();
    }

    private void fecharModal() {
        if (stage != null) {
            stage.close();
        }
    }

    private void notificarAtualizacao() {
        if (onUpdateCallback != null) {
            onUpdateCallback.run();
        }
    }

    private boolean validarFormulario() {
        if (txtNome.getText() == null || txtNome.getText().trim().isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Validação", "O Nome do produto é obrigatório.");
            return false;
        }
        if (txtCodigoLoja.getText() == null || txtCodigoLoja.getText().trim().isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Validação", "O Código da Loja (SKU) é obrigatório.");
            return false;
        }
        try {
            Double.parseDouble(txtPrecoVista.getText().trim().replace(",", "."));
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.WARNING, "Validação", "Preço a Vista inválido.");
            return false;
        }
        try {
            Double.parseDouble(txtPrecoPrazo.getText().trim().replace(",", "."));
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.WARNING, "Validação", "Preço a Prazo inválido.");
            return false;
        }
        return true;
    }

    private void carregarImagem(String url) {
        if (url != null && !url.trim().isEmpty()) {
            try {
                Image image = new Image(url.trim(), true);
                image.errorProperty().addListener((obs, oldV, hasError) -> {
                    if (hasError) {
                        lblFotoPlaceholder.setText("Não foi possível carregar a imagem da URL.");
                        lblFotoPlaceholder.setVisible(true);
                        lblFotoPlaceholder.setManaged(true);
                        imgProduto.setImage(null);
                    }
                });
                image.progressProperty().addListener((obs, oldV, progress) -> {
                    if (progress.doubleValue() >= 1.0 && !image.isError()) {
                        lblFotoPlaceholder.setVisible(false);
                        lblFotoPlaceholder.setManaged(false);
                    }
                });
                imgProduto.setImage(image);
            } catch (Exception e) {
                lblFotoPlaceholder.setText("URL de imagem inválida.");
                lblFotoPlaceholder.setVisible(true);
                lblFotoPlaceholder.setManaged(true);
            }
        } else {
            imgProduto.setImage(null);
            lblFotoPlaceholder.setText("Nenhuma foto carregada\nClique em 'Buscar na Internet' acima");
            lblFotoPlaceholder.setVisible(true);
            lblFotoPlaceholder.setManaged(true);
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
