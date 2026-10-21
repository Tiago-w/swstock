package com.swstock.database;

import com.swstock.model.HistoricoBase;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object para a tabela 'historico_bases'.
 * Registra e consulta o histórico isolado de movimentações de estoque das Bases.
 */
public class HistoricoBaseDAO {

    private static final Logger LOGGER = Logger.getLogger(HistoricoBaseDAO.class.getName());
    private final DatabaseManager databaseManager;

    public HistoricoBaseDAO() {
        this(DatabaseManager.getInstance());
    }

    public HistoricoBaseDAO(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    private Connection getConnection() {
        return databaseManager.getConnection();
    }

    public HistoricoBase insert(HistoricoBase historico) throws SQLException {
        String sql = """
            INSERT INTO historico_bases (base_id, data_hora, tipo, quantidade_alterada, quantidade_anterior, quantidade_nova, motivo, responsavel)
            VALUES (?, COALESCE(?, datetime('now', 'localtime')), ?, ?, ?, ?, ?, ?);
        """;

        try (PreparedStatement stmt = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, historico.getBaseId());
            stmt.setString(2, historico.getDataHora());
            stmt.setString(3, historico.getTipo());
            stmt.setInt(4, historico.getQuantidadeAlterada() != null ? historico.getQuantidadeAlterada() : 0);
            stmt.setInt(5, historico.getQuantidadeAnterior() != null ? historico.getQuantidadeAnterior() : 0);
            stmt.setInt(6, historico.getQuantidadeNova() != null ? historico.getQuantidadeNova() : 0);
            stmt.setString(7, historico.getMotivo());
            stmt.setString(8, historico.getResponsavel());

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    historico.setId(rs.getInt(1));
                }
            }
            return historico;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao registrar histórico de base para baseId=" + historico.getBaseId(), e);
            throw e;
        }
    }

    public List<HistoricoBase> findByBase(int baseId) throws SQLException {
        String sql = """
            SELECT h.id, h.base_id, h.data_hora, h.tipo, h.quantidade_alterada, h.quantidade_anterior, h.quantidade_nova, h.motivo, h.responsavel,
                   b.tamanho AS base_codigo, b.nome AS base_nome, b.tipo AS base_tipo, b.fabricante AS base_fabricante
            FROM historico_bases h
            JOIN bases b ON b.id = h.base_id
            WHERE h.base_id = ?
            ORDER BY datetime(h.data_hora) DESC, h.id DESC;
        """;

        List<HistoricoBase> lista = new ArrayList<>();
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, baseId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapResultSet(rs));
                }
            }
        }
        return lista;
    }

    public List<HistoricoBase> findAllGlobal(LocalDate dataInicio, LocalDate dataFim, String tipoBaseFiltro, String tipoOperacaoFiltro, String termoBusca) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT h.id, h.base_id, h.data_hora, h.tipo, h.quantidade_alterada, h.quantidade_anterior, h.quantidade_nova, h.motivo, h.responsavel,
                   b.tamanho AS base_codigo, b.nome AS base_nome, b.tipo AS base_tipo, b.fabricante AS base_fabricante
            FROM historico_bases h
            JOIN bases b ON b.id = h.base_id
            WHERE 1=1
        """);

        List<Object> params = new ArrayList<>();

        if (dataInicio != null && dataFim != null) {
            sql.append(" AND strftime('%Y-%m-%d', h.data_hora) BETWEEN ? AND ? ");
            params.add(dataInicio.toString());
            params.add(dataFim.toString());
        } else if (dataInicio != null) {
            sql.append(" AND strftime('%Y-%m-%d', h.data_hora) = ? ");
            params.add(dataInicio.toString());
        }

        if (tipoBaseFiltro != null && !tipoBaseFiltro.trim().isEmpty() && !tipoBaseFiltro.toUpperCase().contains("TODAS")) {
            sql.append(" AND UPPER(b.tipo) = ? ");
            params.add(tipoBaseFiltro.trim().toUpperCase());
        }

        if (tipoOperacaoFiltro != null && !tipoOperacaoFiltro.trim().isEmpty() && !tipoOperacaoFiltro.toUpperCase().contains("TODAS")) {
            String op = tipoOperacaoFiltro.trim().toUpperCase();
            if (op.contains("ENTRADA")) {
                sql.append(" AND (UPPER(h.tipo) = 'ENTRADA' OR h.quantidade_alterada > 0) ");
            } else if (op.contains("SAIDA") || op.contains("SAÍDA")) {
                sql.append(" AND (UPPER(h.tipo) = 'SAIDA' OR h.quantidade_alterada < 0) ");
            } else if (op.contains("AJUSTE")) {
                sql.append(" AND UPPER(h.tipo) = 'AJUSTE' ");
            }
        }

        if (termoBusca != null && !termoBusca.trim().isEmpty()) {
            sql.append(" AND (UNACCENT(b.tamanho) LIKE ? OR UNACCENT(b.nome) LIKE ? OR UNACCENT(b.fabricante) LIKE ? OR UNACCENT(h.motivo) LIKE ? OR UNACCENT(h.responsavel) LIKE ?) ");
            String wild = "%" + DatabaseManager.unaccent(termoBusca.trim()) + "%";
            params.add(wild);
            params.add(wild);
            params.add(wild);
            params.add(wild);
            params.add(wild);
        }

        sql.append(" ORDER BY datetime(h.data_hora) DESC, h.id DESC;");

        List<HistoricoBase> lista = new ArrayList<>();
        try (PreparedStatement stmt = getConnection().prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapResultSet(rs));
                }
            }
        }
        return lista;
    }

    public int getTotalAdicionado(String tipoBaseFiltro) {
        StringBuilder sql = new StringBuilder("""
            SELECT SUM(h.quantidade_alterada)
            FROM historico_bases h
            JOIN bases b ON b.id = h.base_id
            WHERE h.quantidade_alterada > 0
        """);
        List<Object> params = new ArrayList<>();
        if (tipoBaseFiltro != null && !tipoBaseFiltro.trim().isEmpty() && !tipoBaseFiltro.toUpperCase().contains("TODAS")) {
            sql.append(" AND UPPER(b.tipo) = ? ");
            params.add(tipoBaseFiltro.trim().toUpperCase());
        }

        try (PreparedStatement stmt = getConnection().prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erro ao calcular total adicionado de bases", e);
        }
        return 0;
    }

    public int getTotalSubtraido(String tipoBaseFiltro) {
        StringBuilder sql = new StringBuilder("""
            SELECT SUM(ABS(h.quantidade_alterada))
            FROM historico_bases h
            JOIN bases b ON b.id = h.base_id
            WHERE h.quantidade_alterada < 0
        """);
        List<Object> params = new ArrayList<>();
        if (tipoBaseFiltro != null && !tipoBaseFiltro.trim().isEmpty() && !tipoBaseFiltro.toUpperCase().contains("TODAS")) {
            sql.append(" AND UPPER(b.tipo) = ? ");
            params.add(tipoBaseFiltro.trim().toUpperCase());
        }

        try (PreparedStatement stmt = getConnection().prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erro ao calcular total subtraído de bases", e);
        }
        return 0;
    }

    private HistoricoBase mapResultSet(ResultSet rs) throws SQLException {
        return new HistoricoBase(
                rs.getInt("id"),
                rs.getInt("base_id"),
                rs.getString("data_hora"),
                rs.getString("tipo"),
                rs.getInt("quantidade_alterada"),
                rs.getInt("quantidade_anterior"),
                rs.getInt("quantidade_nova"),
                rs.getString("motivo"),
                rs.getString("responsavel"),
                rs.getString("base_codigo"),
                rs.getString("base_nome"),
                rs.getString("base_tipo"),
                rs.getString("base_fabricante")
        );
    }
}
