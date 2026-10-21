package com.swstock.database;

import com.swstock.model.ProdutoSubcategoria;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object para gerenciar as subcategorias e variações de estoque dos produtos no SQLite.
 */
public class ProdutoSubcategoriaDAO {

    private static final Logger LOGGER = Logger.getLogger(ProdutoSubcategoriaDAO.class.getName());
    private final DatabaseManager dbManager;

    public ProdutoSubcategoriaDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public ProdutoSubcategoriaDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public DatabaseManager getDatabaseManager() {
        return dbManager;
    }

    /**
     * Retorna todas as subcategorias cadastradas para um produto, ordenadas alfabeticamente.
     */
    public List<ProdutoSubcategoria> findByProdutoId(int produtoId) throws SQLException {
        List<ProdutoSubcategoria> lista = new ArrayList<>();
        String sql = "SELECT id, produto_id, nome_subcategoria, quantidade FROM produto_subcategorias WHERE produto_id = ? ORDER BY nome_subcategoria ASC";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, produtoId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(new ProdutoSubcategoria(
                            rs.getInt("id"),
                            rs.getInt("produto_id"),
                            rs.getString("nome_subcategoria"),
                            rs.getInt("quantidade")
                    ));
                }
            }
        }
        return lista;
    }

    /**
     * Retorna uma subcategoria específica por ID.
     */
    public ProdutoSubcategoria findById(int id) throws SQLException {
        String sql = "SELECT id, produto_id, nome_subcategoria, quantidade FROM produto_subcategorias WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new ProdutoSubcategoria(
                            rs.getInt("id"),
                            rs.getInt("produto_id"),
                            rs.getString("nome_subcategoria"),
                            rs.getInt("quantidade")
                    );
                }
            }
        }
        return null;
    }

    /**
     * Adiciona uma nova subcategoria para o produto informando o funcionário responsável.
     */
    public boolean addSubcategoria(int produtoId, String nomeSubcategoria, int quantidadeInicial, String responsavel) throws SQLException {
        if (nomeSubcategoria == null || nomeSubcategoria.trim().isEmpty()) {
            return false;
        }
        String nomeLimpo = nomeSubcategoria.trim().toUpperCase();
        String sql = "INSERT INTO produto_subcategorias (produto_id, nome_subcategoria, quantidade) VALUES (?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, produtoId);
            stmt.setString(2, nomeLimpo);
            stmt.setInt(3, Math.max(0, quantidadeInicial));
            int affected = stmt.executeUpdate();

            String resp = (responsavel != null && !responsavel.trim().isEmpty()) ? responsavel : "Não informado";
            if (affected > 0) {
                salvarNoCatalogo(nomeLimpo);
                if (quantidadeInicial > 0) {
                    registrarHistoricoSubcategoria(produtoId, nomeLimpo, 0, quantidadeInicial,
                            "Cadastro de subcategoria [" + nomeLimpo + "] / Modificação de estoque", resp);
                } else {
                    registrarHistoricoSubcategoria(produtoId, nomeLimpo, 0, 0,
                            "Cadastro de subcategoria [" + nomeLimpo + "]", resp);
                }
            }
            return affected > 0;
        }
    }

    public boolean addSubcategoria(int produtoId, String nomeSubcategoria, int quantidadeInicial) throws SQLException {
        return addSubcategoria(produtoId, nomeSubcategoria, quantidadeInicial, "Não informado");
    }

    /**
     * Atualiza a quantidade em estoque de uma subcategoria específica e registra no histórico.
     */
    public boolean updateQuantidade(int subcategoriaId, int novaQtd, String motivo, String responsavel) throws SQLException {
        ProdutoSubcategoria subAtual = findById(subcategoriaId);
        if (subAtual == null) {
            return false;
        }

        novaQtd = Math.max(0, novaQtd);
        int qtdAnterior = subAtual.getQuantidade();
        if (qtdAnterior == novaQtd) {
            return true;
        }

        String sql = "UPDATE produto_subcategorias SET quantidade = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, novaQtd);
            stmt.setInt(2, subcategoriaId);
            int affected = stmt.executeUpdate();

            if (affected > 0) {
                String mot = (motivo != null && !motivo.trim().isEmpty()) ? motivo : "Modificação de estoque [" + subAtual.getNomeSubcategoria() + "]";
                registrarHistoricoSubcategoria(subAtual.getProdutoId(), subAtual.getNomeSubcategoria(), qtdAnterior, novaQtd, mot, responsavel);
            }
            return affected > 0;
        }
    }

    /**
     * Exclui uma subcategoria de um produto informando o responsável.
     */
    public boolean deleteSubcategoria(int subcategoriaId, String responsavel) throws SQLException {
        ProdutoSubcategoria sub = findById(subcategoriaId);
        if (sub == null) {
            return false;
        }
        String resp = (responsavel != null && !responsavel.trim().isEmpty()) ? responsavel : "Não informado";
        registrarHistoricoSubcategoria(sub.getProdutoId(), sub.getNomeSubcategoria(), sub.getQuantidade(), 0, "Exclusão de subcategoria [" + sub.getNomeSubcategoria() + "]", resp);

        String sql = "DELETE FROM produto_subcategorias WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, subcategoriaId);
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean deleteSubcategoria(int subcategoriaId) throws SQLException {
        return deleteSubcategoria(subcategoriaId, "Não informado");
    }

    /**
     * Retorna a soma de unidades de todas as subcategorias cadastradas para o produto.
     */
    public int getTotalQuantidadeSubcategorias(int produtoId) throws SQLException {
        String sql = "SELECT SUM(quantidade) as total FROM produto_subcategorias WHERE produto_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, produtoId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total");
                }
            }
        }
        return 0;
    }

    /**
     * Verifica se o produto tem ao menos uma subcategoria cadastrada.
     */
    public boolean hasSubcategorias(int produtoId) throws SQLException {
        String sql = "SELECT 1 FROM produto_subcategorias WHERE produto_id = ? LIMIT 1";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, produtoId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Retorna um mapa contendo produto_id -> lista de ProdutoSubcategoria para otimização em lote.
     */
    public Map<Integer, List<ProdutoSubcategoria>> getAllSubcategoriasAgrupadas() throws SQLException {
        Map<Integer, List<ProdutoSubcategoria>> mapa = new HashMap<>();
        String sql = "SELECT id, produto_id, nome_subcategoria, quantidade FROM produto_subcategorias ORDER BY nome_subcategoria ASC";
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int pId = rs.getInt("produto_id");
                mapa.computeIfAbsent(pId, k -> new ArrayList<>()).add(new ProdutoSubcategoria(
                        rs.getInt("id"),
                        pId,
                        rs.getString("nome_subcategoria"),
                        rs.getInt("quantidade")
                ));
            }
        }
        return mapa;
    }

    private void registrarHistoricoSubcategoria(int produtoId, String nomeSub, int anterior, int novo, String motivo, String responsavel) {
        String sql = """
            INSERT INTO historico_estoque (produto_id, tipo, quantidade_alterada, quantidade_anterior, quantidade_nova, motivo, responsavel)
            VALUES (?, ?, ?, ?, ?, ?, ?);
        """;
        int diff = novo - anterior;
        String tipo = diff > 0 ? "ENTRADA" : (diff < 0 ? "SAIDA" : "AJUSTE");
        String motivoFinal = (motivo != null && !motivo.trim().isEmpty()) ? motivo.trim() : "Modificação de estoque [" + nomeSub + "]";
        String resp = (responsavel != null && !responsavel.trim().isEmpty()) ? responsavel : "Não informado";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, produtoId);
            stmt.setString(2, tipo);
            stmt.setInt(3, diff);
            stmt.setInt(4, anterior);
            stmt.setInt(5, novo);
            stmt.setString(6, motivoFinal);
            stmt.setString(7, resp);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Falha ao registrar histórico para a subcategoria " + nomeSub, e);
        }
    }

    /**
     * Salva um nome de subcategoria no catálogo global (para sugestões e seleção sem digitação).
     */
    public boolean salvarNoCatalogo(String nomeSubcategoria) {
        if (nomeSubcategoria == null || nomeSubcategoria.trim().isEmpty()) {
            return false;
        }
        String sql = "INSERT OR IGNORE INTO catalogo_subcategorias (nome) VALUES (?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nomeSubcategoria.trim().toUpperCase());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erro ao salvar subcategoria no catálogo: " + nomeSubcategoria, e);
            return false;
        }
    }

    /**
     * Retorna todas as subcategorias únicas já cadastradas no sistema (catálogo global + produtos).
     */
    public List<String> getCatalogoSubcategorias() {
        List<String> catalogo = new ArrayList<>();
        String sql = """
            SELECT DISTINCT UPPER(TRIM(nome)) AS sub FROM catalogo_subcategorias WHERE nome IS NOT NULL AND TRIM(nome) != ''
            UNION
            SELECT DISTINCT UPPER(TRIM(nome_subcategoria)) AS sub FROM produto_subcategorias WHERE nome_subcategoria IS NOT NULL AND TRIM(nome_subcategoria) != ''
            UNION
            SELECT DISTINCT UPPER(TRIM(subcategoria)) AS sub FROM produtos WHERE subcategoria IS NOT NULL AND TRIM(subcategoria) != ''
            ORDER BY 1 ASC;
        """;
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String sub = rs.getString("sub");
                if (sub != null && !sub.trim().isEmpty()) {
                    catalogo.add(sub.trim());
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erro ao buscar catálogo de subcategorias.", e);
        }
        return catalogo;
    }
}
