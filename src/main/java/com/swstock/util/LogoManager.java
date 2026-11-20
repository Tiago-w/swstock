package com.swstock.util;

import com.swstock.MainApp;
import com.swstock.database.ConfiguracaoDAO;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gerenciador centralizado da logo personalizada da empresa.
 * Persiste a imagem em Base64 na tabela 'configuracoes' do SQLite (acompanhando o banco em backups)
 * e mantém um cache local rápido para exibição imediata na Splash Screen.
 */
public class LogoManager {

    private static final Logger LOGGER = Logger.getLogger(LogoManager.class.getName());
    public static final String CONFIG_KEY_LOGO_BASE64 = "empresa_logo_base64";
    private static final long MAX_INPUT_FILE_BYTES = 15L * 1024L * 1024L; // 15 MB
    private static final long MAX_RAW_KEEP_BYTES = 2L * 1024L * 1024L;    // 2 MB
    private static final int MAX_DIMENSION = 1200;

    private static final LogoManager INSTANCE = new LogoManager();

    private ConfiguracaoDAO configuracaoDAO;
    private Path cacheFilePath;

    private volatile byte[] cachedLogoBytes = null;
    private volatile boolean loadedFromDb = false;

    private final IntegerProperty logoVersion = new SimpleIntegerProperty(0);

    private LogoManager() {
        this.configuracaoDAO = null;
        this.cacheFilePath = null;
    }

    /**
     * Construtor para testes unitários isolados com banco e cache próprios.
     */
    public LogoManager(ConfiguracaoDAO configuracaoDAO, Path cacheFilePath) {
        this.configuracaoDAO = configuracaoDAO;
        this.cacheFilePath = cacheFilePath;
    }

    public static LogoManager getInstance() {
        return INSTANCE;
    }

    /**
     * Propriedade reativa incrementada sempre que a logo é adicionada, alterada ou removida.
     */
    public IntegerProperty logoVersionProperty() {
        return logoVersion;
    }

    private ConfiguracaoDAO getConfiguracaoDAO() {
        if (configuracaoDAO == null) {
            configuracaoDAO = new ConfiguracaoDAO();
        }
        return configuracaoDAO;
    }

    private Path getCacheFilePath() {
        if (cacheFilePath != null) {
            return cacheFilePath;
        }
        String customProp = System.getProperty("swstock.logo.cache.path");
        if (customProp != null && !customProp.isBlank()) {
            return Path.of(customProp.trim());
        }
        boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path dir;
        if (isWindows) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                dir = Path.of(appData, "SWStock");
            } else {
                dir = Path.of(System.getProperty("user.home"), "AppData", "Roaming", "SWStock");
            }
        } else {
            dir = Path.of(System.getProperty("user.home"), ".swstock");
        }
        return dir.resolve("custom_logo.img");
    }

    /**
     * Lê o cache local da logo sem inicializar o SQLite (ideal para abertura instantânea da Splash Screen).
     */
    public synchronized byte[] getLogoBytesForSplash() {
        if (cachedLogoBytes != null && cachedLogoBytes.length > 0) {
            return cachedLogoBytes.clone();
        }
        Path cache = getCacheFilePath();
        try {
            if (Files.isRegularFile(cache)) {
                byte[] bytes = Files.readAllBytes(cache);
                if (bytes.length > 0) {
                    return bytes;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Não foi possível ler cache local da logo para splash.", e);
        }
        return null;
    }

    /**
     * Carrega ou recarrega a logo diretamente do banco de dados SQLite e atualiza o cache local.
     */
    public synchronized void sincronizarComBanco() {
        try {
            String base64 = getConfiguracaoDAO().getValor(CONFIG_KEY_LOGO_BASE64, null);
            if (base64 != null && !base64.isBlank()) {
                byte[] decoded = Base64.getDecoder().decode(base64.trim());
                if (decoded.length > 0) {
                    this.cachedLogoBytes = decoded;
                    this.loadedFromDb = true;
                    escreverCacheSilencioso(decoded);
                    return;
                }
            }
            this.cachedLogoBytes = null;
            this.loadedFromDb = true;
            apagarCacheSilencioso();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao sincronizar logo da empresa com o banco SQLite.", e);
            this.loadedFromDb = true;
        }
    }

    private synchronized void ensureLoadedFromDb() {
        if (!loadedFromDb) {
            sincronizarComBanco();
        }
    }

    /**
     * Indica se o usuário já configurou uma logo personalizada da empresa.
     */
    public synchronized boolean hasCustomLogo() {
        ensureLoadedFromDb();
        return cachedLogoBytes != null && cachedLogoBytes.length > 0;
    }

    /**
     * Retorna os bytes da logo personalizada configurada, ou null se nenhuma logo foi definida.
     */
    public synchronized byte[] getLogoBytes() {
        ensureLoadedFromDb();
        if (cachedLogoBytes == null || cachedLogoBytes.length == 0) {
            return null;
        }
        return cachedLogoBytes.clone();
    }

    /**
     * Retorna a imagem JavaFX da logo personalizada, ou null se nenhuma logo foi definida.
     */
    public Image getLogoFxImage() {
        byte[] bytes = getLogoBytes();
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return new Image(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao criar Image JavaFX a partir dos bytes da logo.", e);
            return null;
        }
    }

    /**
     * Retorna a logo personalizada da empresa ou, se ainda não configurada, o ícone padrão do SWStock.
     */
    public Image getLogoFxImageOrDefault() {
        Image custom = getLogoFxImage();
        return custom != null ? custom : MainApp.getAppIcon();
    }

    /**
     * Retorna a logo em cache para a Splash Screen (antes do banco abrir) ou o ícone padrão do SWStock.
     */
    public Image getLogoFxImageForSplashOrDefault() {
        byte[] bytes = getLogoBytesForSplash();
        if (bytes != null && bytes.length > 0) {
            try {
                Image img = new Image(new ByteArrayInputStream(bytes));
                if (!img.isError()) {
                    return img;
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Erro ao ler imagem de cache para splash.", e);
            }
        }
        return MainApp.getAppIcon();
    }

    /**
     * Valida, otimiza (se necessário) e persiste a imagem escolhida pelo usuário como logo da empresa.
     */
    public synchronized void salvarLogo(File arquivoImagem) throws IOException {
        if (arquivoImagem == null || !arquivoImagem.exists() || !arquivoImagem.isFile()) {
            throw new IOException("O arquivo selecionado não existe ou não pode ser lido.");
        }
        if (arquivoImagem.length() == 0) {
            throw new IOException("O arquivo selecionado está vazio.");
        }
        if (arquivoImagem.length() > MAX_INPUT_FILE_BYTES) {
            throw new IOException("A imagem selecionada excede o limite máximo de 15 MB.");
        }

        BufferedImage buffered = ImageIO.read(arquivoImagem);
        if (buffered == null || buffered.getWidth() <= 0 || buffered.getHeight() <= 0) {
            throw new IOException("O arquivo selecionado não é uma imagem válida. Utilize PNG, JPG, JPEG, GIF ou BMP.");
        }

        byte[] finalBytes = processarImagemParaArmazenamento(arquivoImagem, buffered);
        salvarLogoBytes(finalBytes);
    }

    private byte[] processarImagemParaArmazenamento(File arquivoImagem, BufferedImage buffered) throws IOException {
        int width = buffered.getWidth();
        int height = buffered.getHeight();
        String lowerName = arquivoImagem.getName().toLowerCase(Locale.ROOT);
        boolean isNativeSupported = lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg");

        if (isNativeSupported && width <= MAX_DIMENSION && height <= MAX_DIMENSION && arquivoImagem.length() <= MAX_RAW_KEEP_BYTES) {
            return Files.readAllBytes(arquivoImagem.toPath());
        }

        BufferedImage output = buffered;
        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
            double scale = Math.min((double) MAX_DIMENSION / width, (double) MAX_DIMENSION / height);
            int newW = Math.max(1, (int) Math.round(width * scale));
            int newH = Math.max(1, (int) Math.round(height * scale));
            BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = resized.createGraphics();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.drawImage(buffered, 0, 0, newW, newH, null);
            } finally {
                g2d.dispose();
            }
            output = resized;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (!ImageIO.write(output, "png", baos)) {
            throw new IOException("Não foi possível converter a imagem para o formato PNG.");
        }
        return baos.toByteArray();
    }

    /**
     * Persiste os bytes da imagem no SQLite, atualiza cache em disco e notifica a interface.
     */
    public synchronized void salvarLogoBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            removerLogo();
            return;
        }
        String base64 = Base64.getEncoder().encodeToString(bytes);
        getConfiguracaoDAO().setValor(CONFIG_KEY_LOGO_BASE64, base64);
        this.cachedLogoBytes = bytes.clone();
        this.loadedFromDb = true;
        escreverCacheSilencioso(bytes);
        notificarAlteracaoLogo();
        LOGGER.info("Logo personalizada da empresa salva com sucesso (" + bytes.length + " bytes).");
    }

    /**
     * Remove a logo personalizada do banco de dados, memória e cache local.
     */
    public synchronized void removerLogo() {
        getConfiguracaoDAO().deleteValor(CONFIG_KEY_LOGO_BASE64);
        this.cachedLogoBytes = null;
        this.loadedFromDb = true;
        apagarCacheSilencioso();
        notificarAlteracaoLogo();
        LOGGER.info("Logo personalizada da empresa removida.");
    }

    private void escreverCacheSilencioso(byte[] bytes) {
        try {
            Path cache = getCacheFilePath();
            if (cache.getParent() != null && !Files.exists(cache.getParent())) {
                Files.createDirectories(cache.getParent());
            }
            Files.write(cache, bytes);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Não foi possível gravar cache local da logo.", e);
        }
    }

    private void apagarCacheSilencioso() {
        try {
            Files.deleteIfExists(getCacheFilePath());
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Não foi possível remover cache local da logo.", e);
        }
    }

    private void notificarAlteracaoLogo() {
        try {
            if (Platform.isFxApplicationThread()) {
                logoVersion.set(logoVersion.get() + 1);
            } else {
                Platform.runLater(() -> logoVersion.set(logoVersion.get() + 1));
            }
        } catch (IllegalStateException ignored) {
            // Toolkit JavaFX não inicializado (ex: testes headless)
            logoVersion.set(logoVersion.get() + 1);
        }
    }

    /**
     * Abre a janela modal para o usuário adicionar, alterar ou remover a logo da empresa.
     */
    public static void exibirDialogoGerenciarLogo(Window owner) {
        LogoManager manager = getInstance();

        Stage stage = new Stage();
        MainApp.applyAppIcon(stage);
        stage.setTitle("SWStock - Alterar / Adicionar Logo da Empresa");
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setResizable(false);

        VBox root = new VBox(16);
        root.setPadding(new Insets(24, 28, 22, 28));
        root.setStyle("-fx-background-color: #F8FAFC;");

        Label lblTitulo = new Label("Logo da Empresa");
        lblTitulo.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        Label lblSub = new Label("Escolha a imagem da sua empresa (PNG com fundo transparente ou JPG). Ela será exibida na tela de inicialização, barra superior e nos relatórios em PDF.");
        lblSub.setWrapText(true);
        lblSub.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");

        // Área de pré-visualização da logo
        ImageView previewView = new ImageView();
        previewView.setFitHeight(110);
        previewView.setFitWidth(340);
        previewView.setPreserveRatio(true);
        previewView.setSmooth(true);

        Label lblPlaceholder = new Label("Nenhuma logo personalizada cadastrada");
        lblPlaceholder.setStyle("-fx-font-size: 13px; -fx-text-fill: #94A3B8; -fx-font-style: italic;");

        StackPane previewBox = new StackPane(previewView, lblPlaceholder);
        previewBox.setPrefHeight(150);
        previewBox.setMaxWidth(Double.MAX_VALUE);
        previewBox.setStyle(
                "-fx-background-color: #FFFFFF; " +
                "-fx-border-color: #CBD5E1; " +
                "-fx-border-style: dashed; " +
                "-fx-border-width: 1.5px; " +
                "-fx-border-radius: 10px; " +
                "-fx-background-radius: 10px; " +
                "-fx-padding: 12px;"
        );

        Label lblFeedback = new Label("");
        lblFeedback.setWrapText(true);
        lblFeedback.setVisible(false);
        lblFeedback.setManaged(false);

        Button btnEscolher = new Button("Escolher Imagem...");
        btnEscolher.setStyle(
                "-fx-background-color: #1565C0; -fx-text-fill: #FFFFFF; " +
                "-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 9px 18px; " +
                "-fx-background-radius: 7px; -fx-cursor: hand;"
        );

        Button btnRemover = new Button("Remover Logo");
        btnRemover.setStyle(
                "-fx-background-color: #FFFFFF; -fx-text-fill: #DC2626; " +
                "-fx-border-color: #FCA5A5; -fx-border-radius: 7px; -fx-background-radius: 7px; " +
                "-fx-font-size: 13px; -fx-font-weight: 600; -fx-padding: 8px 14px; -fx-cursor: hand;"
        );

        Button btnFechar = new Button("Fechar");
        btnFechar.setStyle(
                "-fx-background-color: #E2E8F0; -fx-text-fill: #1E293B; " +
                "-fx-font-size: 13px; -fx-font-weight: 600; -fx-padding: 9px 18px; " +
                "-fx-background-radius: 7px; -fx-cursor: hand;"
        );

        Runnable atualizarPreview = () -> {
            Image current = manager.getLogoFxImage();
            if (current != null && !current.isError()) {
                previewView.setImage(current);
                previewView.setVisible(true);
                lblPlaceholder.setVisible(false);
                btnEscolher.setText("Alterar Logo...");
                btnRemover.setDisable(false);
            } else {
                previewView.setImage(null);
                previewView.setVisible(false);
                lblPlaceholder.setVisible(true);
                btnEscolher.setText("Adicionar Logo...");
                btnRemover.setDisable(true);
            }
        };
        atualizarPreview.run();

        btnEscolher.setOnAction(ev -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Selecionar Imagem da Logo");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Imagens (*.png, *.jpg, *.jpeg, *.bmp, *.gif)",
                            "*.png", "*.PNG", "*.jpg", "*.JPG", "*.jpeg", "*.JPEG", "*.bmp", "*.BMP", "*.gif", "*.GIF"),
                    new FileChooser.ExtensionFilter("Todos os Arquivos", "*.*")
            );
            File selecionado = fileChooser.showOpenDialog(stage);
            if (selecionado != null) {
                try {
                    manager.salvarLogo(selecionado);
                    atualizarPreview.run();
                    lblFeedback.setText("Logo salva com sucesso! Já aplicada na barra superior e nos PDFs.");
                    lblFeedback.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #16A34A;");
                    lblFeedback.setVisible(true);
                    lblFeedback.setManaged(true);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Falha ao salvar logo selecionada.", ex);
                    lblFeedback.setText("Erro: " + ex.getMessage());
                    lblFeedback.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #DC2626;");
                    lblFeedback.setVisible(true);
                    lblFeedback.setManaged(true);
                }
            }
        });

        btnRemover.setOnAction(ev -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.initOwner(stage);
            confirm.setTitle("Remover Logo");
            confirm.setHeaderText("Remover a logo atual da empresa?");
            confirm.setContentText("A imagem personalizada será removida do cabeçalho, da tela inicial e dos relatórios em PDF.");
            Optional<ButtonType> res = confirm.showAndWait();
            if (res.isPresent() && res.get() == ButtonType.OK) {
                manager.removerLogo();
                atualizarPreview.run();
                lblFeedback.setText("Logo removida com sucesso.");
                lblFeedback.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
                lblFeedback.setVisible(true);
                lblFeedback.setManaged(true);
            }
        });

        btnFechar.setOnAction(ev -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, btnEscolher, btnRemover, spacer, btnFechar);
        actions.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(lblTitulo, lblSub, previewBox, lblFeedback, actions);

        Scene scene = new Scene(root, 490, 390);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.showAndWait();
    }
}
