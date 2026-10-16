package com.swstock.database;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Normalizer;
import java.util.Locale;
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
            instance = new DatabaseManager(resolveDefaultDatabasePath());
        }
        return instance;
    }

    /**
     * Resolve de forma inteligente e segura o caminho do banco de dados SQLite.
     * Prioridades:
     * 1. Propriedade de sistema 'swstock.db.path' (-Dswstock.db.path=...)
     * 2. Modo portátil explícito (se existir .portable ou portable.txt na pasta da aplicação e a pasta for gravável)
     * 3. Diretório de dados do usuário:
     *    - No Windows: %APPDATA%/SWStock/swstock.db (garante 100% de permissão de escrita sem necessidade de Administrador)
     *    - No Linux/Mac: ~/.swstock/swstock.db (ou diretório local em ambiente de desenvolvimento)
     * 4. Migração transparente de swstock.db legado caso exista na pasta da aplicação.
     */
    public static String resolveDefaultDatabasePath() {
        // 1. Propriedade de sistema tem prioridade absoluta (-Dswstock.db.path=...)
        String customProp = System.getProperty("swstock.db.path");
        if (customProp != null && !customProp.isBlank()) {
            return customProp.trim();
        }

        Path appDir = getApplicationDirectory();
        Path localDbInAppDir = appDir.resolve(DEFAULT_DB_FILE);
        Path localDbInCurrentDir = Path.of(DEFAULT_DB_FILE).toAbsolutePath().normalize();

        // 2. Prioridade direta para o arquivo swstock.db na pasta da aplicação ou diretório atual:
        //    Permite o fluxo tradicional do usuário ("baixar o executável e colocar o .db antigo junto")
        if (Files.exists(localDbInAppDir) && isDirectoryWritable(appDir)) {
            garantirPermissaoEscrita(localDbInAppDir);
            LOGGER.info("Base de dados local encontrada na pasta da aplicação: " + localDbInAppDir.toAbsolutePath());
            return localDbInAppDir.toAbsolutePath().toString();
        }

        if (Files.exists(localDbInCurrentDir) && isDirectoryWritable(localDbInCurrentDir.getParent())) {
            garantirPermissaoEscrita(localDbInCurrentDir);
            LOGGER.info("Base de dados local encontrada no diretório atual: " + localDbInCurrentDir);
            return localDbInCurrentDir.toString();
        }

        // 3. Se a pasta da aplicação for gravável (Desktop, Downloads, C:\swstock, pendrive, etc.):
        //    Cria/mantém o banco diretamente na mesma pasta do executável (modo portátil e transparente)
        if (isDirectoryWritable(appDir)) {
            LOGGER.info("Pasta da aplicação é gravável. Utilizando banco local: " + localDbInAppDir.toAbsolutePath());
            return localDbInAppDir.toAbsolutePath().toString();
        }

        // 4. Caso a pasta da aplicação NÃO seja gravável (ex: C:\Program Files restrito sem admin):
        //    Redireciona com segurança para a pasta do usuário (%APPDATA% no Windows ou ~/.swstock no Linux/Mac)
        boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path userDbDir;
        if (isWindows) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                userDbDir = Path.of(appData, "SWStock");
            } else {
                userDbDir = Path.of(System.getProperty("user.home"), "AppData", "Roaming", "SWStock");
            }
        } else {
            userDbDir = Path.of(System.getProperty("user.home"), ".swstock");
        }

        try {
            if (!Files.exists(userDbDir)) {
                Files.createDirectories(userDbDir);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Não foi possível criar o diretório de dados do usuário: " + userDbDir + ". Tentando diretório local.", e);
            return localDbInAppDir.toAbsolutePath().toString();
        }

        Path targetDb = userDbDir.resolve(DEFAULT_DB_FILE);

        // Se havia um swstock.db legado na pasta protegida mas não conseguimos escrever lá, copia para %APPDATA%
        if (!Files.exists(targetDb) && Files.exists(localDbInAppDir)) {
            try {
                LOGGER.info("Copiando base de dados da pasta restrita para a pasta do usuário: " + targetDb);
                Files.copy(localDbInAppDir, targetDb, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Falha ao copiar base legada para " + targetDb, e);
            }
        }

        return targetDb.toAbsolutePath().toString();
    }

    private static void garantirPermissaoEscrita(Path dbFile) {
        try {
            File f = dbFile.toFile();
            if (!f.canWrite()) {
                f.setWritable(true, false);
            }
            if (!f.canRead()) {
                f.setReadable(true, false);
            }
        } catch (Exception ignored) {
        }
    }

    private static Path getApplicationDirectory() {
        try {
            var codeSource = DatabaseManager.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                Path path = Path.of(codeSource.getLocation().toURI());
                if (Files.isRegularFile(path)) {
                    return path.getParent();
                }
                return path;
            }
        } catch (Exception e) {
            // Fallback para diretório de execução atual
        }
        return Path.of(".").toAbsolutePath().normalize();
    }

    private static boolean isPortableMode(Path appDir) {
        if (appDir == null) return false;
        return Files.exists(appDir.resolve(".portable")) || Files.exists(appDir.resolve("portable.txt"));
    }

    private static boolean isDirectoryWritable(Path dir) {
        if (dir == null || !Files.exists(dir)) return false;
        try {
            Path testFile = dir.resolve(".swstock_perm_test_" + System.currentTimeMillis());
            Files.createFile(testFile);
            Files.deleteIfExists(testFile);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getDbUrl() {
        return dbUrl;
    }

    public String getDbPath() {
        if (dbUrl != null && dbUrl.startsWith("jdbc:sqlite:")) {
            return dbUrl.substring("jdbc:sqlite:".length());
        }
        return dbUrl;
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
                registerCustomFunctions(connection);
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

    private void registerCustomFunctions(Connection conn) {
        try {
            org.sqlite.Function.create(conn, "UNACCENT", new org.sqlite.Function() {
                @Override
                protected void xFunc() throws SQLException {
                    String val = value_text(0);
                    if (val == null) {
                        result();
                        return;
                    }
                    result(unaccent(val));
                }
            });
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erro ao registrar função personalizada UNACCENT no SQLite.", e);
        }
    }

    /**
     * Remove acentuação e caracteres diacríticos, convertendo para minúsculas.
     */
    public static String unaccent(String text) {
        if (text == null) return null;
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private void initSchema() {
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS produtos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nome TEXT NOT NULL,
                grupo TEXT DEFAULT 'GERAL',
                subcategoria TEXT DEFAULT '',
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
        String createIndexSubcategoria = "CREATE INDEX IF NOT EXISTS idx_produtos_subcategoria ON produtos(subcategoria);";
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
                numero TEXT DEFAULT '',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """;

        String seedFuncionarios = """
            INSERT OR IGNORE INTO funcionarios (nome, numero) VALUES 
            ('Administrador', '01'),
            ('Padrão', '02');
            """;

        String createTableProdutoCores = """
            CREATE TABLE IF NOT EXISTS produto_cores (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                produto_id INTEGER NOT NULL,
                nome_cor TEXT NOT NULL,
                quantidade INTEGER NOT NULL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(produto_id) REFERENCES produtos(id) ON DELETE CASCADE,
                UNIQUE(produto_id, nome_cor)
            );
            """;

        String createIndexCoresProd = "CREATE INDEX IF NOT EXISTS idx_produto_cores_prod ON produto_cores(produto_id);";
        String createIndexCoresNome = "CREATE INDEX IF NOT EXISTS idx_produto_cores_nome ON produto_cores(nome_cor);";

        String createTableProdutoSubcategorias = """
            CREATE TABLE IF NOT EXISTS produto_subcategorias (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                produto_id INTEGER NOT NULL,
                nome_subcategoria TEXT NOT NULL,
                quantidade INTEGER NOT NULL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(produto_id) REFERENCES produtos(id) ON DELETE CASCADE,
                UNIQUE(produto_id, nome_subcategoria)
            );
            """;

        String createIndexSubcategoriasProd = "CREATE INDEX IF NOT EXISTS idx_produto_subcategorias_prod ON produto_subcategorias(produto_id);";
        String createIndexSubcategoriasNome = "CREATE INDEX IF NOT EXISTS idx_produto_subcategorias_nome ON produto_subcategorias(nome_subcategoria);";

        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(createTableSql);

            // Migração automática caso a tabela já existisse sem a coluna grupo
            try {
                stmt.execute("ALTER TABLE produtos ADD COLUMN grupo TEXT DEFAULT 'GERAL';");
            } catch (SQLException ignored) {
                // Coluna já existe
            }

            // Migração automática caso a tabela já existisse sem a coluna subcategoria
            try {
                stmt.execute("ALTER TABLE produtos ADD COLUMN subcategoria TEXT DEFAULT '';");
            } catch (SQLException ignored) {
                // Coluna já existe
            }

            stmt.execute(createIndexCodigoLoja);
            stmt.execute(createIndexGrupo);
            stmt.execute(createIndexSubcategoria);
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
            // Migração da coluna numero em funcionarios
            try {
                stmt.execute("ALTER TABLE funcionarios ADD COLUMN numero TEXT DEFAULT '';");
            } catch (SQLException ignored) {
                // Coluna já existe
            }
            stmt.execute(seedFuncionarios);

            stmt.execute(createTableProdutoCores);
            stmt.execute(createIndexCoresProd);
            stmt.execute(createIndexCoresNome);

            stmt.execute(createTableProdutoSubcategorias);
            stmt.execute(createIndexSubcategoriasProd);
            stmt.execute(createIndexSubcategoriasNome);

            // Migração da tabela bases: de 'codigo' (UNIQUE) para 'tamanho' (sem restrição UNIQUE)
            try {
                boolean temCodigo = false;
                boolean temTamanho = false;
                try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(bases);")) {
                    while (rs.next()) {
                        String colName = rs.getString("name");
                        if ("codigo".equalsIgnoreCase(colName)) temCodigo = true;
                        if ("tamanho".equalsIgnoreCase(colName)) temTamanho = true;
                    }
                }
                if (temCodigo && !temTamanho) {
                    LOGGER.info("Migrando tabela 'bases' de 'codigo' para 'tamanho' (sem restrição UNIQUE)...");
                    stmt.execute("PRAGMA foreign_keys = OFF;");
                    stmt.execute("""
                        CREATE TABLE bases_migration (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            tamanho TEXT NOT NULL,
                            nome TEXT NOT NULL,
                            tipo TEXT NOT NULL,
                            fabricante TEXT DEFAULT 'Geral',
                            quantidade INTEGER NOT NULL DEFAULT 0,
                            valor_compra REAL NOT NULL DEFAULT 0.0,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        );
                    """);
                    stmt.execute("""
                        INSERT INTO bases_migration (id, tamanho, nome, tipo, fabricante, quantidade, valor_compra, created_at, updated_at)
                        SELECT id, codigo, nome, tipo, fabricante, quantidade, valor_compra, created_at, updated_at FROM bases;
                    """);
                    stmt.execute("DROP TABLE bases;");
                    stmt.execute("ALTER TABLE bases_migration RENAME TO bases;");
                    stmt.execute("PRAGMA foreign_keys = ON;");
                    LOGGER.info("Tabela 'bases' migrada com sucesso para 'tamanho' (sem restrição UNIQUE).");
                }
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Aviso na verificação/migração da tabela bases: " + e.getMessage());
            }

            String createTableBases = """
                CREATE TABLE IF NOT EXISTS bases (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    tamanho TEXT NOT NULL,
                    nome TEXT NOT NULL,
                    tipo TEXT NOT NULL,
                    fabricante TEXT DEFAULT 'Geral',
                    quantidade INTEGER NOT NULL DEFAULT 0,
                    valor_compra REAL NOT NULL DEFAULT 0.0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
                """;

            String createIndexBasesTamanho = "CREATE INDEX IF NOT EXISTS idx_bases_tamanho ON bases(tamanho);";
            String createIndexBasesTipo = "CREATE INDEX IF NOT EXISTS idx_bases_tipo ON bases(tipo);";
            String createIndexBasesNome = "CREATE INDEX IF NOT EXISTS idx_bases_nome ON bases(nome);";

            String createTableHistoricoBases = """
                CREATE TABLE IF NOT EXISTS historico_bases (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    base_id INTEGER NOT NULL,
                    data_hora TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                    tipo TEXT NOT NULL,
                    quantidade_alterada INTEGER NOT NULL,
                    quantidade_anterior INTEGER NOT NULL,
                    quantidade_nova INTEGER NOT NULL,
                    motivo TEXT,
                    responsavel TEXT DEFAULT 'Não informado',
                    FOREIGN KEY(base_id) REFERENCES bases(id) ON DELETE CASCADE
                );
                """;

            String createIndexHistBasesBase = "CREATE INDEX IF NOT EXISTS idx_hist_bases_base ON historico_bases(base_id);";
            String createIndexHistBasesData = "CREATE INDEX IF NOT EXISTS idx_hist_bases_data ON historico_bases(data_hora);";

            stmt.execute(createTableBases);
            stmt.execute(createIndexBasesTamanho);
            stmt.execute(createIndexBasesTipo);
            stmt.execute(createIndexBasesNome);

            stmt.execute(createTableHistoricoBases);
            stmt.execute(createIndexHistBasesBase);
            stmt.execute(createIndexHistBasesData);

            // Sanitização e autocorreção de registros históricos antigos
            try {
                stmt.execute("UPDATE historico_estoque SET quantidade_alterada = -ABS(quantidade_alterada) WHERE UPPER(tipo) = 'SAIDA' AND quantidade_alterada > 0;");
                stmt.execute("UPDATE historico_estoque SET quantidade_alterada = ABS(quantidade_alterada) WHERE UPPER(tipo) = 'ENTRADA' AND quantidade_alterada < 0;");
            } catch (SQLException ignored) {
            }

            LOGGER.info("Schema das tabelas 'produtos', 'historico_estoque', 'funcionarios', 'produto_cores', 'bases' e 'historico_bases' verificado com sucesso.");
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
                initSchema();
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
        } finally {
            if (this == instance) {
                instance = null;
            }
        }
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("JVM Shutdown detectado. Fechando conexão do banco de dados...");
            close();
        }));
    }
}
