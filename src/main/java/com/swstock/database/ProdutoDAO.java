package com.swstock.database;

import com.swstock.model.HistoricoEstoque;
import com.swstock.model.Produto;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object (DAO) para a entidade Produto.
 * Contém operações de CRUD, buscas textuais, filtros por localização, grupo,
 * cálculo de ocupação para o Mapa 2D e operações atômicas com UPSERT e Histórico.
 */
public class ProdutoDAO {

    private static final Logger LOGGER = Logger.getLogger(ProdutoDAO.class.getName());
    private final DatabaseManager databaseManager;

    public ProdutoDAO() {
        this(DatabaseManager.getInstance());
    }

    public ProdutoDAO(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    private Connection getConnection() {
        return databaseManager.getConnection();
    }

    /**
     * Insere um novo produto na base.
     */
    public Produto insert(Produto produto) throws SQLException {
        String sql = """
            INSERT INTO produtos (nome, grupo, precoVista, precoPrazo, codigoLoja, localizacao, quantidade, urlImagem, descricaoBreve)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
            """;

        try (PreparedStatement stmt = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setStatementParameters(stmt, produto);
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    produto.setId(rs.getInt(1));
                }
            }
            return produto;
        }
    }

    /**
     * Atualiza um produto existente por ID.
     */
    public boolean update(Produto produto, String responsavel, String motivo) throws SQLException {
        if (produto.getId() == null) {
            throw new IllegalArgumentException("Produto deve possuir ID para ser atualizado.");
        }

        Produto anterior = findById(produto.getId());
        int qtdAnterior = anterior != null && anterior.getQuantidade() != null ? anterior.getQuantidade() : 0;
        int qtdNova = produto.getQuantidade() != null ? produto.getQuantidade() : 0;
        int diferenca = qtdNova - qtdAnterior;

        String sql = """
            UPDATE produtos
            SET nome = ?, grupo = ?, precoVista = ?, precoPrazo = ?, codigoLoja = ?,
                localizacao = ?, quantidade = ?, urlImagem = ?, descricaoBreve = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?;
            """;

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            setStatementParameters(stmt, produto);
            stmt.setInt(10, produto.getId());
            boolean ok = stmt.executeUpdate() > 0;

            if (ok && diferenca != 0 && !"SEM_HISTORICO".equals(motivo)) {
                String tipo = diferenca > 0 ? "ENTRADA" : "SAIDA";
                String motivoReal = (motivo != null && !motivo.trim().isEmpty() && !motivo.equals("Edição Geral de Cadastro")) ? motivo : "Modificação de estoque";
                String resp = (responsavel != null && !responsavel.trim().isEmpty()) ? responsavel : "Não informado";
                HistoricoEstoque historico = new HistoricoEstoque(
                        produto.getId(),
                        tipo,
                        diferenca,
                        qtdAnterior,
                        qtdNova,
                        motivoReal,
                        resp
                );
                new HistoricoEstoqueDAO(databaseManager).insert(historico);
            }
            return ok;
        }
    }

    public boolean update(Produto produto) throws SQLException {
        return update(produto, "Não informado", "Modificação de estoque");
    }

    public boolean updateSemHistorico(Produto produto) throws SQLException {
        return update(produto, "Sistema", "SEM_HISTORICO");
    }

    /**
     * Executa UPSERT baseado no `codigoLoja`.
     */
    public Produto upsert(Produto produto) throws SQLException {
        String sql = """
            INSERT INTO produtos (nome, grupo, precoVista, precoPrazo, codigoLoja, localizacao, quantidade, urlImagem, descricaoBreve)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(codigoLoja) DO UPDATE SET
                nome = excluded.nome,
                grupo = excluded.grupo,
                precoVista = excluded.precoVista,
                precoPrazo = excluded.precoPrazo,
                localizacao = CASE 
                    WHEN produtos.localizacao IS NOT NULL AND TRIM(produtos.localizacao) != '' THEN produtos.localizacao 
                    ELSE excluded.localizacao 
                END,
                quantidade = produtos.quantidade,
                urlImagem = CASE 
                    WHEN excluded.urlImagem IS NOT NULL AND TRIM(excluded.urlImagem) != '' THEN excluded.urlImagem 
                    ELSE produtos.urlImagem 
                END,
                descricaoBreve = CASE 
                    WHEN excluded.descricaoBreve IS NOT NULL AND TRIM(excluded.descricaoBreve) != '' THEN excluded.descricaoBreve 
                    ELSE produtos.descricaoBreve 
                END,
                updated_at = CURRENT_TIMESTAMP;
            """;

        try (PreparedStatement stmt = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setStatementParameters(stmt, produto);
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    produto.setId(rs.getInt(1));
                } else {
                    Produto existente = findByCodigoLoja(produto.getCodigoLoja());
                    if (existente != null) {
                        produto.setId(existente.getId());
                    }
                }
            }
            return produto;
        }
    }

    /**
     * Importação em lote (Batch) com UPSERT dentro de uma única transação.
     */
    public int batchUpsert(List<Produto> produtos) throws SQLException {
        String sql = """
            INSERT INTO produtos (nome, grupo, precoVista, precoPrazo, codigoLoja, localizacao, quantidade, urlImagem, descricaoBreve)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(codigoLoja) DO UPDATE SET
                nome = excluded.nome,
                grupo = excluded.grupo,
                precoVista = excluded.precoVista,
                precoPrazo = excluded.precoPrazo,
                localizacao = CASE 
                    WHEN produtos.localizacao IS NOT NULL AND TRIM(produtos.localizacao) != '' THEN produtos.localizacao 
                    ELSE excluded.localizacao 
                END,
                quantidade = produtos.quantidade,
                urlImagem = CASE 
                    WHEN excluded.urlImagem IS NOT NULL AND TRIM(excluded.urlImagem) != '' THEN excluded.urlImagem 
                    ELSE produtos.urlImagem 
                END,
                descricaoBreve = CASE 
                    WHEN excluded.descricaoBreve IS NOT NULL AND TRIM(excluded.descricaoBreve) != '' THEN excluded.descricaoBreve 
                    ELSE produtos.descricaoBreve 
                END,
                updated_at = CURRENT_TIMESTAMP;
            """;

        Connection conn = getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        int affected = 0;

        try {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Produto p : produtos) {
                    setStatementParameters(stmt, p);
                    stmt.addBatch();
                }
                int[] results = stmt.executeBatch();
                affected = results.length;
                conn.commit();
            }
        } catch (SQLException e) {
            conn.rollback();
            LOGGER.log(Level.SEVERE, "Erro durante batch UPSERT, rollback realizado.", e);
            throw e;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
        return affected;
    }

    /**
     * Atualização de quantidade no banco com registro no histórico e identificação do responsável.
     */
    public boolean updateQuantidade(int id, int novaQuantidade, String motivo, String responsavel) throws SQLException {
        if (novaQuantidade < 0) {
            novaQuantidade = 0;
        }

        Produto anterior = findById(id);
        int qtdAnterior = anterior != null && anterior.getQuantidade() != null ? anterior.getQuantidade() : 0;
        int diferenca = novaQuantidade - qtdAnterior;

        String sql = "UPDATE produtos SET quantidade = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?;";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, novaQuantidade);
            stmt.setInt(2, id);
            boolean ok = stmt.executeUpdate() > 0;

            if (ok && diferenca != 0 && !"SEM_HISTORICO".equals(motivo)) {
                String tipo = diferenca > 0 ? "ENTRADA" : "SAIDA";
                if (motivo == null || motivo.trim().isEmpty()) {
                    motivo = "Modificação de estoque";
                }
                String resp = (responsavel != null && !responsavel.trim().isEmpty()) ? responsavel : "Não informado";
                HistoricoEstoque historico = new HistoricoEstoque(
                        id,
                        tipo,
                        diferenca,
                        qtdAnterior,
                        novaQuantidade,
                        motivo,
                        resp
                );
                new HistoricoEstoqueDAO(databaseManager).insert(historico);
            }
            return ok;
        }
    }

    public boolean updateQuantidade(int id, int novaQuantidade, String motivo) throws SQLException {
        return updateQuantidade(id, novaQuantidade, motivo, "Não informado");
    }

    public boolean updateQuantidade(int id, int novaQuantidade) throws SQLException {
        return updateQuantidade(id, novaQuantidade, null, "Não informado");
    }

    public boolean updateQuantidadeSemHistorico(int id, int novaQuantidade) throws SQLException {
        return updateQuantidade(id, novaQuantidade, "SEM_HISTORICO", "Sistema");
    }

    /**
     * Exclusão por ID.
     */
    public boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM produtos WHERE id = ?;";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Busca por ID.
     */
    public Produto findById(int id) throws SQLException {
        String sql = "SELECT * FROM produtos WHERE id = ?;";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Busca por Código da Loja.
     */
    public Produto findByCodigoLoja(String codigoLoja) throws SQLException {
        String sql = "SELECT * FROM produtos WHERE codigoLoja = ?;";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, codigoLoja);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Retorna todos os produtos ordenados por nome.
     */
    public List<Produto> findAll() throws SQLException {
        String sql = "SELECT * FROM produtos ORDER BY nome ASC;";
        List<Produto> lista = new ArrayList<>();
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                lista.add(mapRow(rs));
            }
        }
        return lista;
    }

    /**
     * Filtro flexível por termo de busca e/ou localização.
     */
    public List<Produto> findByFilter(String termo, String localizacao) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM produtos WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (termo != null && !termo.trim().isEmpty()) {
            sql.append("AND (LOWER(nome) LIKE ? OR LOWER(codigoLoja) LIKE ? OR LOWER(grupo) LIKE ? OR LOWER(descricaoBreve) LIKE ?) ");
            String wild = "%" + termo.trim().toLowerCase() + "%";
            params.add(wild);
            params.add(wild);
            params.add(wild);
            params.add(wild);
        }

        if (localizacao != null && !localizacao.trim().isEmpty() && !localizacao.equalsIgnoreCase("TODAS")) {
            sql.append("AND UPPER(TRIM(localizacao)) = ? ");
            params.add(localizacao.trim().toUpperCase());
        }

        sql.append("ORDER BY nome ASC;");

        List<Produto> lista = new ArrayList<>();
        try (PreparedStatement stmt = getConnection().prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapRow(rs));
                }
            }
        }
        return lista;
    }

    /**
     * Retorna contagem de produtos e soma de quantidades por localização para alimentar o Mapa 2D.
     */
    public Map<String, Integer> getLocationCounts() throws SQLException {
        String sql = """
            SELECT UPPER(TRIM(localizacao)) as loc, COUNT(*) as total_itens, SUM(quantidade) as total_qtd
            FROM produtos
            WHERE localizacao IS NOT NULL AND TRIM(localizacao) != ''
            GROUP BY UPPER(TRIM(localizacao));
            """;

        Map<String, Integer> mapa = new HashMap<>();
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String loc = rs.getString("loc");
                int totalQtd = rs.getInt("total_qtd");
                mapa.put(loc, totalQtd);
            }
        }
        return mapa;
    }

    /**
     * Retorna todos os grupos/fabricantes cadastrados no banco de dados.
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

    /**
     * Retorna todas as localizações cadastradas.
     */
    public List<String> getAllLocations() throws SQLException {
        String sql = """
            SELECT DISTINCT UPPER(TRIM(localizacao)) as loc
            FROM produtos
            WHERE localizacao IS NOT NULL AND TRIM(localizacao) != ''
            ORDER BY loc ASC;
            """;
        List<String> locs = new ArrayList<>();
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                locs.add(rs.getString("loc"));
            }
        }
        return locs;
    }

    /**
     * Retorna produtos filtrados por grupo específico (ou todos se null/TODOS) com busca opcional.
     */
    public List<Produto> findByGrupo(String grupo, String termoBusca) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM produtos WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (grupo != null && !grupo.trim().isEmpty() && !grupo.toUpperCase().contains("TODOS") && !grupo.toUpperCase().contains("TODAS")) {
            sql.append("AND UPPER(TRIM(grupo)) = ? ");
            params.add(grupo.trim().toUpperCase());
        }

        if (termoBusca != null && !termoBusca.trim().isEmpty()) {
            sql.append("AND (LOWER(nome) LIKE ? OR LOWER(codigoLoja) LIKE ? OR LOWER(localizacao) LIKE ? OR LOWER(descricaoBreve) LIKE ?) ");
            String wild = "%" + termoBusca.trim().toLowerCase() + "%";
            params.add(wild);
            params.add(wild);
            params.add(wild);
            params.add(wild);
        }

        sql.append("ORDER BY UPPER(TRIM(grupo)) ASC, nome ASC;");

        List<Produto> lista = new ArrayList<>();
        try (PreparedStatement stmt = getConnection().prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapRow(rs));
                }
            }
        }
        return lista;
    }

    private void setStatementParameters(PreparedStatement stmt, Produto p) throws SQLException {
        stmt.setString(1, p.getNome());
        stmt.setString(2, p.getGrupo() != null ? p.getGrupo() : "GERAL");
        stmt.setDouble(3, p.getPrecoVista() != null ? p.getPrecoVista() : 0.0);
        stmt.setDouble(4, p.getPrecoPrazo() != null ? p.getPrecoPrazo() : 0.0);
        stmt.setString(5, p.getCodigoLoja());
        stmt.setString(6, p.getLocalizacao());
        stmt.setInt(7, p.getQuantidade() != null ? p.getQuantidade() : 0);
        stmt.setString(8, p.getUrlImagem());
        stmt.setString(9, p.getDescricaoBreve());
    }

    private Produto mapRow(ResultSet rs) throws SQLException {
        String grupo = "GERAL";
        try {
            grupo = rs.getString("grupo");
            if (grupo == null || grupo.trim().isEmpty()) {
                grupo = "GERAL";
            }
        } catch (SQLException ignored) {}

        return new Produto(
            rs.getInt("id"),
            rs.getString("nome"),
            grupo,
            rs.getDouble("precoVista"),
            rs.getDouble("precoPrazo"),
            rs.getString("codigoLoja"),
            rs.getString("localizacao"),
            rs.getInt("quantidade"),
            rs.getString("urlImagem"),
            rs.getString("descricaoBreve")
        );
    }
}
