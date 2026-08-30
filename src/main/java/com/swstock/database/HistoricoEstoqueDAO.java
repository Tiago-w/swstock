package com.swstock.database;

import com.swstock.model.HistoricoEstoque;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object para a tabela 'historico_estoque'.
 * Permite inserção e consultas filtradas por produto, fabricante/grupo, dia, mês e ano.
 */
public class HistoricoEstoqueDAO {

    private static final Logger LOGGER = Logger.getLogger(HistoricoEstoqueDAO.class.getName());
    private final DatabaseManager databaseManager;

    public HistoricoEstoqueDAO() {
        this(DatabaseManager.getInstance());
    }

    public HistoricoEstoqueDAO(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    private Connection getConnection() {
        return databaseManager.getConnection();
    }

    /**
     * Registra uma nova movimentação no histórico de estoque.
     */
    public HistoricoEstoque insert(HistoricoEstoque historico) throws SQLException {
        String sql = """
            INSERT INTO historico_estoque (produto_id, data_hora, tipo, quantidade_alterada, quantidade_anterior, quantidade_nova, motivo, responsavel)
            VALUES (?, COALESCE(?, datetime('now', 'localtime')), ?, ?, ?, ?, ?, ?);
            """;

        try (PreparedStatement stmt = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, historico.getProdutoId());
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
            LOGGER.log(Level.SEVERE, "Erro ao registrar histórico de estoque para produtoId=" + historico.getProdutoId(), e);
            throw e;
        }
    }

    /**
     * Busca todo o histórico de um produto ordenado da movimentação mais recente para a mais antiga.
     */
    public List<HistoricoEstoque> findByProduto(int produtoId) throws SQLException {
        String sql = """
            SELECT id, produto_id, data_hora, tipo, quantidade_alterada, quantidade_anterior, quantidade_nova, motivo, responsavel
            FROM historico_estoque
            WHERE produto_id = ?
            ORDER BY datetime(data_hora) DESC, id DESC;
            """;

        List<HistoricoEstoque> lista = new ArrayList<>();
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, produtoId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapResultSet(rs));
                }
            }
        }
        return lista;
    }

    /**
     * Busca histórico de um produto filtrado por uma data específica (Dia/Mês/Ano).
     */
    public List<HistoricoEstoque> findByProdutoAndDate(int produtoId, LocalDate data) throws SQLException {
        if (data == null) {
            return findByProduto(produtoId);
        }

        String dataStr = data.toString();
        String sql = """
            SELECT id, produto_id, data_hora, tipo, quantidade_alterada, quantidade_anterior, quantidade_nova, motivo, responsavel
            FROM historico_estoque
            WHERE produto_id = ? AND strftime('%Y-%m-%d', data_hora) = ?
            ORDER BY datetime(data_hora) DESC, id DESC;
            """;

        List<HistoricoEstoque> lista = new ArrayList<>();
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, produtoId);
            stmt.setString(2, dataStr);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapResultSet(rs));
                }
            }
        }
        return lista;
    }

    /**
     * Busca histórico de um produto filtrado por mês e ano.
     */
    public List<HistoricoEstoque> findByProdutoAndMonthYear(int produtoId, int mes, int ano) throws SQLException {
        String mesAnoStr = String.format("%04d-%02d", ano, mes);
        String sql = """
            SELECT id, produto_id, data_hora, tipo, quantidade_alterada, quantidade_anterior, quantidade_nova, motivo, responsavel
            FROM historico_estoque
            WHERE produto_id = ? AND strftime('%Y-%m', data_hora) = ?
            ORDER BY datetime(data_hora) DESC, id DESC;
            """;

        List<HistoricoEstoque> lista = new ArrayList<>();
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, produtoId);
            stmt.setString(2, mesAnoStr);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapResultSet(rs));
                }
            }
        }
        return lista;
    }

    /**
     * Calcula o total acumulado de unidades adicionadas para o produto.
     */
    public int getTotalAdicionado(int produtoId) {
        String sql = "SELECT SUM(quantidade_alterada) FROM historico_estoque WHERE produto_id = ? AND quantidade_alterada > 0;";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, produtoId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erro ao calcular total adicionado", e);
        }
        return 0;
    }

    /**
     * Calcula o total acumulado de unidades subtraídas para o produto.
     */
    public int getTotalSubtraido(int produtoId) {
        String sql = "SELECT SUM(ABS(quantidade_alterada)) FROM historico_estoque WHERE produto_id = ? AND quantidade_alterada < 0;";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, produtoId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erro ao calcular total subtraído", e);
        }
        return 0;
    }

    /**
     * Busca todas as movimentações globais de TODOS os produtos, com suporte a filtros de data, tipo, grupo e busca textual.
     */
    public List<HistoricoEstoque> findAllGlobal(LocalDate dataInicio, LocalDate dataFim, String tipoFiltro, String grupoFiltro, String termoBusca) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT h.id, h.produto_id, h.data_hora, h.tipo, h.quantidade_alterada, h.quantidade_anterior, h.quantidade_nova, h.motivo, h.responsavel,
                   p.nome AS produto_nome, p.codigoLoja AS produto_codigo, p.localizacao AS produto_localizacao, p.grupo AS produto_grupo
            FROM historico_estoque h
            JOIN produtos p ON p.id = h.produto_id
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

        if (tipoFiltro != null && !tipoFiltro.trim().isEmpty()) {
            String tf = tipoFiltro.trim().toUpperCase();
            if (!tf.contains("TODOS")) {
                if (tf.contains("ENTRADA")) {
                    sql.append(" AND (UPPER(h.tipo) = 'ENTRADA' OR h.quantidade_alterada > 0) ");
                } else if (tf.contains("SAIDA") || tf.contains("SAÍDA")) {
                    sql.append(" AND (UPPER(h.tipo) = 'SAIDA' OR h.quantidade_alterada < 0) ");
                }
            }
        }

        if (grupoFiltro != null && !grupoFiltro.trim().isEmpty()) {
            String gf = grupoFiltro.trim().toUpperCase();
            if (!gf.contains("TODOS")) {
                sql.append(" AND UPPER(TRIM(p.grupo)) = ? ");
                params.add(grupoFiltro.trim().toUpperCase());
            }
        }

        if (termoBusca != null && !termoBusca.trim().isEmpty()) {
            sql.append(" AND (p.nome LIKE ? OR p.codigoLoja LIKE ? OR p.grupo LIKE ? OR p.localizacao LIKE ? OR h.motivo LIKE ? OR h.responsavel LIKE ?) ");
            String wild = "%" + termoBusca.trim() + "%";
            params.add(wild);
            params.add(wild);
            params.add(wild);
            params.add(wild);
            params.add(wild);
            params.add(wild);
        }

        sql.append(" ORDER BY h.data_hora DESC, h.id DESC;");

        List<HistoricoEstoque> lista = new ArrayList<>();
        try (PreparedStatement stmt = getConnection().prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapResultSetGlobal(rs));
                }
            }
        }
        return lista;
    }

    public List<HistoricoEstoque> findAllGlobal(LocalDate dataInicio, LocalDate dataFim, String tipoFiltro, String termoBusca) throws SQLException {
        return findAllGlobal(dataInicio, dataFim, tipoFiltro, null, termoBusca);
    }

    /**
     * Busca especificamente as saídas de estoque (produtos pegos) para geração de relatório em PDF.
     */
    public List<HistoricoEstoque> findSaidasParaRelatorio(LocalDate dataInicio, LocalDate dataFim) throws SQLException {
        return findSaidasParaRelatorio(dataInicio, dataFim, null);
    }

    public List<HistoricoEstoque> findSaidasParaRelatorio(LocalDate dataInicio, LocalDate dataFim, String grupoFiltro) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT h.id, h.produto_id, h.data_hora, h.tipo, h.quantidade_alterada, h.quantidade_anterior, h.quantidade_nova, h.motivo, h.responsavel,
                   p.nome AS produto_nome, p.codigoLoja AS produto_codigo, p.localizacao AS produto_localizacao, p.grupo AS produto_grupo
            FROM historico_estoque h
            JOIN produtos p ON p.id = h.produto_id
            WHERE (h.tipo = 'SAIDA' OR h.quantidade_alterada < 0)
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

        if (grupoFiltro != null && !grupoFiltro.trim().isEmpty() && !grupoFiltro.toUpperCase().contains("TODOS")) {
            sql.append(" AND UPPER(TRIM(p.grupo)) = ? ");
            params.add(grupoFiltro.trim().toUpperCase());
        }

        sql.append(" ORDER BY strftime('%Y-%m-%d', h.data_hora) DESC, datetime(h.data_hora) DESC, h.id DESC;");

        List<HistoricoEstoque> lista = new ArrayList<>();
        try (PreparedStatement stmt = getConnection().prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapResultSetGlobal(rs));
                }
            }
        }
        return lista;
    }

    /**
     * Retorna todos os grupos de fabricantes cadastrados no sistema.
     */
    public List<String> getAllGrupos() throws SQLException {
        String sql = """
            SELECT DISTINCT UPPER(TRIM(grupo)) as g
            FROM produtos
            WHERE grupo IS NOT NULL AND TRIM(grupo) != ''
            ORDER BY g ASC;
            """;
        List<String> grupos = new ArrayList<>();
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                grupos.add(rs.getString("g"));
            }
        }
        return grupos;
    }

    private HistoricoEstoque mapResultSet(ResultSet rs) throws SQLException {
        return new HistoricoEstoque(
                rs.getInt("id"),
                rs.getInt("produto_id"),
                rs.getString("data_hora"),
                rs.getString("tipo"),
                rs.getInt("quantidade_alterada"),
                rs.getInt("quantidade_anterior"),
                rs.getInt("quantidade_nova"),
                rs.getString("motivo"),
                rs.getString("responsavel")
        );
    }

    private HistoricoEstoque mapResultSetGlobal(ResultSet rs) throws SQLException {
        String grupo = "GERAL";
        try {
            grupo = rs.getString("produto_grupo");
            if (grupo == null || grupo.trim().isEmpty()) {
                grupo = "GERAL";
            }
        } catch (SQLException ignored) {}

        return new HistoricoEstoque(
                rs.getInt("id"),
                rs.getInt("produto_id"),
                rs.getString("data_hora"),
                rs.getString("tipo"),
                rs.getInt("quantidade_alterada"),
                rs.getInt("quantidade_anterior"),
                rs.getInt("quantidade_nova"),
                rs.getString("motivo"),
                rs.getString("responsavel"),
                rs.getString("produto_nome"),
                rs.getString("produto_codigo"),
                rs.getString("produto_localizacao"),
                grupo
        );
    }
}
