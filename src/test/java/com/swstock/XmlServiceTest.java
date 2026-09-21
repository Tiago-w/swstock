package com.swstock;

import com.swstock.database.DatabaseManager;
import com.swstock.database.FuncionarioDAO;
import com.swstock.database.HistoricoEstoqueDAO;
import com.swstock.database.ProdutoCorDAO;
import com.swstock.database.ProdutoDAO;
import com.swstock.model.Funcionario;
import com.swstock.model.HistoricoEstoque;
import com.swstock.model.Produto;
import com.swstock.model.ProdutoCor;
import com.swstock.service.XmlService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class XmlServiceTest {

    private static final String TEST_DB_PATH = "test_xml_swstock.db";
    private static DatabaseManager dbManager;
    private static ProdutoDAO produtoDAO;
    private static HistoricoEstoqueDAO historicoDAO;
    private static ProdutoCorDAO produtoCorDAO;
    private static FuncionarioDAO funcionarioDAO;
    private static XmlService xmlService;

    @BeforeAll
    static void setUp() {
        File dbFile = new File(TEST_DB_PATH);
        if (dbFile.exists()) {
            boolean deleted = dbFile.delete();
            if (!deleted) {
                dbFile.deleteOnExit();
            }
        }
        dbManager = DatabaseManager.getInstance(TEST_DB_PATH);
        produtoDAO = new ProdutoDAO(dbManager);
        historicoDAO = new HistoricoEstoqueDAO(dbManager);
        produtoCorDAO = new ProdutoCorDAO(dbManager);
        funcionarioDAO = new FuncionarioDAO(dbManager);
        xmlService = new XmlService(produtoDAO, historicoDAO, produtoCorDAO, funcionarioDAO);
    }

    @AfterAll
    static void tearDown() {
        if (dbManager != null) {
            dbManager.close();
        }
        File dbFile = new File(TEST_DB_PATH);
        if (dbFile.exists()) {
            boolean deleted = dbFile.delete();
            if (!deleted) {
                dbFile.deleteOnExit();
            }
        }
    }

    @Test
    @Order(1)
    void testImportacaoEExportacaoXml() throws Exception {
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <estoque>
                <produto>
                    <codigoLoja>TEST-001</codigoLoja>
                    <nome>Placa de Vídeo RTX 4060</nome>
                    <precoVista>1899.90</precoVista>
                    <precoPrazo>2099.90</precoPrazo>
                    <localizacao>Estante A3</localizacao>
                    <quantidade>7</quantidade>
                    <urlImagem></urlImagem>
                    <descricaoBreve>Placa de vídeo 8GB GDDR6 DLSS 3</descricaoBreve>
                </produto>
                <produto>
                    <codigoLoja>TEST-002</codigoLoja>
                    <nome>Fonte Modular 650W Bronze</nome>
                    <precoVista>349.00</precoVista>
                    <precoPrazo>389.00</precoPrazo>
                    <localizacao>Estante B3</localizacao>
                    <quantidade>14</quantidade>
                    <urlImagem></urlImagem>
                    <descricaoBreve>Fonte semi-modular certificada 80 Plus Bronze</descricaoBreve>
                </produto>
            </estoque>
            """;

        File tempImportFile = File.createTempFile("swstock_import_", ".xml");
        Files.writeString(tempImportFile.toPath(), xmlContent);

        XmlService.ImportResult result = xmlService.importarProdutos(tempImportFile);
        assertEquals(2, result.totalLidos());
        assertEquals(2, result.totalProcessados());
        assertTrue(result.erros().isEmpty());

        List<Produto> produtos = produtoDAO.findAll();
        assertEquals(2, produtos.size());

        File tempExportFile = File.createTempFile("swstock_export_", ".xml");
        xmlService.exportarProdutos(tempExportFile);

        assertTrue(tempExportFile.exists());
        String exportedXml = Files.readString(tempExportFile.toPath());
        assertTrue(exportedXml.contains("<codigoLoja>TEST-001</codigoLoja>"));
        assertTrue(exportedXml.contains("<nome>Placa de Vídeo RTX 4060</nome>"));
        assertTrue(exportedXml.contains("<codigoLoja>TEST-002</codigoLoja>"));
        assertTrue(exportedXml.contains("<produtos>"));
        assertTrue(exportedXml.contains("<produto_cores"));
        assertTrue(exportedXml.contains("<historico_estoque"));
        assertTrue(exportedXml.contains("<funcionarios"));

        if (!tempImportFile.delete()) tempImportFile.deleteOnExit();
        if (!tempExportFile.delete()) tempExportFile.deleteOnExit();
    }

    @Test
    @Order(2)
    void testPreservacaoEstoqueELocalizacaoEmImportacaoMensal() throws Exception {
        Produto existente = produtoDAO.findByCodigoLoja("TEST-001");
        assertNotNull(existente);
        existente.setQuantidade(45);
        existente.setLocalizacao("Estante A1");
        produtoDAO.update(existente);

        String xmlMensal = """
            <?xml version="1.0" encoding="UTF-8"?>
            <estoque>
                <produto>
                    <codigoLoja>TEST-001</codigoLoja>
                    <nome>Placa de Vídeo RTX 4060 SUPER 8GB (NOVO NOME)</nome>
                    <precoVista>2299.90</precoVista>
                    <precoPrazo>2499.90</precoPrazo>
                    <localizacao></localizacao>
                    <quantidade>0</quantidade>
                    <urlImagem></urlImagem>
                    <descricaoBreve>Preço atualizado pelo fornecedor</descricaoBreve>
                </produto>
                <produto>
                    <codigoLoja>TEST-003</codigoLoja>
                    <nome>Teclado Mecânico RGB</nome>
                    <precoVista>199.90</precoVista>
                    <precoPrazo>229.90</precoPrazo>
                    <localizacao>Estante C2</localizacao>
                    <quantidade>20</quantidade>
                    <urlImagem></urlImagem>
                    <descricaoBreve>Novo produto adicionado no catálogo</descricaoBreve>
                </produto>
            </estoque>
            """;

        File tempXml = File.createTempFile("swstock_mensal_", ".xml");
        Files.writeString(tempXml.toPath(), xmlMensal);

        XmlService.ImportResult result = xmlService.importarProdutos(tempXml);

        assertEquals(2, result.totalLidos());
        assertEquals(1, result.novosInseridos());
        assertEquals(1, result.atualizadosPreservados());
        assertEquals(1, result.precosAlterados());
        assertEquals(1, result.nomesAlterados());

        Produto atualizado = produtoDAO.findByCodigoLoja("TEST-001");
        assertNotNull(atualizado);
        assertEquals("Placa de Vídeo RTX 4060 SUPER 8GB (NOVO NOME)", atualizado.getNome());
        assertEquals(2299.90, atualizado.getPrecoVista());
        assertEquals(45, atualizado.getQuantidade(), "A quantidade física de estoque DEVE ser preservada intacta!");
        assertEquals("Estante A1", atualizado.getLocalizacao(), "A localização da estante DEVE ser preservada intacta!");

        Produto novo = produtoDAO.findByCodigoLoja("TEST-003");
        assertNotNull(novo);
        assertEquals("Teclado Mecânico RGB", novo.getNome());
        assertEquals(20, novo.getQuantidade());

        if (!tempXml.delete()) tempXml.deleteOnExit();
    }

    @Test
    @Order(3)
    void testBackupCompleto360ERestauracaoEmBancoLimpo() throws Exception {
        // 1. Cadastrar dados completos: cores, historico, funcionario
        Produto p1 = produtoDAO.findByCodigoLoja("TEST-001");
        assertNotNull(p1);

        produtoCorDAO.addCor(p1.getId(), "PRETO FOSCO", 15, "Tiago");
        produtoCorDAO.addCor(p1.getId(), "BRANCO TITÂNIO", 30, "Lucas");

        funcionarioDAO.insert(new Funcionario("Carlos Supervisor"));

        HistoricoEstoque hManual = new HistoricoEstoque(
                p1.getId(),
                "ENTRADA",
                10,
                35,
                45,
                "Recebimento de Lote de Fábrica",
                "Tiago"
        );
        hManual.setDataHora("2026-09-20 10:15:30");
        historicoDAO.insert(hManual);

        // 2. Exportar o backup completo 360 graus
        File backupFile = File.createTempFile("swstock_full_backup_", ".xml");
        xmlService.exportarProdutos(backupFile);
        assertTrue(backupFile.exists());

        String xmlStr = Files.readString(backupFile.toPath());
        assertTrue(xmlStr.contains("<swstock_backup"));
        assertTrue(xmlStr.contains("<nome_cor>PRETO FOSCO</nome_cor>"));
        assertTrue(xmlStr.contains("<nome_cor>BRANCO TITÂNIO</nome_cor>"));
        assertTrue(xmlStr.contains("<motivo>Recebimento de Lote de Fábrica</motivo>"));
        assertTrue(xmlStr.contains("<nome>Carlos Supervisor</nome>"));

        // 3. Simular restauração em um NOVO banco limpo (ex: PC queimou e usou pen drive)
        String freshDbPath = "test_fresh_restore_swstock.db";
        File freshDbFile = new File(freshDbPath);
        if (freshDbFile.exists()) freshDbFile.delete();

        DatabaseManager freshDbManager = DatabaseManager.getInstance(freshDbPath);
        ProdutoDAO freshProdutoDAO = new ProdutoDAO(freshDbManager);
        HistoricoEstoqueDAO freshHistoricoDAO = new HistoricoEstoqueDAO(freshDbManager);
        ProdutoCorDAO freshProdutoCorDAO = new ProdutoCorDAO(freshDbManager);
        FuncionarioDAO freshFuncionarioDAO = new FuncionarioDAO(freshDbManager);
        XmlService freshXmlService = new XmlService(freshProdutoDAO, freshHistoricoDAO, freshProdutoCorDAO, freshFuncionarioDAO);

        XmlService.ImportResult restoreResult = freshXmlService.importarProdutos(backupFile);

        assertEquals(3, restoreResult.novosInseridos());
        assertTrue(restoreResult.coresProcessadas() >= 2);
        assertTrue(restoreResult.movimentacoesHistoricoRestauradas() >= 1);
        assertTrue(restoreResult.funcionariosProcessados() >= 1);
        assertTrue(restoreResult.isSucesso());

        // Verificar restauração dos produtos
        List<Produto> produtosRestaurados = freshProdutoDAO.findAll();
        assertEquals(3, produtosRestaurados.size());

        Produto pRestaurado = freshProdutoDAO.findByCodigoLoja("TEST-001");
        assertNotNull(pRestaurado);
        assertEquals(45, pRestaurado.getQuantidade());
        assertEquals("Estante A1", pRestaurado.getLocalizacao());

        // Verificar restauração das cores
        List<ProdutoCor> coresRestauradas = freshProdutoCorDAO.findByProdutoId(pRestaurado.getId());
        assertEquals(2, coresRestauradas.size());

        // Verificar restauração do histórico
        List<HistoricoEstoque> historicoRestaurado = freshHistoricoDAO.findByProduto(pRestaurado.getId());
        assertTrue(historicoRestaurado.stream().anyMatch(h -> "Recebimento de Lote de Fábrica".equals(h.getMotivo())));

        // Verificar restauração dos funcionários
        List<String> funcionariosRestaurados = freshFuncionarioDAO.getNomesFuncionarios();
        assertTrue(funcionariosRestaurados.contains("Carlos Supervisor"));

        freshDbManager.close();
        if (!freshDbFile.delete()) freshDbFile.deleteOnExit();
        if (!backupFile.delete()) backupFile.deleteOnExit();
    }
}
