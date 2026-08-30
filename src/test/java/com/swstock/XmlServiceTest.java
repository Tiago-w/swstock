package com.swstock;

import com.swstock.database.DatabaseManager;
import com.swstock.database.ProdutoDAO;
import com.swstock.model.Produto;
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
        xmlService = new XmlService(produtoDAO);
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
}
