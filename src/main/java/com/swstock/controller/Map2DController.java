package com.swstock.controller;

import com.swstock.database.ProdutoDAO;
import com.swstock.model.Produto;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller para o Mapa 2D interativo da planta baixa do depósito físico.
 * Permite navegação visual por estantes, inspeção detalhada de produtos por estante
 * e destaque com animação piscante em vermelho para localização de produtos.
 */
public class Map2DController {

    private static final Logger LOGGER = Logger.getLogger(Map2DController.class.getName());

    @FXML private FlowPane paneCorredorA;
    @FXML private FlowPane paneCorredorB;
    @FXML private FlowPane paneCorredorC;
    @FXML private FlowPane paneCorredorD;
    @FXML private FlowPane paneOutrasLocalizacoes;
    @FXML private VBox boxOutrasLocalizacoes;

    @FXML private HBox boxDestaqueAlvo;
    @FXML private Label lblDestaqueAlvo;

    @FXML private Label lblEstanteTitulo;
    @FXML private Label lblEstanteResumo;
    @FXML private TableView<Produto> tblProdutosEstante;
    @FXML private TableColumn<Produto, String> colEstanteNome;
    @FXML private TableColumn<Produto, String> colEstanteQtd;
    @FXML private TableColumn<Produto, String> colEstantePreco;
    @FXML private Button btnFiltrarPrincipal;

    private ProdutoDAO produtoDAO;
    private Consumer<String> onLocationSelectedCallback;
    private Stage stage;

    private final ObservableList<Produto> produtosEstanteList = FXCollections.observableArrayList();
    private final Map<String, VBox> mapCardsEstantes = new HashMap<>();
    private String estanteSelecionada = null;

    private Timeline blinkTimeline;
    private VBox cardPiscando;

    @FXML
    public void initialize() {
        configurarTabelaEstante();
    }

    private void configurarTabelaEstante() {
        colEstanteNome.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getNome() != null ? cell.getValue().getNome() : "-"
        ));

        colEstanteQtd.setCellValueFactory(cell -> new SimpleStringProperty(
                (cell.getValue().getQuantidade() != null ? cell.getValue().getQuantidade() : 0) + " un."
        ));
        colEstanteQtd.setCellFactory(column -> new TableCell<Produto, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER);
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #16A34A;");
                }
            }
        });

        colEstantePreco.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getPrecoVistaFormatado()
        ));
        colEstantePreco.setCellFactory(column -> new TableCell<Produto, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER_RIGHT);
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #0F172A;");
                }
            }
        });

        tblProdutosEstante.setItems(produtosEstanteList);
    }

    public void setDialogStage(Stage stage) {
        this.stage = stage;
        if (stage != null) {
            stage.setOnHidden(event -> pararAnimacaoPiscante());
        }
    }

    public void setProdutoDAO(ProdutoDAO produtoDAO) {
        this.produtoDAO = produtoDAO;
    }

    public void setOnLocationSelectedCallback(Consumer<String> callback) {
        this.onLocationSelectedCallback = callback;
    }

    /**
     * Carrega as ocupações do SQLite e constrói os cartões gráficos das estantes.
     */
    public void carregarMapa() {
        if (produtoDAO == null) {
            produtoDAO = new ProdutoDAO();
        }

        paneCorredorA.getChildren().clear();
        paneCorredorB.getChildren().clear();
        paneCorredorC.getChildren().clear();
        paneCorredorD.getChildren().clear();
        paneOutrasLocalizacoes.getChildren().clear();
        mapCardsEstantes.clear();

        try {
            Map<String, Integer> contagens = produtoDAO.getLocationCounts();
            List<String> todasLocs = produtoDAO.getAllLocations();

            // Estantes padrão do armazém
            List<String> estantesA = List.of("Estante A1", "Estante A2", "Estante A3", "Estante A4");
            List<String> estantesB = List.of("Estante B1", "Estante B2", "Estante B3", "Estante B4");
            List<String> estantesC = List.of("Estante C1", "Estante C2", "Estante C3", "Estante C4");
            List<String> estantesD = List.of("Estante D1", "Estante D2", "Estante D3", "Estante D4");

            Set<String> processadas = new HashSet<>();

            for (String loc : estantesA) {
                int qtd = buscarQuantidade(contagens, loc);
                VBox card = criarCardEstante(loc, qtd);
                paneCorredorA.getChildren().add(card);
                mapCardsEstantes.put(loc.toUpperCase(), card);
                processadas.add(loc.toUpperCase());
            }

            for (String loc : estantesB) {
                int qtd = buscarQuantidade(contagens, loc);
                VBox card = criarCardEstante(loc, qtd);
                paneCorredorB.getChildren().add(card);
                mapCardsEstantes.put(loc.toUpperCase(), card);
                processadas.add(loc.toUpperCase());
            }

            for (String loc : estantesC) {
                int qtd = buscarQuantidade(contagens, loc);
                VBox card = criarCardEstante(loc, qtd);
                paneCorredorC.getChildren().add(card);
                mapCardsEstantes.put(loc.toUpperCase(), card);
                processadas.add(loc.toUpperCase());
            }

            for (String loc : estantesD) {
                int qtd = buscarQuantidade(contagens, loc);
                VBox card = criarCardEstante(loc, qtd);
                paneCorredorD.getChildren().add(card);
                mapCardsEstantes.put(loc.toUpperCase(), card);
                processadas.add(loc.toUpperCase());
            }

            // Localizações dinâmicas adicionais cadastradas no banco
            int adicionais = 0;
            for (String locBanco : todasLocs) {
                if (locBanco != null && !locBanco.trim().isEmpty() && !processadas.contains(locBanco.toUpperCase())) {
                    int qtd = buscarQuantidade(contagens, locBanco);
                    VBox card = criarCardEstante(locBanco, qtd);
                    paneOutrasLocalizacoes.getChildren().add(card);
                    mapCardsEstantes.put(locBanco.toUpperCase(), card);
                    processadas.add(locBanco.toUpperCase());
                    adicionais++;
                }
            }

            if (adicionais == 0) {
                boxOutrasLocalizacoes.setVisible(false);
                boxOutrasLocalizacoes.setManaged(false);
            } else {
                boxOutrasLocalizacoes.setVisible(true);
                boxOutrasLocalizacoes.setManaged(true);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao carregar contagens para o Mapa 2D.", e);
        }
    }

    private int buscarQuantidade(Map<String, Integer> contagens, String loc) {
        for (Map.Entry<String, Integer> entry : contagens.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(loc.trim())) {
                return entry.getValue();
            }
        }
        return 0;
    }

    private VBox criarCardEstante(String nomeEstante, int totalUnidades) {
        VBox card = new VBox();
        card.getStyleClass().add("shelf-card");
        card.setAlignment(Pos.CENTER);

        Label lblNome = new Label(nomeEstante.toUpperCase());
        lblNome.getStyleClass().add("shelf-title");

        Label lblQtd = new Label(totalUnidades + " unidades");
        lblQtd.getStyleClass().add("shelf-qty");

        Label lblBadge = new Label(totalUnidades > 0 ? "OCUPADA" : "LIVRE");
        lblBadge.getStyleClass().add(totalUnidades > 0 ? "badge-stock-ok" : "badge-stock-empty");

        card.getChildren().addAll(lblNome, lblQtd, lblBadge);

        // Tooltip rica ao passar o mouse
        Tooltip tooltip = new Tooltip(nomeEstante + "\n• " + totalUnidades + " unidades em estoque\nClique para inspecionar");
        Tooltip.install(card, tooltip);

        card.setOnMouseClicked(event -> {
            selecionarEstante(nomeEstante, card);
        });

        return card;
    }

    /**
     * Seleciona uma estante, atualiza estilos visuais e carrega seus produtos no painel lateral.
     */
    public void selecionarEstante(String nomeEstante, VBox card) {
        this.estanteSelecionada = nomeEstante;

        // Limpa seleção visual de outros cards não piscantes
        for (Map.Entry<String, VBox> entry : mapCardsEstantes.entrySet()) {
            if (entry.getValue() != cardPiscando) {
                entry.getValue().getStyleClass().remove("shelf-card-active");
            }
        }

        if (card != null && card != cardPiscando) {
            card.getStyleClass().add("shelf-card-active");
        }

        lblEstanteTitulo.setText(nomeEstante.toUpperCase());

        try {
            List<Produto> produtos = produtoDAO.findByFilter(null, nomeEstante);
            produtosEstanteList.setAll(produtos);

            int totalUnidades = produtos.stream()
                    .mapToInt(p -> p.getQuantidade() != null ? p.getQuantidade() : 0)
                    .sum();

            lblEstanteResumo.setText(String.format("%d produto(s) cadastrado(s) • %d unidades no total",
                    produtos.size(), totalUnidades));

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao carregar produtos da estante " + nomeEstante, e);
            lblEstanteResumo.setText("Erro ao carregar produtos desta estante.");
        }
    }
    /**
     * Destaca visualmente uma estante com banner informativo e efeito piscante.
     */
    public void destacarLocalizacao(String localAlvo) {
        if (localAlvo == null || localAlvo.trim().isEmpty()) {
            return;
        }

        String chave = localAlvo.trim().toUpperCase();
        VBox card = mapCardsEstantes.get(chave);

        // Busca flexível se não encontrar pela chave exata
        if (card == null) {
            String chaveLimpa = chave.replaceAll("[^A-Z0-9]", "");
            for (Map.Entry<String, VBox> entry : mapCardsEstantes.entrySet()) {
                String kLimpa = entry.getKey().replaceAll("[^A-Z0-9]", "");
                if (kLimpa.equalsIgnoreCase(chaveLimpa) || kLimpa.contains(chaveLimpa) || chaveLimpa.contains(kLimpa)) {
                    card = entry.getValue();
                    chave = entry.getKey();
                    break;
                }
            }
        }

        boxDestaqueAlvo.setVisible(true);
        boxDestaqueAlvo.setManaged(true);
        lblDestaqueAlvo.setText("Localização do Produto: " + chave + " (Piscando em Vermelho)");

        if (card != null) {
            selecionarEstante(chave, card);
            iniciarAnimacaoPiscante(card);
        } else {
            lblEstanteTitulo.setText(chave);
            lblEstanteResumo.setText("Localização cadastrada no produto, mas fora da planta padrão.");
        }
    }

    private void iniciarAnimacaoPiscante(VBox card) {
        pararAnimacaoPiscante();
        this.cardPiscando = card;

        blinkTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, e -> {
                    card.setStyle("-fx-background-color: #DC2626 !important; -fx-border-color: #F87171 !important; -fx-border-width: 3.5px !important; -fx-border-radius: 8px !important; -fx-background-radius: 8px !important; -fx-effect: dropshadow(three-pass-box, rgba(239, 68, 68, 0.95), 22, 0, 0, 0);");
                }),
                new KeyFrame(Duration.millis(350), e -> {
                    card.setStyle("-fx-background-color: #1E293B !important; -fx-border-color: #EF4444 !important; -fx-border-width: 2px !important; -fx-border-radius: 8px !important; -fx-background-radius: 8px !important;");
                }),
                new KeyFrame(Duration.millis(700), e -> {})
        );
        blinkTimeline.setCycleCount(Timeline.INDEFINITE);
        blinkTimeline.play();
    }

    private void pararAnimacaoPiscante() {
        if (blinkTimeline != null) {
            blinkTimeline.stop();
            blinkTimeline = null;
        }
        if (cardPiscando != null) {
            cardPiscando.setStyle("");
            cardPiscando = null;
        }
    }

    @FXML
    private void handleFiltrarTabelaPrincipal() {
        if (estanteSelecionada != null && onLocationSelectedCallback != null) {
            onLocationSelectedCallback.accept(estanteSelecionada);
            if (stage != null) {
                stage.close();
            }
        }
    }

    @FXML
    private void handleExibirTodos() {
        if (onLocationSelectedCallback != null) {
            onLocationSelectedCallback.accept(null);
            if (stage != null) {
                stage.close();
            }
        }
    }

    @FXML
    private void handleFechar() {
        pararAnimacaoPiscante();
        if (stage != null) {
            stage.close();
        }
    }
}
