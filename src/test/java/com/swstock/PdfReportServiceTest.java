package com.swstock;

import com.swstock.model.HistoricoEstoque;
import com.swstock.service.PdfReportService;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
                "Produto Teste 100", "SKU-100", "Estante A1"
        ));
        movimentos.add(new HistoricoEstoque(
                2, 102, "2026-08-28 11:30:00", "ENTRADA",
                +20, 10, 30, "Chegada de Nota Fiscal", "Operador 2",
                "Produto Teste 200", "SKU-200", "Estante B2"
        ));
        movimentos.add(new HistoricoEstoque(
                3, 103, "2026-08-27 09:00:00", "SAIDA",
                -10, 50, 40, "Venda Direta Balcão", "Operador 3",
                "Parafuso Autoatarraxante 4,2x38", "305011", "Estante C3"
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

        assertTrue(tempPdf.exists());
        assertTrue(tempPdf.length() > 500);
    }
}
