package com.swstock.database;

import com.swstock.model.Base;
import com.swstock.model.HistoricoBase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object para gerenciamento da tabela 'bases' no SQLite.
 */
public class BaseDAO {

    private static final Logger LOGGER = Logger.getLogger(BaseDAO.class.getName());
    private final DatabaseManager databaseManager;

    public BaseDAO() {
        this(DatabaseManager.getInstance());
    }

    public BaseDAO(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    private Connection getConnection() {
        return databaseManager.getConnection();
    }

    public Base insert(Base base, String responsavel) throws SQLException {
        if (base.getTamanho() == null || base.getTamanho().trim().isEmpty()) {
            throw new IllegalArgumentException("Tamanho da base é obrigatório.");
        }
        if (base.getNome() == null || base.getNome().trim().isEmpty()) {
            throw new IllegalArgumentException("Nome da base é obrigatório.");
        }

        String sql = """
            INSERT INTO bases (tamanho, nome, tipo, fabricante, quantidade, valor_compra)
            VALUES (?, ?, ?, ?, ?, ?);
        """;

        try (PreparedStatement stmt = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, base.getTamanho().trim());
            stmt.setString(2, base.getNome().trim());
            stmt.setString(3, base.getTipo() != null ? base.getTipo().trim().toUpperCase() : "AUTOMOTIVA");
            stmt.setString(4, base.getFabricante() != null ? base.getFabricante().trim() : "Geral");
            stmt.setInt(5, base.getQuantidade() != null ? Math.max(0, base.getQuantidade()) : 0);
            stmt.setDouble(6, base.getValorCompra() != null ? Math.max(0.0, base.getValorCompra()) : 0.0);

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    base.setId(rs.getInt(1));
                }
            }

            int qtdInicial = base.getQuantidade() != null ? base.getQuantidade() : 0;
            if (base.getId() != null && qtdInicial > 0) {
                HistoricoBase h = new HistoricoBase(
                        base.getId(),
                        "ENTRADA",
                        qtdInicial,
                        0,
                        qtdInicial,
                        "Cadastro Inicial da Base",
                        responsavel != null ? responsavel : "Não informado"
                );
                new HistoricoBaseDAO(databaseManager).insert(h);
            }

            return base;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao inserir base: " + base.getNome(), e);
            throw e;
        }
    }

    public boolean update(Base base, String responsavel, String motivo) throws SQLException {
        if (base.getId() == null) {
            throw new IllegalArgumentException("Base deve possuir ID para ser atualizada.");
        }

        Base anterior = findById(base.getId());
        int qtdAnterior = anterior != null && anterior.getQuantidade() != null ? anterior.getQuantidade() : 0;
        int qtdNova = base.getQuantidade() != null ? base.getQuantidade() : 0;
        int diferenca = qtdNova - qtdAnterior;

        String sql = """
            UPDATE bases
            SET tamanho = ?, nome = ?, tipo = ?, fabricante = ?, quantidade = ?, valor_compra = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?;
        """;

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, base.getTamanho().trim());
            stmt.setString(2, base.getNome().trim());
            stmt.setString(3, base.getTipo() != null ? base.getTipo().trim().toUpperCase() : "AUTOMOTIVA");
            stmt.setString(4, base.getFabricante() != null ? base.getFabricante().trim() : "Geral");
            stmt.setInt(5, qtdNova);
            stmt.setDouble(6, base.getValorCompra() != null ? Math.max(0.0, base.getValorCompra()) : 0.0);
            stmt.setInt(7, base.getId());

            boolean ok = stmt.executeUpdate() > 0;

            if (ok && diferenca != 0) {
                String tipo = diferenca > 0 ? "ENTRADA" : "SAIDA";
                String mot = (motivo != null && !motivo.trim().isEmpty()) ? motivo.trim() : "Modificação de estoque da base";
                String resp = (responsavel != null && !responsavel.trim().isEmpty()) ? responsavel : "Não informado";

                HistoricoBase h = new HistoricoBase(
                        base.getId(),
                        tipo,
                        diferenca,
                        qtdAnterior,
                        qtdNova,
                        mot,
                        resp
                );
                new HistoricoBaseDAO(databaseManager).insert(h);
            }

            return ok;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao atualizar base ID=" + base.getId(), e);
            throw e;
        }
    }

    public boolean delete(int id, String responsavel) throws SQLException {
        Base b = findById(id);
        if (b != null && b.getQuantidade() > 0) {
            HistoricoBase h = new HistoricoBase(
                    b.getId(),
                    "SAIDA",
                    -b.getQuantidade(),
                    b.getQuantidade(),
                    0,
                    "Exclusão da base do catálogo",
                    responsavel != null ? responsavel : "Não informado"
            );
            new HistoricoBaseDAO(databaseManager).insert(h);
        }

        String sql = "DELETE FROM bases WHERE id = ?;";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao excluir base ID=" + id, e);
            throw e;
        }
    }

    public Base findById(int id) throws SQLException {
        String sql = "SELECT id, tamanho, nome, tipo, fabricante, quantidade, valor_compra, created_at, updated_at FROM bases WHERE id = ?;";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public Base findByCodigo(String codigo) throws SQLException {
        return findByTamanho(codigo);
    }

    public Base findByTamanho(String tamanho) throws SQLException {
        if (tamanho == null || tamanho.trim().isEmpty()) return null;
        String sql = "SELECT id, tamanho, nome, tipo, fabricante, quantidade, valor_compra, created_at, updated_at FROM bases WHERE UPPER(tamanho) = ?;";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, tamanho.trim().toUpperCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public Base findByTamanhoNomeTipo(String tamanho, String nome, String tipo, String fabricante) throws SQLException {
        String sql = "SELECT id, tamanho, nome, tipo, fabricante, quantidade, valor_compra, created_at, updated_at FROM bases WHERE UPPER(tamanho) = ? AND UPPER(nome) = ? AND UPPER(tipo) = ? AND UPPER(fabricante) = ?;";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, tamanho != null ? tamanho.trim().toUpperCase() : "");
            stmt.setString(2, nome != null ? nome.trim().toUpperCase() : "");
            stmt.setString(3, tipo != null ? tipo.trim().toUpperCase() : "AUTOMOTIVA");
            stmt.setString(4, fabricante != null ? fabricante.trim().toUpperCase() : "GERAL");
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public List<Base> findAll() throws SQLException {
        String sql = "SELECT id, tamanho, nome, tipo, fabricante, quantidade, valor_compra, created_at, updated_at FROM bases ORDER BY tipo ASC, nome ASC;";
        List<Base> lista = new ArrayList<>();
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                lista.add(mapResultSet(rs));
            }
        }
        return lista;
    }

    public List<Base> findByTipo(String tipo) throws SQLException {
        if (tipo == null || tipo.trim().isEmpty()) return findAll();
        String sql = "SELECT id, tamanho, nome, tipo, fabricante, quantidade, valor_compra, created_at, updated_at FROM bases WHERE UPPER(tipo) = ? ORDER BY nome ASC;";
        List<Base> lista = new ArrayList<>();
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, tipo.trim().toUpperCase());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapResultSet(rs));
                }
            }
        }
        return lista;
    }

    public List<Base> search(String tipo, String termo, String fabricante) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT id, tamanho, nome, tipo, fabricante, quantidade, valor_compra, created_at, updated_at FROM bases WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (tipo != null && !tipo.trim().isEmpty() && !tipo.equalsIgnoreCase("TODAS")) {
            sql.append(" AND UPPER(tipo) = ? ");
            params.add(tipo.trim().toUpperCase());
        }

        if (fabricante != null && !fabricante.trim().isEmpty() && !fabricante.equalsIgnoreCase("TODOS")) {
            sql.append(" AND UPPER(fabricante) = ? ");
            params.add(fabricante.trim().toUpperCase());
        }

        if (termo != null && !termo.trim().isEmpty()) {
            sql.append(" AND (UNACCENT(tamanho) LIKE ? OR UNACCENT(nome) LIKE ? OR UNACCENT(fabricante) LIKE ?) ");
            String wild = "%" + DatabaseManager.unaccent(termo.trim()) + "%";
            params.add(wild);
            params.add(wild);
            params.add(wild);
        }

        sql.append(" ORDER BY nome ASC;");

        List<Base> lista = new ArrayList<>();
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

    public List<String> getAllFabricantes(String tipo) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT fabricante FROM bases WHERE fabricante IS NOT NULL AND TRIM(fabricante) != '' ");
        if (tipo != null && !tipo.trim().isEmpty() && !tipo.equalsIgnoreCase("TODAS")) {
            sql.append(" AND UPPER(tipo) = '").append(tipo.trim().toUpperCase()).append("' ");
        }
        sql.append(" ORDER BY fabricante ASC;");

        List<String> fabs = new ArrayList<>();
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql.toString())) {
            while (rs.next()) {
                fabs.add(rs.getString("fabricante"));
            }
        }
        return fabs;
    }

    public List<String> getAllTamanhos(String tipo) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT tamanho FROM bases WHERE tamanho IS NOT NULL AND TRIM(tamanho) != '' ");
        if (tipo != null && !tipo.trim().isEmpty() && !tipo.equalsIgnoreCase("TODAS") && !tipo.equalsIgnoreCase("TODOS")) {
            sql.append(" AND UPPER(tipo) = '").append(tipo.trim().toUpperCase()).append("' ");
        }
        sql.append(" ORDER BY tamanho ASC;");

        List<String> tamanhos = new ArrayList<>();
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql.toString())) {
            while (rs.next()) {
                tamanhos.add(rs.getString("tamanho"));
            }
        }
        return tamanhos;
    }

    public List<Base> searchLevantamento(String tipo, String fabricante, String tamanho, String filtroEstoque, String termo) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT id, tamanho, nome, tipo, fabricante, quantidade, valor_compra, created_at, updated_at FROM bases WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (tipo != null && !tipo.trim().isEmpty() && !tipo.equalsIgnoreCase("TODAS") && !tipo.equalsIgnoreCase("TODOS") && !tipo.equalsIgnoreCase("TODOS OS TIPOS")) {
            sql.append(" AND UPPER(tipo) = ? ");
            params.add(tipo.trim().toUpperCase());
        }

        if (fabricante != null && !fabricante.trim().isEmpty() && !fabricante.equalsIgnoreCase("TODOS") && !fabricante.equalsIgnoreCase("TODOS OS FABRICANTES")) {
            sql.append(" AND UPPER(fabricante) = ? ");
            params.add(fabricante.trim().toUpperCase());
        }

        if (tamanho != null && !tamanho.trim().isEmpty() && !tamanho.equalsIgnoreCase("TODOS") && !tamanho.equalsIgnoreCase("TODOS OS TAMANHOS")) {
            sql.append(" AND UPPER(tamanho) = ? ");
            params.add(tamanho.trim().toUpperCase());
        }

        if (filtroEstoque != null) {
            if (filtroEstoque.contains("> 0") || filtroEstoque.toLowerCase().contains("com estoque")) {
                sql.append(" AND quantidade > 0 ");
            } else if (filtroEstoque.contains("= 0") || filtroEstoque.toLowerCase().contains("zerad")) {
                sql.append(" AND quantidade = 0 ");
            }
        }

        if (termo != null && !termo.trim().isEmpty()) {
            sql.append(" AND (UNACCENT(tamanho) LIKE ? OR UNACCENT(nome) LIKE ? OR UNACCENT(fabricante) LIKE ?) ");
            String wild = "%" + DatabaseManager.unaccent(termo.trim()) + "%";
            params.add(wild);
            params.add(wild);
            params.add(wild);
        }

        sql.append(" ORDER BY tipo ASC, fabricante ASC, nome ASC;");

        List<Base> lista = new ArrayList<>();
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

    public boolean ajustarEstoque(int id, int diferenca, String tipo, String motivo, String responsavel) throws SQLException {
        Base base = findById(id);
        if (base == null) return false;
        int qtdAnterior = base.getQuantidade() != null ? base.getQuantidade() : 0;
        int qtdNova = Math.max(0, qtdAnterior + diferenca);
        base.setQuantidade(qtdNova);
        return update(base, responsavel, motivo);
    }

    private Base mapResultSet(ResultSet rs) throws SQLException {
        String tam = null;
        try {
            tam = rs.getString("tamanho");
        } catch (SQLException ignored) {
            try {
                tam = rs.getString("codigo");
            } catch (SQLException ignored2) {}
        }
        return new Base(
                rs.getInt("id"),
                tam != null ? tam : "",
                rs.getString("nome"),
                rs.getString("tipo"),
                rs.getString("fabricante"),
                rs.getInt("quantidade"),
                rs.getDouble("valor_compra"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        );
    }
}
