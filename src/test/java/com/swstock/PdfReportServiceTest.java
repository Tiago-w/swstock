package com.swstock;

import com.swstock.model.HistoricoEstoque;
import com.swstock.service.PdfReportService;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PdfReportServiceTest {

    @Test
    void testGerarRelatorioPdfCompletoTodosOsTipos() throws Exception {
        PdfReportService service = new PdfReportService();
        File tempPdf = File.createTempFile("teste_relatorio_todos_", ".pdf");
        tempPdf.deleteOnExit();

        List<HistoricoEstoque> movimentos = new ArrayList<>();
        movimentos.add(new HistoricoEstoque(
                1, 101, "2026-08-28 10:15:00", "SAIDA",
                -5, 45, 40, "Retirada para Expedição", "Operador 1",
                "Produto Teste 101", "SKU-101", "Estante A1"
        ));
        movimentos.add(new HistoricoEstoque(
                2, 102, "2026-08-28 11:30:00", "ENTRADA",
                +20, 10, 30, "Chegada de Nota Fiscal", "Operador 2",
                "Produto Teste 102", "SKU-102", "Estante B2"
        ));
        movimentos.add(new HistoricoEstoque(
                3, 103, "2026-08-27 09:00:00", "SAIDA",
                -10, 50, 40, "Venda Direta Balcão", "Operador 3",
                "Produto Teste 103", "SKU-103", "Estante C3"
        ));

        service.gerarRelatorio(tempPdf, movimentos, LocalDate.of(2026, 8, 27), LocalDate.of(2026, 8, 28),
                "Todos os Tipos (Entradas e Saídas)", "Grupo A");

        assertTrue(tempPdf.exists(), "O arquivo PDF deve ser criado.");
        assertTrue(tempPdf.length() > 500, "O arquivo PDF deve conter dados gerados (>500 bytes).");
    }

    @Test
    void testGerarRelatorioPdfApenasEntradas() throws Exception {
        PdfReportService service = new PdfReportService();
        File tempPdf = File.createTempFile("teste_relatorio_entradas_", ".pdf");
        tempPdf.deleteOnExit();

        List<HistoricoEstoque> entradas = new ArrayList<>();
        entradas.add(new HistoricoEstoque(
                1, 101, "2026-08-28 10:00:00", "ENTRADA",
                +50, 0, 50, "Cadastro Inicial", "Operador 1",
                "Produto Teste 101", "SKU-101", "Estante A2"
        ));

        service.gerarRelatorio(tempPdf, entradas, LocalDate.now(), LocalDate.now(), "Apenas Entradas", null);

        assertTrue(tempPdf.exists(), "O arquivo PDF de entradas deve ser criado.");
        assertTrue(tempPdf.length() > 500, "O arquivo PDF deve conter conteúdo.");
    }

    @Test
    void testGerarRelatorioPdfComCoresEUnica() throws Exception {
        PdfReportService service = new PdfReportService();
        File tempPdf = File.createTempFile("teste_relatorio_cores_", ".pdf");
        tempPdf.deleteOnExit();

        List<HistoricoEstoque> movs = new ArrayList<>();
        movs.add(new HistoricoEstoque(
                1, 201, "2026-08-30 14:00:00", "SAIDA",
                -2, 10, 8, "[AZUL] Retirada para pintura", "Operador 1",
                "Produto Teste 201", "SKU-201", "Estante A1"
        ));
        movs.add(new HistoricoEstoque(
                2, 202, "2026-08-30 15:30:00", "ENTRADA",
                +15, 0, 15, "Entrada Geral", "Operador 2",
                "Produto Teste 202", "SKU-202", "Estante B1"
        ));

        service.gerarRelatorio(tempPdf, movs, LocalDate.now(), LocalDate.now(),
                "Entradas e Saídas apenas (Sem Ajustes)", "Grupo B", null);

        assertTrue(tempPdf.exists(), "O arquivo PDF com coluna de cores deve ser criado.");
        assertTrue(tempPdf.length() > 500, "O arquivo PDF deve conter dados.");
    }

    @Test
    void testGerarRelatorioLevantamentoEstoque() throws Exception {
        PdfReportService service = new PdfReportService();
        File tempPdf = File.createTempFile("teste_levantamento_", ".pdf");
        tempPdf.deleteOnExit();

        List<com.swstock.model.Produto> produtos = new ArrayList<>();
        com.swstock.model.Produto p1 = new com.swstock.model.Produto(1, "Produto Teste 1", "Grupo A", 20.0, 25.0, "SKU-001", "Estante A1", 15, null, null);
        com.swstock.model.Produto p2 = new com.swstock.model.Produto(2, "Produto Teste 2", "Grupo B", 10.0, 12.0, "SKU-002", "Estante B1", 40, null, null);
        produtos.add(p1);
        produtos.add(p2);

        Map<Integer, List<com.swstock.model.ProdutoCor>> mapaCores = new HashMap<>();
        List<com.swstock.model.ProdutoCor> coresP1 = new ArrayList<>();
        coresP1.add(new com.swstock.model.ProdutoCor(1, 1, "AZUL", 10));
        coresP1.add(new com.swstock.model.ProdutoCor(2, 1, "VERMELHO", 5));
        mapaCores.put(1, coresP1);

        service.gerarRelatorioLevantamentoEstoque(tempPdf, produtos, mapaCores, "Grupo A", null);

        assertTrue(tempPdf.exists(), "O arquivo PDF de levantamento deve ser gerado.");
        assertTrue(tempPdf.length() > 500, "O arquivo PDF de levantamento deve conter conteúdo válido.");
    }
}
