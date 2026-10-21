package com.swstock.util;

import com.swstock.database.ConfiguracaoDAO;
import com.swstock.database.DatabaseManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utilitário responsável por salvar e restaurar automaticamente a ordem e largura
 * das colunas de qualquer TableView JavaFX no SWStock.
 */
public class TablePreferencesHelper {

    private static final Logger LOGGER = Logger.getLogger(TablePreferencesHelper.class.getName());
    private static final String PREF_PREFIX = "table_columns_";

    /**
     * Vincula a TableView à persistência automática no SQLite e em arquivo de fallback.
     */
    public static void vincular(TableView<?> table, String tableKey, DatabaseManager dbManager) {
        if (table == null || tableKey == null || tableKey.trim().isEmpty()) {
            return;
        }

        ConfiguracaoDAO configDAO = new ConfiguracaoDAO(dbManager);
        File localFile = getLocalPreferencesFile(dbManager);

        AtomicBoolean isRestoring = new AtomicBoolean(false);
        PauseTransition debounce = new PauseTransition(Duration.millis(300));

        // 1. Restaura ordem e larguras previamente salvas
        restaurar(table, tableKey, configDAO, localFile, isRestoring);

        // 2. Ação de salvamento suave (debounced)
        Runnable dispararSalvamento = () -> {
            if (isRestoring.get()) return;
            debounce.setOnFinished(e -> salvar(table, tableKey, configDAO, localFile));
            debounce.playFromStart();
        };

        // 3. Monitora reordenação de colunas (arrastar e soltar cabeçalhos)
        table.getColumns().addListener((ListChangeListener<TableColumn<?, ?>>) change -> {
            if (isRestoring.get()) return;
            dispararSalvamento.run();
            // Garante que novas colunas reordenadas mantenham seus listeners de redimensionamento
            configurarListenersLargura(table, dispararSalvamento);
        });

        // 4. Monitora redimensionamento de colunas
        configurarListenersLargura(table, dispararSalvamento);

        // 5. Garante salvamento no fechamento da janela
        vincularFechamentoJanela(table, () -> salvar(table, tableKey, configDAO, localFile));
    }

    /**
     * Configura listeners de alteração de largura para todas as colunas da tabela.
     */
    private static void configurarListenersLargura(TableView<?> table, Runnable dispararSalvamento) {
        for (TableColumn<?, ?> col : table.getColumns()) {
            if (col.getProperties().get("swstock_width_listener_attached") == null) {
                col.getProperties().put("swstock_width_listener_attached", Boolean.TRUE);
                col.widthProperty().addListener((obs, oldW, newW) -> dispararSalvamento.run());
            }
        }
    }

    /**
     * Restaura a ordem e larguras salvas na TableView.
     */
    public static void restaurar(TableView<?> table, String tableKey, ConfiguracaoDAO configDAO, File localFile, AtomicBoolean isRestoring) {
        String chave = PREF_PREFIX + tableKey.trim();
        String configStr = configDAO != null ? configDAO.getValor(chave, null) : null;

        if ((configStr == null || configStr.trim().isEmpty()) && localFile != null && localFile.exists()) {
            configStr = lerArquivoLocal(localFile, chave);
        }

        if (configStr == null || configStr.trim().isEmpty()) {
            return;
        }

        Map<String, TableColumn<?, ?>> colMap = new HashMap<>();
        for (TableColumn<?, ?> col : table.getColumns()) {
            if (col.getId() != null && !col.getId().trim().isEmpty()) {
                colMap.put(col.getId().toLowerCase().trim(), col);
            }
            if (col.getText() != null && !col.getText().trim().isEmpty()) {
                colMap.put(col.getText().toLowerCase().trim(), col);
            }
        }

        List<TableColumn<?, ?>> novaOrdem = new ArrayList<>();
        Set<TableColumn<?, ?>> adicionados = new HashSet<>();

        String[] entries = configStr.split(";");
        for (String entry : entries) {
            if (entry == null || entry.trim().isEmpty()) continue;
            String[] parts = entry.split(":");
            String colIdentifier = parts[0].toLowerCase().trim();
            TableColumn<?, ?> col = colMap.get(colIdentifier);
            if (col != null && !adicionados.contains(col)) {
                novaOrdem.add(col);
                adicionados.add(col);
                if (parts.length > 1) {
                    try {
                        double w = Double.parseDouble(parts[1].trim());
                        if (w >= 30.0) {
                            col.setPrefWidth(w);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // Adiciona colunas existentes que não estavam na configuração salva
        for (TableColumn<?, ?> col : table.getColumns()) {
            if (!adicionados.contains(col)) {
                novaOrdem.add(col);
            }
        }

        if (!novaOrdem.isEmpty() && novaOrdem.size() == table.getColumns().size()) {
            if (isRestoring != null) isRestoring.set(true);
            try {
                table.getColumns().setAll((List) novaOrdem);
            } finally {
                if (isRestoring != null) isRestoring.set(false);
            }
        }
    }

    /**
     * Serializa e salva a ordem e larguras atuais das colunas da TableView.
     */
    public static void salvar(TableView<?> table, String tableKey, ConfiguracaoDAO configDAO, File localFile) {
        if (table == null || tableKey == null) return;

        StringBuilder sb = new StringBuilder();
        for (TableColumn<?, ?> col : table.getColumns()) {
            String colId = col.getId();
            if (colId == null || colId.trim().isEmpty()) {
                colId = col.getText();
            }
            if (colId != null && !colId.trim().isEmpty()) {
                if (sb.length() > 0) sb.append(";");
                double width = col.getWidth() >= 30.0 ? col.getWidth() : col.getPrefWidth();
                sb.append(colId.trim()).append(":").append((long) Math.round(width));
            }
        }

        String valor = sb.toString();
        String chave = PREF_PREFIX + tableKey.trim();

        if (configDAO != null) {
            configDAO.setValor(chave, valor);
        }

        if (localFile != null) {
            salvarArquivoLocal(localFile, chave, valor);
        }
    }

    private static void vincularFechamentoJanela(TableView<?> table, Runnable salvarAcao) {
        table.sceneProperty().addListener((obsS, oldS, newS) -> {
            if (newS != null) {
                newS.windowProperty().addListener((obsW, oldW, newW) -> {
                    if (newW != null) {
                        newW.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, e -> salvarAcao.run());
                    }
                });
            }
        });

        // Shutdown hook como garantia final caso o processo seja finalizado
        Runtime.getRuntime().addShutdownHook(new Thread(salvarAcao::run));
    }

    private static File getLocalPreferencesFile(DatabaseManager dbManager) {
        try {
            String dbPath = (dbManager != null) ? dbManager.getDbPath() : null;
            if (dbPath != null && !dbPath.trim().isEmpty()) {
                File dbFile = new File(dbPath);
                File dir = dbFile.getParentFile();
                if (dir != null && dir.exists()) {
                    return new File(dir, "table_preferences.properties");
                }
            }
            Path userHome = Path.of(System.getProperty("user.home"), ".swstock");
            if (!java.nio.file.Files.exists(userHome)) {
                java.nio.file.Files.createDirectories(userHome);
            }
            return userHome.resolve("table_preferences.properties").toFile();
        } catch (Exception e) {
            return null;
        }
    }

    private static String lerArquivoLocal(File file, String chave) {
        try {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);
            }
            return props.getProperty(chave);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void salvarArquivoLocal(File file, String chave, String valor) {
        try {
            Properties props = new Properties();
            if (file.exists()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    props.load(fis);
                } catch (Exception ignored) {}
            }
            props.setProperty(chave, valor);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                props.store(fos, "SWStock Table Preferences");
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Falha ao gravar arquivo local de preferências: " + e.getMessage());
        }
    }
}
