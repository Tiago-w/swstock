package com.swstock.database;

import com.swstock.model.Funcionario;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object para a tabela 'funcionarios'.
 */
public class FuncionarioDAO {

    private static final Logger LOGGER = Logger.getLogger(FuncionarioDAO.class.getName());
    private final DatabaseManager databaseManager;

    public FuncionarioDAO() {
        this(DatabaseManager.getInstance());
    }

    public FuncionarioDAO(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    private Connection getConnection() {
        return databaseManager.getConnection();
    }

    /**
     * Retorna todos os funcionários cadastrados ordenados alfabeticamente.
     */
    public List<Funcionario> findAll() throws SQLException {
        String sql = "SELECT id, nome, created_at FROM funcionarios ORDER BY nome ASC;";
        List<Funcionario> lista = new ArrayList<>();
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                lista.add(new Funcionario(
                        rs.getInt("id"),
                        rs.getString("nome"),
                        rs.getString("created_at")
                ));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao buscar todos os funcionários.", e);
            throw e;
        }
        return lista;
    }

    /**
     * Retorna uma lista de strings com todos os nomes de funcionários.
     */
    public List<String> getNomesFuncionarios() {
        List<String> nomes = new ArrayList<>();
        try {
            for (Funcionario f : findAll()) {
                if (f.getNome() != null && !f.getNome().trim().isEmpty()) {
                    nomes.add(f.getNome().trim());
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erro ao listar nomes de funcionários.", e);
        }
        return nomes;
    }

    /**
     * Insere um novo funcionário no banco.
     */
    public Funcionario insert(Funcionario funcionario) throws SQLException {
        if (funcionario.getNome() == null || funcionario.getNome().trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do funcionário não pode ser vazio.");
        }

        String sql = "INSERT INTO funcionarios (nome) VALUES (?);";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, funcionario.getNome().trim());
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    funcionario.setId(rs.getInt(1));
                }
            }
            return funcionario;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao inserir funcionário: " + funcionario.getNome(), e);
            throw e;
        }
    }

    /**
     * Remove um funcionário por ID.
     */
    public boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM funcionarios WHERE id = ?;";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao excluir funcionário ID=" + id, e);
            throw e;
        }
    }
}
