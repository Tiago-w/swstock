package com.swstock;

import com.swstock.database.ConfiguracaoDAO;
import com.swstock.database.DatabaseManager;
import com.swstock.util.LogoManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LogoManagerTest {

    private static final String TEST_DB = "test_logo_manager.db";
    private static DatabaseManager dbManager;
    private static ConfiguracaoDAO configuracaoDAO;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setUpAll() {
        new File(TEST_DB).delete();
        dbManager = DatabaseManager.getInstance(TEST_DB);
        configuracaoDAO = new ConfiguracaoDAO(dbManager);
    }

    @AfterAll
    static void tearDownAll() {
        if (dbManager != null) {
            dbManager.close();
        }
        new File(TEST_DB).delete();
    }

    @BeforeEach
    void clearLogoConfig() {
        configuracaoDAO.deleteValor(LogoManager.CONFIG_KEY_LOGO_BASE64);
    }

    private File criarImagemPngTemp(String nome, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(21, 101, 192));
        g.fillRect(0, 0, width, height);
        g.dispose();

        File f = tempDir.resolve(nome).toFile();
        ImageIO.write(img, "png", f);
        return f;
    }

    @Test
    void testSemLogoInicialmente() {
        Path cachePath = tempDir.resolve("cache_vazio.img");
        LogoManager manager = new LogoManager(configuracaoDAO, cachePath);

        assertFalse(manager.hasCustomLogo());
        assertNull(manager.getLogoBytes());
        assertNull(manager.getLogoBytesForSplash());
    }

    @Test
    void testSalvarSincronizarERemoverLogo() throws Exception {
        Path cachePath = tempDir.resolve("custom_logo.img");
        LogoManager manager = new LogoManager(configuracaoDAO, cachePath);

        File imgFile = criarImagemPngTemp("minha_logo.png", 320, 120);
        int versaoAntes = manager.logoVersionProperty().get();

        manager.salvarLogo(imgFile);

        // Garante execução de eventos pendentes na thread JavaFX quando o toolkit já estiver ativo
        try {
            java.util.concurrent.CountDownLatch fxLatch = new java.util.concurrent.CountDownLatch(1);
            javafx.application.Platform.runLater(fxLatch::countDown);
            fxLatch.await(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (IllegalStateException ignored) {
            // Toolkit JavaFX não inicializado (execução headless)
        }

        assertTrue(manager.hasCustomLogo());
        assertTrue(manager.logoVersionProperty().get() > versaoAntes);
        assertNotNull(manager.getLogoBytes());
        assertTrue(Files.exists(cachePath), "Cache local deve ser gravado para a Splash Screen.");
        assertArrayEquals(manager.getLogoBytes(), manager.getLogoBytesForSplash());

        // Simula nova inicialização em outra máquina onde só existe o swstock.db (sem cache)
        Files.delete(cachePath);
        LogoManager novaInstancia = new LogoManager(configuracaoDAO, cachePath);
        assertNull(novaInstancia.getLogoBytesForSplash(), "Antes de sincronizar com banco, cache ausente retorna null.");

        novaInstancia.sincronizarComBanco();
        assertTrue(novaInstancia.hasCustomLogo());
        assertTrue(Files.exists(cachePath), "Sincronização com banco deve recriar o cache local.");

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(novaInstancia.getLogoBytes()));
        assertNotNull(decoded);
        assertEquals(320, decoded.getWidth());
        assertEquals(120, decoded.getHeight());

        // Remover logo
        novaInstancia.removerLogo();
        assertFalse(novaInstancia.hasCustomLogo());
        assertNull(novaInstancia.getLogoBytes());
        assertFalse(Files.exists(cachePath), "Cache local deve ser removido ao apagar a logo.");
    }

    @Test
    void testRedimensionamentoAutomaticoImagemGrande() throws Exception {
        Path cachePath = tempDir.resolve("large_logo.img");
        LogoManager manager = new LogoManager(configuracaoDAO, cachePath);

        File bigImg = criarImagemPngTemp("grande.png", 2000, 1000);
        manager.salvarLogo(bigImg);

        byte[] saved = manager.getLogoBytes();
        assertNotNull(saved);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(saved));
        assertNotNull(decoded);
        assertEquals(1200, decoded.getWidth());
        assertEquals(600, decoded.getHeight());
    }

    @Test
    void testRejeitaArquivoInvalido() throws Exception {
        Path cachePath = tempDir.resolve("invalid_logo.img");
        LogoManager manager = new LogoManager(configuracaoDAO, cachePath);

        File textoFake = tempDir.resolve("fake.png").toFile();
        Files.writeString(textoFake.toPath(), "nao e uma imagem valida");

        assertThrows(IOException.class, () -> manager.salvarLogo(textoFake));
        assertFalse(manager.hasCustomLogo());
    }

    @Test
    void testGeracaoPdfComESemLogoPersonalizada() throws Exception {
        Path cachePath = tempDir.resolve("pdf_logo.img");
        LogoManager manager = new LogoManager(configuracaoDAO, cachePath);
        com.swstock.service.PdfReportService pdfService = new com.swstock.service.PdfReportService(manager);

        File pdfSemLogo = tempDir.resolve("relatorio_sem_logo.pdf").toFile();
        pdfService.gerarRelatorio(pdfSemLogo, java.util.List.of(), null, null, "Todos", null, null);
        assertTrue(pdfSemLogo.exists());
        assertTrue(pdfSemLogo.length() > 300);

        File imgFile = criarImagemPngTemp("logo_para_pdf.png", 300, 120);
        manager.salvarLogo(imgFile);

        File pdfComLogo = tempDir.resolve("relatorio_com_logo.pdf").toFile();
        pdfService.gerarRelatorio(pdfComLogo, java.util.List.of(), null, null, "Todos", null, null);
        assertTrue(pdfComLogo.exists());
        assertTrue(pdfComLogo.length() > pdfSemLogo.length(), "PDF com logo personalizada deve embutir a imagem.");
    }
}
