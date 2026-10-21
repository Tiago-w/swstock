package com.swstock.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object para armazenar e recuperar preferências e configurações do sistema/usuário no SQLite.
 */
public class ConfiguracaoDAO {

    private static final Logger LOGGER = Logger.getLogger(ConfiguracaoDAO.class.getName());
    private final DatabaseManager dbManager;

    public ConfiguracaoDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public ConfiguracaoDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager != null ? dbManager : DatabaseManager.getInstance();
    }

    /**
     * Retorna o valor associado a uma chave de configuração.
     */
    public String getValor(String chave, String defaultVal) {
        if (chave == null || chave.trim().isEmpty()) {
            return defaultVal;
        }
        String sql = "SELECT valor FROM configuracoes WHERE chave = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, chave.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("valor");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erro ao buscar configuração: " + chave, e);
        }
        return defaultVal;
    }

    /**
     * Salva ou atualiza uma chave de configuração.
     */
    public void setValor(String chave, String valor) {
        if (chave == null || chave.trim().isEmpty()) {
            return;
        }
        String sql = "INSERT OR REPLACE INTO configuracoes (chave, valor, updated_at) VALUES (?, ?, datetime('now', 'localtime'))";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, chave.trim());
            stmt.setString(2, valor != null ? valor : "");
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erro ao salvar configuração: " + chave, e);
        }
    }
}
