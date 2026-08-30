package com.swstock.controller;

import com.swstock.database.FuncionarioDAO;
import com.swstock.database.HistoricoEstoqueDAO;
import com.swstock.database.ProdutoDAO;
import com.swstock.model.HistoricoEstoque;
import com.swstock.model.Produto;
import com.swstock.service.ProductEnrichmentService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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
 * Controlador para o modal de detalhes, edição e histórico de estoque do produto.
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
    private final ObservableList<HistoricoEstoque> historicoList = FXCollections.observableArrayList();

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
        // Permite apenas números inteiros sem disparar gravações automáticas intermediárias
        txtQuantidade.textProperty().addListener((observable, oldValue, newValue) -> {
            if (isUpdatingProgrammatically) return;

            if (!newValue.matches("\\d*")) {
                txtQuantidade.setText(newValue.replaceAll("[^\\d]", ""));
                return;
            }

            if (!newValue.isEmpty()) {
                try {
                    int novaQtd = Integer.parseInt(newValue);
                    produto.setQuantidade(novaQtd);
                    if (novaQtd != estoqueInicial) {
                        lblAutoSaveStatus.setText("Alteração pendente de salvamento");
                        lblAutoSaveStatus.setStyle("-fx-text-fill: #D97706; -fx-font-weight: bold; -fx-font-size: 11px;");
                    } else {
                        atualizarStatusSincronizado();
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        // Ao perder o foco, se estiver vazio, reseta para 0
        txtQuantidade.focusedProperty().addListener((obs, oldVal, hasFocus) -> {
            if (!hasFocus) {
                if (txtQuantidade.getText() == null || txtQuantidade.getText().trim().isEmpty()) {
                    setQuantidadeDisplay(0);
                    produto.setQuantidade(0);
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

        if (produto == null) {
            this.produto = new Produto();
            this.estoqueInicial = 0;
            lblModalNome.setText("Novo Produto");
            lblModalCodigo.setText("CODIGO: [Pendente de Cadastro]");
            setQuantidadeDisplay(0);
            if (lblEstoqueInicial != null) {
                lblEstoqueInicial.setText("0 un.");
            }
            lblAutoSaveStatus.setText("Modo Criacao");
            lblAutoSaveStatus.setStyle("-fx-text-fill: #2563EB; -fx-font-weight: bold; -fx-font-size: 11px;");
            txtPrecoVista.setText("0.00");
            txtPrecoPrazo.setText("0.00");
            txtCodigoLoja.setText("SKU-" + (int)(Math.random() * 900000 + 100000));
            if (txtGrupo != null) txtGrupo.setText("GERAL");
            carregarHistorico();
        } else {
            this.estoqueInicial = produto.getQuantidade() != null ? produto.getQuantidade() : 0;
            lblModalNome.setText(produto.getNome() != null ? produto.getNome() : "Sem Nome");
            lblModalCodigo.setText("CODIGO: #" + (produto.getCodigoLoja() != null ? produto.getCodigoLoja() : "N/A"));
            setQuantidadeDisplay(this.estoqueInicial);
            if (lblEstoqueInicial != null) {
                lblEstoqueInicial.setText(this.estoqueInicial + " un.");
            }
            txtNome.setText(produto.getNome());
            txtCodigoLoja.setText(produto.getCodigoLoja());
            if (txtGrupo != null) txtGrupo.setText(produto.getGrupo() != null ? produto.getGrupo() : "GERAL");
            txtPrecoVista.setText(produto.getPrecoVista() != null ? String.valueOf(produto.getPrecoVista()) : "0.00");
            txtPrecoPrazo.setText(produto.getPrecoPrazo() != null ? String.valueOf(produto.getPrecoPrazo()) : "0.00");
            txtLocalizacao.setText(produto.getLocalizacao());
            txtUrlImagem.setText(produto.getUrlImagem());
            txtDescricaoBreve.setText(produto.getDescricaoBreve());

            carregarImagem(produto.getUrlImagem());
            atualizarStatusSincronizado();
            carregarHistorico();
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
            if (lblFiltroHistoricoStatus != null) lblFiltroHistoricoStatus.setText("Produto nao cadastrado ainda.");
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
            LOGGER.log(Level.SEVERE, "Erro ao carregar historico de movimentacoes de estoque.", e);
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
            mostrarAlerta(Alert.AlertType.INFORMATION, "Localizacao Nao Cadastrada",
                    "Este produto ainda nao possui localizacao definida.\nDigite uma estante (ex: 'Estante A1') para visualiza-la no mapa.");
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
                        mostrarAlerta(Alert.AlertType.INFORMATION, "Busca Web", resultado.statusMessage());
                        return;
                    }

                    txtDescricaoBreve.setText(resultado.generatedDescription());
                    imagensEncontradas = new ArrayList<>(resultado.candidateImageUrls());
                    indiceImagemAtual = 0;

                    if (!imagensEncontradas.isEmpty()) {
                        String primeiraFoto = imagensEncontradas.get(0);
                        txtUrlImagem.setText(primeiraFoto);
                        carregarImagem(primeiraFoto);

                        boxImageNav.setVisible(imagensEncontradas.size() > 1);
                        boxImageNav.setManaged(imagensEncontradas.size() > 1);
                        atualizarContadorFotos();
                    }

                    lblAutoSaveStatus.setText("Foto e descricao obtidas da internet");
                    lblAutoSaveStatus.setStyle("-fx-text-fill: #16A34A; -fx-font-weight: bold; -fx-font-size: 11px;");
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        exibirLoadingWeb(false, "");
                        LOGGER.log(Level.SEVERE, "Erro na thread de busca web.", ex);
                        mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Falha na busca web: " + ex.getMessage());
                    });
                    return null;
                });
    }

    @FXML
    private void handleFotoAnterior() {
        if (imagensEncontradas.isEmpty()) return;
        indiceImagemAtual = (indiceImagemAtual - 1 + imagensEncontradas.size()) % imagensEncontradas.size();
        String url = imagensEncontradas.get(indiceImagemAtual);
        txtUrlImagem.setText(url);
        carregarImagem(url);
        atualizarContadorFotos();
    }

    @FXML
    private void handleFotoProxima() {
        if (imagensEncontradas.isEmpty()) return;
        indiceImagemAtual = (indiceImagemAtual + 1) % imagensEncontradas.size();
        String url = imagensEncontradas.get(indiceImagemAtual);
        txtUrlImagem.setText(url);
        carregarImagem(url);
        atualizarContadorFotos();
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
        produto.setQuantidade(novo);
        lblAutoSaveStatus.setText("Alteração pendente de salvamento");
        lblAutoSaveStatus.setStyle("-fx-text-fill: #D97706; -fx-font-weight: bold; -fx-font-size: 11px;");
    }

    @FXML
    private void handleDecrement() {
        int atual = parseQuantidadeAtual();
        if (atual > 0) {
            int novo = atual - 1;
            setQuantidadeDisplay(novo);
            produto.setQuantidade(novo);
            lblAutoSaveStatus.setText("Alteração pendente de salvamento");
            lblAutoSaveStatus.setStyle("-fx-text-fill: #D97706; -fx-font-weight: bold; -fx-font-size: 11px;");
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

    /**
     * Exibe um diálogo modal com a lista de funcionários para selecionar quem é o responsável pela alteração.
     */
    private Optional<String> solicitarResponsavel() {
        FuncionarioDAO fDao = new FuncionarioDAO();
        List<String> funcionarios = fDao.getNomesFuncionarios();
        if (funcionarios.isEmpty()) {
            funcionarios = List.of("Tiago", "Denise", "Lucas", "Maurício", "Éder", "Gustavo");
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Responsável pela Alteração");
        dialog.setHeaderText("Quem está realizando esta alteração no produto/estoque?");
        if (stage != null) {
            dialog.initOwner(stage);
        }

        ButtonType btnConfirmar = new ButtonType("Confirmar e Salvar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnConfirmar, ButtonType.CANCEL);

        ComboBox<String> cmbFuncionarios = new ComboBox<>(FXCollections.observableArrayList(funcionarios));
        cmbFuncionarios.setEditable(true);
        cmbFuncionarios.getSelectionModel().selectFirst();
        cmbFuncionarios.setMaxWidth(Double.MAX_VALUE);
        cmbFuncionarios.setStyle("-fx-font-size: 13px; -fx-padding: 4px;");

        VBox content = new VBox(10);
        content.getChildren().addAll(
                new Label("Selecione ou digite o nome do funcionário:"),
                cmbFuncionarios
        );
        content.setPrefWidth(350);

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton != null && (dialogButton == btnConfirmar || dialogButton.getButtonData() == ButtonBar.ButtonData.OK_DONE)) {
                String val = cmbFuncionarios.getEditor().getText();
                if (val == null || val.trim().isEmpty()) {
                    val = cmbFuncionarios.getValue();
                }
                return (val != null && !val.trim().isEmpty()) ? val.trim() : "Tiago";
            }
            return null;
        });

        return dialog.showAndWait();
    }

    @FXML
    private void handleSalvarAlteracoes() {
        if (!validarFormulario()) {
            return;
        }

        int qtdFinal = parseQuantidadeAtual();
        int diferenca = qtdFinal - this.estoqueInicial;

        // Se houve alteração na quantidade física de estoque ou cadastro de novo item, solicita o funcionário responsável
        String responsavel = "Tiago";
        if (diferenca != 0 || produto.getId() == null) {
            Optional<String> optResp = solicitarResponsavel();
            if (optResp.isEmpty()) {
                // Usuário cancelou o diálogo de salvamento
                return;
            }
            responsavel = optResp.get();
        }

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
                            "Estoque Inicial de Cadastro",
                            responsavel
                    );
                    historicoEstoqueDAO.insert(h);
                }
                mostrarAlerta(Alert.AlertType.INFORMATION, "Sucesso", "Produto cadastrado com sucesso!");
            } else {
                produtoDAO.update(produto, responsavel, "Edição Geral de Cadastro");
                this.estoqueInicial = qtdFinal;
                mostrarAlerta(Alert.AlertType.INFORMATION, "Sucesso", "Alterações salvas no SQLite!");
            }
            atualizarStatusSincronizado();
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
        confirm.setTitle("Confirmar Exclusao");
        confirm.setHeaderText("Excluir o produto '" + produto.getNome() + "'?");
        confirm.setContentText("Esta acao e irreversivel e removera o item do banco SQLite.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
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
            mostrarAlerta(Alert.AlertType.WARNING, "Validacao", "O Nome do produto e obrigatorio.");
            return false;
        }
        if (txtCodigoLoja.getText() == null || txtCodigoLoja.getText().trim().isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Validacao", "O Codigo da Loja (SKU) e obrigatorio.");
            return false;
        }
        try {
            Double.parseDouble(txtPrecoVista.getText().trim().replace(",", "."));
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.WARNING, "Validacao", "Preco a Vista invalido.");
            return false;
        }
        try {
            Double.parseDouble(txtPrecoPrazo.getText().trim().replace(",", "."));
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.WARNING, "Validacao", "Preco a Prazo invalido.");
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
                        lblFotoPlaceholder.setText("Nao foi possivel carregar a imagem da URL.");
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
                lblFotoPlaceholder.setText("URL de imagem invalida.");
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
