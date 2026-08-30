package com.swstock.database;

import com.swstock.model.ProdutoCor;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object para gerenciar as cores e variações de estoque dos produtos no SQLite.
 */
public class ProdutoCorDAO {

    private static final Logger LOGGER = Logger.getLogger(ProdutoCorDAO.class.getName());
    private final DatabaseManager dbManager;

    public ProdutoCorDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public ProdutoCorDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Retorna todas as cores cadastradas para um produto, ordenadas alfabeticamente.
     */
    public List<ProdutoCor> findByProdutoId(int produtoId) throws SQLException {
        List<ProdutoCor> lista = new ArrayList<>();
        String sql = "SELECT id, produto_id, nome_cor, quantidade FROM produto_cores WHERE produto_id = ? ORDER BY nome_cor ASC";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, produtoId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(new ProdutoCor(
                            rs.getInt("id"),
                            rs.getInt("produto_id"),
                            rs.getString("nome_cor"),
                            rs.getInt("quantidade")
                    ));
                }
            }
        }
        return lista;
    }

    /**
     * Retorna uma cor específica por ID.
     */
    public ProdutoCor findById(int id) throws SQLException {
        String sql = "SELECT id, produto_id, nome_cor, quantidade FROM produto_cores WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new ProdutoCor(
                            rs.getInt("id"),
                            rs.getInt("produto_id"),
                            rs.getString("nome_cor"),
                            rs.getInt("quantidade")
                    );
                }
            }
        }
        return null;
    }

    /**
     * Adiciona uma nova cor para o produto informando o funcionário responsável.
     * Regras de motivo:
     * - Sem estoque inicial: "Cadastro de cor [NOME]"
     * - Com estoque inicial: "Cadastro de cor [NOME] / Modificação de estoque"
     */
    public boolean addCor(int produtoId, String nomeCor, int quantidadeInicial, String responsavel) throws SQLException {
        if (nomeCor == null || nomeCor.trim().isEmpty()) {
            return false;
        }
        String nomeLimpo = nomeCor.trim().toUpperCase();
        String sql = "INSERT INTO produto_cores (produto_id, nome_cor, quantidade) VALUES (?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, produtoId);
            stmt.setString(2, nomeLimpo);
            stmt.setInt(3, Math.max(0, quantidadeInicial));
            int affected = stmt.executeUpdate();
            
            String resp = (responsavel != null && !responsavel.trim().isEmpty()) ? responsavel : "Não informado";
            if (affected > 0) {
                if (quantidadeInicial > 0) {
                    registrarHistoricoCor(produtoId, nomeLimpo, 0, quantidadeInicial,
                            "Cadastro de cor [" + nomeLimpo + "] / Modificação de estoque", resp);
                } else {
                    registrarHistoricoCor(produtoId, nomeLimpo, 0, 0,
                            "Cadastro de cor [" + nomeLimpo + "]", resp);
                }
            }
            return affected > 0;
        }
    }

    public boolean addCor(int produtoId, String nomeCor, int quantidadeInicial) throws SQLException {
        return addCor(produtoId, nomeCor, quantidadeInicial, "Não informado");
    }

    /**
     * Atualiza a quantidade em estoque de uma cor específica e registra no histórico:
     * Motivo: "Modificação de estoque [NOME]"
     */
    public boolean updateQuantidade(int corId, int novaQtd, String motivo, String responsavel) throws SQLException {
        ProdutoCor corAtual = findById(corId);
        if (corAtual == null) {
            return false;
        }

        novaQtd = Math.max(0, novaQtd);
        int qtdAnterior = corAtual.getQuantidade();
        if (qtdAnterior == novaQtd) {
            return true;
        }

        String sql = "UPDATE produto_cores SET quantidade = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, novaQtd);
            stmt.setInt(2, corId);
            int affected = stmt.executeUpdate();

            if (affected > 0) {
                String mot = (motivo != null && !motivo.trim().isEmpty()) ? motivo : "Modificação de estoque [" + corAtual.getNomeCor() + "]";
                registrarHistoricoCor(corAtual.getProdutoId(), corAtual.getNomeCor(), qtdAnterior, novaQtd, mot, responsavel);
            }
            return affected > 0;
        }
    }

    /**
     * Exclui uma cor de um produto informando o responsável:
     * Motivo: "Exclusão de cor [NOME]"
     */
    public boolean deleteCor(int corId, String responsavel) throws SQLException {
        ProdutoCor cor = findById(corId);
        if (cor == null) {
            return false;
        }
        String resp = (responsavel != null && !responsavel.trim().isEmpty()) ? responsavel : "Não informado";
        registrarHistoricoCor(cor.getProdutoId(), cor.getNomeCor(), cor.getQuantidade(), 0, "Exclusão de cor [" + cor.getNomeCor() + "]", resp);

        String sql = "DELETE FROM produto_cores WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, corId);
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean deleteCor(int corId) throws SQLException {
        return deleteCor(corId, "Não informado");
    }

    /**
     * Retorna a soma de unidades de todas as cores cadastradas para o produto.
     */
    public int getTotalQuantidadeCores(int produtoId) throws SQLException {
        String sql = "SELECT SUM(quantidade) as total FROM produto_cores WHERE produto_id = ?";
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
     * Verifica se o produto tem ao menos uma cor cadastrada.
     */
    public boolean hasCores(int produtoId) throws SQLException {
        String sql = "SELECT 1 FROM produto_cores WHERE produto_id = ? LIMIT 1";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, produtoId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Retorna um mapa contendo produto_id -> lista de ProdutoCor para otimização de relatórios em lote.
     */
    public java.util.Map<Integer, List<ProdutoCor>> getAllCoresAgrupadas() throws SQLException {
        java.util.Map<Integer, List<ProdutoCor>> mapa = new java.util.HashMap<>();
        String sql = "SELECT id, produto_id, nome_cor, quantidade FROM produto_cores ORDER BY nome_cor ASC";
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int pId = rs.getInt("produto_id");
                mapa.computeIfAbsent(pId, k -> new ArrayList<>()).add(new ProdutoCor(
                        rs.getInt("id"),
                        pId,
                        rs.getString("nome_cor"),
                        rs.getInt("quantidade")
                ));
            }
        }
        return mapa;
    }

    private void registrarHistoricoCor(int produtoId, String nomeCor, int anterior, int novo, String motivo, String responsavel) {
        String sql = """
            INSERT INTO historico_estoque (produto_id, tipo, quantidade_alterada, quantidade_anterior, quantidade_nova, motivo, responsavel)
            VALUES (?, ?, ?, ?, ?, ?, ?);
        """;
        int diff = novo - anterior;
        String tipo = diff > 0 ? "ENTRADA" : (diff < 0 ? "SAIDA" : "AJUSTE");
        String motivoFinal = (motivo != null && !motivo.trim().isEmpty()) ? motivo.trim() : "Modificação de estoque [" + nomeCor + "]";
        String resp = (responsavel != null && !responsavel.trim().isEmpty()) ? responsavel : "Não informado";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, produtoId);
            stmt.setString(2, tipo);
            stmt.setInt(3, Math.abs(diff));
            stmt.setInt(4, anterior);
            stmt.setInt(5, novo);
            stmt.setString(6, motivoFinal);
            stmt.setString(7, resp);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Falha ao registrar histórico para a cor " + nomeCor, e);
        }
    }
}
