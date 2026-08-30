package com.swstock.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gerenciador da conexão SQLite (JDBC) e inicialização da base de dados offline do SWStock.
 * Implementa padrão Singleton com suporte a transações e encerramento seguro (safe close).
 */
public class DatabaseManager {

    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private static final String DEFAULT_DB_FILE = "swstock.db";

    private static DatabaseManager instance;
    private Connection connection;
    private final String dbUrl;

    private DatabaseManager(String dbPath) {
        this.dbUrl = "jdbc:sqlite:" + dbPath;
        initConnection();
        initSchema();
        registerShutdownHook();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager(DEFAULT_DB_FILE);
        }
        return instance;
    }

    public static synchronized DatabaseManager getInstance(String customDbPath) {
        if (instance == null || !instance.dbUrl.equals("jdbc:sqlite:" + customDbPath)) {
            if (instance != null) {
                instance.close();
            }
            instance = new DatabaseManager(customDbPath);
        }
        return instance;
    }

    private void initConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(dbUrl);
                // Ativa verificação de Foreign Keys e modo WAL para alta performance
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON;");
                    stmt.execute("PRAGMA journal_mode = WAL;");
                    stmt.execute("PRAGMA synchronous = NORMAL;");
                }
                LOGGER.info("Conexão com SQLite estabelecida: " + dbUrl);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao conectar ao banco SQLite: " + dbUrl, e);
            throw new RuntimeException("Falha ao inicializar o banco de dados offline.", e);
        }
    }

    private void initSchema() {
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS produtos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nome TEXT NOT NULL,
                grupo TEXT DEFAULT 'GERAL',
                precoVista REAL NOT NULL DEFAULT 0.0,
                precoPrazo REAL NOT NULL DEFAULT 0.0,
                codigoLoja TEXT NOT NULL UNIQUE,
                localizacao TEXT,
                quantidade INTEGER NOT NULL DEFAULT 0,
                urlImagem TEXT,
                descricaoBreve TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """;

        String createIndexCodigoLoja = "CREATE INDEX IF NOT EXISTS idx_produtos_codigo_loja ON produtos(codigoLoja);";
        String createIndexGrupo = "CREATE INDEX IF NOT EXISTS idx_produtos_grupo ON produtos(grupo);";
        String createIndexLocalizacao = "CREATE INDEX IF NOT EXISTS idx_produtos_localizacao ON produtos(localizacao);";
        String createIndexNome = "CREATE INDEX IF NOT EXISTS idx_produtos_nome ON produtos(nome);";

        String createTableHistorico = """
            CREATE TABLE IF NOT EXISTS historico_estoque (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                produto_id INTEGER NOT NULL,
                data_hora TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                tipo TEXT NOT NULL,
                quantidade_alterada INTEGER NOT NULL,
                quantidade_anterior INTEGER NOT NULL,
                quantidade_nova INTEGER NOT NULL,
                motivo TEXT,
                responsavel TEXT DEFAULT 'Não informado',
                FOREIGN KEY(produto_id) REFERENCES produtos(id) ON DELETE CASCADE
            );
            """;

        String createIndexHistoricoProd = "CREATE INDEX IF NOT EXISTS idx_historico_produto ON historico_estoque(produto_id);";
        String createIndexHistoricoData = "CREATE INDEX IF NOT EXISTS idx_historico_data ON historico_estoque(data_hora);";

        String createTableFuncionarios = """
            CREATE TABLE IF NOT EXISTS funcionarios (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nome TEXT NOT NULL UNIQUE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """;

        String seedFuncionarios = """
            INSERT OR IGNORE INTO funcionarios (nome) VALUES 
            ('Tiago'),
            ('Denise'),
            ('Lucas'),
            ('Maurício'),
            ('Éder'),
            ('Gustavo');
            """;

        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(createTableSql);

            // Migração automática caso a tabela já existisse sem a coluna grupo
            try {
                stmt.execute("ALTER TABLE produtos ADD COLUMN grupo TEXT DEFAULT 'GERAL';");
            } catch (SQLException ignored) {
                // Coluna já existe
            }

            stmt.execute(createIndexCodigoLoja);
            stmt.execute(createIndexGrupo);
            stmt.execute(createIndexLocalizacao);
            stmt.execute(createIndexNome);
            stmt.execute(createTableHistorico);
            stmt.execute(createIndexHistoricoProd);
            stmt.execute(createIndexHistoricoData);

            // Migração da coluna responsavel se a tabela já existia sem ela
            try {
                stmt.execute("ALTER TABLE historico_estoque ADD COLUMN responsavel TEXT DEFAULT 'Não informado';");
            } catch (SQLException ignored) {
                // Coluna já existe
            }

            stmt.execute(createTableFuncionarios);
            stmt.execute(seedFuncionarios);

            LOGGER.info("Schema das tabelas 'produtos', 'historico_estoque', 'funcionarios' e índices verificados com sucesso.");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao inicializar o schema SQLite.", e);
            throw new RuntimeException("Falha ao criar tabelas no SQLite.", e);
        }
    }

    /**
     * Retorna a conexão ativa, restabelecendo-a caso tenha sido fechada.
     */
    public synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                initConnection();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao verificar estado da conexão SQLite.", e);
        }
        return connection;
    }

    /**
     * Fecha com segurança a conexão, garantindo commit de transações pendentes.
     */
    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                if (!connection.getAutoCommit()) {
                    connection.commit();
                    LOGGER.info("Transações pendentes commitadas.");
                }
                connection.close();
                LOGGER.info("Conexão com SQLite encerrada com segurança.");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erro ao fechar conexão com SQLite.", e);
        }
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("JVM Shutdown detectado. Fechando conexão do banco de dados...");
            close();
        }));
    }
}
