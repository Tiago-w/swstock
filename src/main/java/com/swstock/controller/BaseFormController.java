package com.swstock.controller;

import com.swstock.model.Base;
import com.swstock.util.DeadKeyHelper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Controller executivo do modal de Cadastro e Modificação de Bases de Tintas,
 * padronizado com o mesmo visual da Tela de Login e do modal de Trocar Responsável.
 */
public class BaseFormController {

    private static final String LABEL_AUTOMOTIVA = "Automotiva";
    private static final String LABEL_PREDIAL = "Predial";

    @FXML private VBox rootPane;
    @FXML private Label lblModalTitulo;
    @FXML private Label lblModalSubtitulo;
    @FXML private Label lblBadgeTipoHeader;

    @FXML private HBox boxResumoBase;
    @FXML private Circle dotTipoResumo;
    @FXML private Label lblResumoNome;
    @FXML private Label lblResumoEstoque;

    @FXML private TextField txtTamanho;
    @FXML private ComboBox<String> cbTipo;
    @FXML private TextField txtNome;
    @FXML private TextField txtFabricante;
    @FXML private TextField txtValorCompra;

    @FXML private Button btnMenos;
    @FXML private TextField txtQuantidade;
    @FXML private Button btnMais;
    @FXML private Label lblDelta;

    @FXML private Label lblErroValidacao;
    @FXML private Button btnVerHistorico;
    @FXML private Button btnCancelar;
    @FXML private Button btnSalvar;

    private Stage stage;
    private Base baseOriginal;
    private Base resultado;
    private int estoqueInicial = 0;
    private Consumer<Base> onAbrirHistorico;

    @FXML
    public void initialize() {
        cbTipo.setItems(FXCollections.observableArrayList(LABEL_AUTOMOTIVA, LABEL_PREDIAL));
        cbTipo.getSelectionModel().selectFirst();

        DeadKeyHelper.attach(txtTamanho);
        DeadKeyHelper.attach(txtNome);
        DeadKeyHelper.attach(txtFabricante);

        // Permite apenas dígitos no campo de quantidade
        txtQuantidade.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("\\d*") ? change : null));

        txtQuantidade.textProperty().addListener((obs, o, n) -> atualizarDelta());
        cbTipo.valueProperty().addListener((obs, o, n) -> atualizarBadgeTipo());

        // Limpa alerta inline ao editar qualquer campo obrigatório ou de preço
        txtTamanho.textProperty().addListener((obs, o, n) -> ocultarErro());
        txtNome.textProperty().addListener((obs, o, n) -> ocultarErro());
        txtValorCompra.textProperty().addListener((obs, o, n) -> ocultarErro());

        atualizarBadgeTipo();
        atualizarDelta();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setOnAbrirHistorico(Consumer<Base> onAbrirHistorico) {
        this.onAbrirHistorico = onAbrirHistorico;
        atualizarVisibilidadeBotaoHistorico();
    }

    /**
     * Configura o modal no modo de cadastro de uma nova base.
     */
    public void configurarModoCadastro(String tipoPadrao) {
        this.baseOriginal = null;
        this.resultado = null;
        this.estoqueInicial = 0;

        lblModalTitulo.setText("Cadastrar Nova Base");
        lblModalSubtitulo.setText("Preencha os dados para incluir uma nova base no catálogo.");
        btnSalvar.setText("Cadastrar Base");

        boxResumoBase.setVisible(false);
        boxResumoBase.setManaged(false);

        txtTamanho.setText("");
        txtNome.setText("");
        cbTipo.setValue(tipoParaRotulo(tipoPadrao));
        txtFabricante.setText("Geral");
        txtValorCompra.setText("0,00");
        txtQuantidade.setText("0");

        ocultarErro();
        atualizarBadgeTipo();
        atualizarDelta();
        atualizarVisibilidadeBotaoHistorico();
    }

    /**
     * Configura o modal no modo de edição/modificação de uma base existente.
     */
    public void configurarModoEdicao(Base base) {
        this.baseOriginal = base;
        this.resultado = null;
        this.estoqueInicial = base != null ? base.getQuantidade() : 0;

        lblModalTitulo.setText("Modificar Base de Tinta");
        lblModalSubtitulo.setText("Atualize os dados cadastrais e o saldo em estoque.");
        btnSalvar.setText("Salvar Alterações");

        boxResumoBase.setVisible(true);
        boxResumoBase.setManaged(true);

        if (base != null) {
            String nomeExibicao = base.getNome() != null ? base.getNome() : "";
            String tamExibicao = base.getTamanho();
            lblResumoNome.setText(tamExibicao.isEmpty() ? nomeExibicao : "[" + tamExibicao + "] " + nomeExibicao);

            int qtdAtual = base.getQuantidade();
            lblResumoEstoque.setText("Atual: " + qtdAtual + " un.");
            lblResumoEstoque.getStyleClass().removeAll("badge-stock-empty", "badge-stock-low", "badge-stock-ok");
            if (qtdAtual == 0) {
                lblResumoEstoque.getStyleClass().add("badge-stock-empty");
            } else if (qtdAtual <= 3) {
                lblResumoEstoque.getStyleClass().add("badge-stock-low");
            } else {
                lblResumoEstoque.getStyleClass().add("badge-stock-ok");
            }

            txtTamanho.setText(base.getTamanho());
            txtNome.setText(base.getNome() != null ? base.getNome() : "");
            cbTipo.setValue(tipoParaRotulo(base.getTipo()));
            txtFabricante.setText(base.getFabricante());
            txtValorCompra.setText(String.format(Locale.of("pt", "BR"), "%.2f", base.getValorCompra()));
            txtQuantidade.setText(String.valueOf(qtdAtual));
        }

        ocultarErro();
        atualizarBadgeTipo();
        atualizarDelta();
        atualizarVisibilidadeBotaoHistorico();
    }

    public Optional<Base> getResultado() {
        return Optional.ofNullable(resultado);
    }

    @FXML
    private void handleDecrementar() {
        int atual = parseQuantidadeAtual();
        if (atual > 0) {
            txtQuantidade.setText(String.valueOf(atual - 1));
        }
    }

    @FXML
    private void handleIncrementar() {
        int atual = parseQuantidadeAtual();
        txtQuantidade.setText(String.valueOf(atual + 1));
    }

    @FXML
    private void handleVerHistorico() {
        if (baseOriginal != null && onAbrirHistorico != null) {
            onAbrirHistorico.accept(baseOriginal);
        }
    }

    @FXML
    private void handleCancelar() {
        this.resultado = null;
        if (stage != null) {
            stage.close();
        }
    }

    @FXML
    private void handleSalvar() {
        String tamanho = txtTamanho.getText() != null ? txtTamanho.getText().trim().toUpperCase() : "";
        String nome = txtNome.getText() != null ? txtNome.getText().trim() : "";
        String fabricante = txtFabricante.getText() != null ? txtFabricante.getText().trim() : "";
        String tipo = rotuloParaTipo(cbTipo.getValue());

        if (tamanho.isEmpty()) {
            mostrarErro("Informe o tamanho da base (ex: 3.6L, 900ml, 18L).");
            txtTamanho.requestFocus();
            return;
        }
        if (nome.isEmpty()) {
            mostrarErro("Informe o nome da base.");
            txtNome.requestFocus();
            return;
        }

        Double valorCompra = parseValorMonetario(txtValorCompra.getText());
        if (valorCompra == null || valorCompra < 0.0) {
            mostrarErro("Informe um valor de compra válido (ex: 85,50).");
            txtValorCompra.requestFocus();
            return;
        }

        int quantidade = parseQuantidadeAtual();

        if (baseOriginal != null) {
            baseOriginal.setTamanho(tamanho);
            baseOriginal.setNome(nome);
            baseOriginal.setTipo(tipo);
            baseOriginal.setFabricante(fabricante.isEmpty() ? "Geral" : fabricante);
            baseOriginal.setQuantidade(quantidade);
            baseOriginal.setValorCompra(valorCompra);
            this.resultado = baseOriginal;
        } else {
            this.resultado = new Base(
                    tamanho,
                    nome,
                    tipo,
                    fabricante.isEmpty() ? "Geral" : fabricante,
                    quantidade,
                    valorCompra
            );
        }

        if (stage != null) {
            stage.close();
        }
    }

    private void atualizarBadgeTipo() {
        String tipo = rotuloParaTipo(cbTipo.getValue());
        boolean predial = "PREDIAL".equalsIgnoreCase(tipo);
        lblBadgeTipoHeader.setText(predial ? "PREDIAL" : "AUTOMOTIVA");
        if (dotTipoResumo != null) {
            dotTipoResumo.setFill(Color.web(predial ? "#D97706" : "#1565C0"));
        }
    }

    private void atualizarDelta() {
        int atual = parseQuantidadeAtual();
        int diff = atual - estoqueInicial;
        lblDelta.getStyleClass().removeAll("delta-pill-neutral", "delta-pill-add", "delta-pill-remove");

        if (diff > 0) {
            lblDelta.setText("+" + diff + (diff == 1 ? " un. adicionada" : " un. adicionadas"));
            lblDelta.getStyleClass().add("delta-pill-add");
        } else if (diff < 0) {
            int abs = Math.abs(diff);
            lblDelta.setText("-" + abs + (abs == 1 ? " un. retirada" : " un. retiradas"));
            lblDelta.getStyleClass().add("delta-pill-remove");
        } else {
            lblDelta.setText(baseOriginal == null ? "Estoque inicial" : "Sem alteração");
            lblDelta.getStyleClass().add("delta-pill-neutral");
        }
    }

    private void atualizarVisibilidadeBotaoHistorico() {
        boolean mostrar = baseOriginal != null && onAbrirHistorico != null;
        if (btnVerHistorico != null) {
            btnVerHistorico.setVisible(mostrar);
            btnVerHistorico.setManaged(mostrar);
        }
    }

    private void mostrarErro(String msg) {
        lblErroValidacao.setText(msg);
        lblErroValidacao.setVisible(true);
        lblErroValidacao.setManaged(true);
        if (stage != null) {
            stage.sizeToScene();
        }
    }

    private void ocultarErro() {
        if (lblErroValidacao != null && lblErroValidacao.isVisible()) {
            lblErroValidacao.setVisible(false);
            lblErroValidacao.setManaged(false);
            if (stage != null) {
                stage.sizeToScene();
            }
        }
    }

    private int parseQuantidadeAtual() {
        if (txtQuantidade == null || txtQuantidade.getText() == null || txtQuantidade.getText().trim().isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(txtQuantidade.getText().trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static Double parseValorMonetario(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return 0.0;
        }
        String limpo = texto.trim().replace("R$", "").replace(" ", "");
        if (limpo.isEmpty()) {
            return 0.0;
        }
        if (limpo.contains(".") && limpo.contains(",")) {
            limpo = limpo.replace(".", "").replace(",", ".");
        } else {
            limpo = limpo.replace(",", ".");
        }
        try {
            double v = Double.parseDouble(limpo);
            return Double.isFinite(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String tipoParaRotulo(String tipo) {
        return (tipo != null && tipo.toUpperCase(Locale.ROOT).contains("PREDIA")) ? LABEL_PREDIAL : LABEL_AUTOMOTIVA;
    }

    static String rotuloParaTipo(String rotulo) {
        return (rotulo != null && rotulo.toUpperCase(Locale.ROOT).contains("PREDIA")) ? "PREDIAL" : "AUTOMOTIVA";
    }
}
