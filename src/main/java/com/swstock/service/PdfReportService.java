package com.swstock.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.swstock.model.HistoricoEstoque;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serviço responsável pela geração de relatórios em PDF das movimentações de estoque,
 * respeitando os filtros ativos em tela (Todos os Tipos, Entradas ou Saídas, Fabricante/Grupo, período e busca),
 * agrupando as movimentações por data (dia) com subtotais diários e consolidados.
 */
public class PdfReportService {

    private static final Logger LOGGER = Logger.getLogger(PdfReportService.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static final Font FONT_TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, new Color(15, 23, 42));
    private static final Font FONT_SUBTITLE = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(100, 116, 139));
    private static final Font FONT_SECTION_DATE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(30, 41, 59));
    private static final Font FONT_HEADER_TABLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
    private static final Font FONT_CELL_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(15, 23, 42));
    private static final Font FONT_CELL = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(51, 65, 85));
    private static final Font FONT_CELL_GREEN = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(22, 101, 52));
    private static final Font FONT_CELL_RED = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(185, 28, 28));
    private static final Font FONT_FOOTER = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(148, 163, 184));

    /**
     * Gera o relatório PDF das movimentações filtradas de estoque agrupadas por data.
     */
    public void gerarRelatorio(File arquivoDestino, List<HistoricoEstoque> movimentos,
                               LocalDate dataInicio, LocalDate dataFim,
                               String tipoFiltro, String grupoFiltro, String termoBusca) throws Exception {
        Document document = new Document(PageSize.A4, 28, 28, 28, 28);

        try (FileOutputStream out = new FileOutputStream(arquivoDestino)) {
            PdfWriter writer = PdfWriter.getInstance(document, out);

            // Rodapé com numeração de páginas
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter writer, Document doc) {
                    PdfContentByte cb = writer.getDirectContent();
                    ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                            new Phrase(String.format("SWStock - Sistema de Controle de Estoque | Página %d", writer.getPageNumber()), FONT_FOOTER),
                            (doc.right() - doc.left()) / 2 + doc.leftMargin(), doc.bottom() - 14, 0);
                }
            });

            document.open();

            // 1. Cabeçalho Executivo do Documento
            adicionarCabecalho(document, dataInicio, dataFim, tipoFiltro, grupoFiltro, termoBusca, movimentos);

            // 2. Agrupamento das Movimentações por Dia (Data)
            Map<String, List<HistoricoEstoque>> agrupadoPorDia = agruparPorData(movimentos);

            if (agrupadoPorDia.isEmpty()) {
                Paragraph pVazio = new Paragraph("\nNenhuma movimentação de estoque encontrada para os filtros selecionados.", FONT_CELL);
                pVazio.setAlignment(Element.ALIGN_CENTER);
                document.add(pVazio);
            } else {
                for (Map.Entry<String, List<HistoricoEstoque>> entry : agrupadoPorDia.entrySet()) {
                    String dataFormatada = entry.getKey();
                    List<HistoricoEstoque> listaDia = entry.getValue();

                    adicionarSecaoDia(document, dataFormatada, listaDia, tipoFiltro);
                }
            }

            document.close();
            LOGGER.info("Relatório PDF gerado com sucesso: " + arquivoDestino.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao gerar relatório PDF.", e);
            throw e;
        }
    }

    public void gerarRelatorio(File arquivoDestino, List<HistoricoEstoque> movimentos,
                               LocalDate dataInicio, LocalDate dataFim,
                               String tipoFiltro, String termoBusca) throws Exception {
        gerarRelatorio(arquivoDestino, movimentos, dataInicio, dataFim, tipoFiltro, null, termoBusca);
    }

    public void gerarRelatorioSaidasPorData(File arquivoDestino, List<HistoricoEstoque> saidas,
                                           LocalDate dataInicio, LocalDate dataFim) throws Exception {
        gerarRelatorio(arquivoDestino, saidas, dataInicio, dataFim, "Apenas Saídas", null, null);
    }

    private void adicionarCabecalho(Document document, LocalDate dataInicio, LocalDate dataFim,
                                   String tipoFiltro, String grupoFiltro, String termoBusca,
                                   List<HistoricoEstoque> movimentos) throws DocumentException {
        String titulo;
        if (tipoFiltro == null || tipoFiltro.contains("Todos") || tipoFiltro.contains("Geral") || tipoFiltro.equalsIgnoreCase("TODOS")) {
            titulo = "SWStock - Relatório de Movimentações (Entradas e Saídas)";
        } else if (tipoFiltro.contains("Entrada") || tipoFiltro.equalsIgnoreCase("ENTRADA")) {
            titulo = "SWStock - Relatório de Entradas de Estoque";
        } else if (tipoFiltro.contains("Saída") || tipoFiltro.contains("Saida") || tipoFiltro.equalsIgnoreCase("SAIDA")) {
            titulo = "SWStock - Relatório de Saídas de Estoque (Produtos Retirados)";
        } else {
            titulo = "SWStock - Relatório de Movimentações";
        }

        if (grupoFiltro != null && !grupoFiltro.trim().isEmpty() && !grupoFiltro.toUpperCase().contains("TODOS")) {
            titulo += " - " + grupoFiltro.trim();
        }

        Paragraph pTitulo = new Paragraph(titulo, FONT_TITLE);
        document.add(pTitulo);

        // Linha de contexto (Período, Filtros e Data de Emissão)
        StringBuilder sub = new StringBuilder();
        if (dataInicio != null && dataFim != null) {
            sub.append("Período: ").append(dataInicio.format(DATE_FORMATTER)).append(" até ").append(dataFim.format(DATE_FORMATTER));
        } else if (dataInicio != null) {
            sub.append("Data: ").append(dataInicio.format(DATE_FORMATTER));
        } else {
            sub.append("Período: Todo o Histórico");
        }

        if (tipoFiltro != null && !tipoFiltro.trim().isEmpty()) {
            sub.append(" | Operação: ").append(tipoFiltro);
        }
        if (grupoFiltro != null && !grupoFiltro.trim().isEmpty() && !grupoFiltro.toUpperCase().contains("TODOS")) {
            sub.append(" | Fabricante: ").append(grupoFiltro.trim());
        }
        if (termoBusca != null && !termoBusca.trim().isEmpty()) {
            sub.append(" | Busca: '").append(termoBusca.trim()).append("'");
        }
        sub.append(" | Emitido em: ").append(LocalDateTime.now().format(DATE_TIME_FORMATTER));

        Paragraph pSub = new Paragraph(sub.toString(), FONT_SUBTITLE);
        pSub.setSpacingAfter(8);
        document.add(pSub);

        // Métricas Consolidadas do Cabeçalho
        int totalEntradas = 0;
        int totalSaidas = 0;
        for (HistoricoEstoque h : movimentos) {
            int delta = h.getQuantidadeAlterada() != null ? h.getQuantidadeAlterada() : 0;
            if (delta > 0) {
                totalEntradas += delta;
            } else if (delta < 0) {
                totalSaidas += Math.abs(delta);
            }
        }
        int balanco = totalEntradas - totalSaidas;

        PdfPTable tblResumo = new PdfPTable(4);
        tblResumo.setWidthPercentage(100);
        tblResumo.setWidths(new float[]{25f, 25f, 25f, 25f});
        tblResumo.setSpacingBefore(2);
        tblResumo.setSpacingAfter(10);

        PdfPCell c1 = criarCardResumo("REGISTROS", movimentos.size() + " mov.", new Color(241, 245, 249), new Color(15, 23, 42));
        PdfPCell c2 = criarCardResumo("TOTAL ENTRADAS", "+" + totalEntradas + " un.", new Color(240, 253, 244), new Color(22, 101, 52));
        PdfPCell c3 = criarCardResumo("TOTAL SAÍDAS", "-" + totalSaidas + " un.", new Color(254, 242, 242), new Color(185, 28, 28));
        PdfPCell c4 = criarCardResumo("BALANÇO LÍQUIDO", (balanco >= 0 ? "+" : "") + balanco + " un.", new Color(239, 246, 255), new Color(29, 78, 216));

        tblResumo.addCell(c1);
        tblResumo.addCell(c2);
        tblResumo.addCell(c3);
        tblResumo.addCell(c4);
        document.add(tblResumo);
    }

    private PdfPCell criarCardResumo(String label, String valor, Color bgColor, Color textColor) {
        Font fLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, new Color(100, 116, 139));
        Font fVal = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, textColor);

        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "\n", fLabel));
        p.add(new Chunk(valor, fVal));

        PdfPCell cell = new PdfPCell(p);
        cell.setBackgroundColor(bgColor);
        cell.setPadding(5);
        cell.setBorderColor(new Color(226, 232, 240));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    private void adicionarSecaoDia(Document document, String dataFormatada,
                                  List<HistoricoEstoque> listaDia, String tipoFiltro) throws DocumentException {
        // Título do Dia
        Paragraph pDia = new Paragraph("Data: " + dataFormatada + " (" + listaDia.size() + " movimentações)", FONT_SECTION_DATE);
        pDia.setSpacingBefore(8);
        pDia.setSpacingAfter(4);
        document.add(pDia);

        // Tabela com as 5 colunas:
        // 1. Qtd. Movimento | 2. Responsável | 3. Nome do Produto (SKU) | 4. Fabricante/Grupo | 5. Estoque Restante
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{14f, 18f, 36f, 18f, 14f});
        table.setSpacingAfter(8);

        String col1Header = "MOVIMENTO";
        if (tipoFiltro == null || tipoFiltro.contains("Todos") || tipoFiltro.contains("Geral") || tipoFiltro.equalsIgnoreCase("TODOS")) {
            col1Header = "MOVIMENTO";
        } else if (tipoFiltro.contains("Saída") || tipoFiltro.contains("Saida") || tipoFiltro.equalsIgnoreCase("SAIDA")) {
            col1Header = "QTD. RETIRADA";
        } else if (tipoFiltro.contains("Entrada") || tipoFiltro.equalsIgnoreCase("ENTRADA")) {
            col1Header = "QTD. ENTRADA";
        }

        adicionarCabecalhoColuna(table, col1Header, Element.ALIGN_CENTER);
        adicionarCabecalhoColuna(table, "RESPONSÁVEL", Element.ALIGN_LEFT);
        adicionarCabecalhoColuna(table, "NOME DO PRODUTO (SKU)", Element.ALIGN_LEFT);
        adicionarCabecalhoColuna(table, "FABRICANTE / GRUPO", Element.ALIGN_LEFT);
        adicionarCabecalhoColuna(table, "ESTOQUE", Element.ALIGN_CENTER);

        int totalDiaEntradas = 0;
        int totalDiaSaidas = 0;

        for (int i = 0; i < listaDia.size(); i++) {
            HistoricoEstoque h = listaDia.get(i);
            Color bg = (i % 2 == 0) ? Color.WHITE : new Color(248, 250, 252);

            int delta = h.getQuantidadeAlterada() != null ? h.getQuantidadeAlterada() : 0;
            if (delta > 0) {
                totalDiaEntradas += delta;
            } else if (delta < 0) {
                totalDiaSaidas += Math.abs(delta);
            }

            String responsavel = h.getResponsavel() != null && !h.getResponsavel().trim().isEmpty() ? h.getResponsavel() : "Não informado";
            String produto = h.getProdutoNome() + (h.getProdutoCodigo() != null && !h.getProdutoCodigo().isEmpty() ? " [" + h.getProdutoCodigo() + "]" : "");
            String grupo = h.getProdutoGrupo() != null ? h.getProdutoGrupo() : "GERAL";
            String restante = (h.getQuantidadeNova() != null ? h.getQuantidadeNova() : 0) + " un.";

            // 1. Quantidade com sinal e destaque visual
            String movTexto = h.getMovimentoFormatado();
            Font movFont = delta > 0 ? FONT_CELL_GREEN : (delta < 0 ? FONT_CELL_RED : FONT_CELL_BOLD);
            PdfPCell cellQtd = new PdfPCell(new Phrase(movTexto, movFont));
            cellQtd.setHorizontalAlignment(Element.ALIGN_CENTER);
            cellQtd.setBackgroundColor(bg);
            cellQtd.setPadding(4);
            cellQtd.setBorderColor(new Color(226, 232, 240));
            table.addCell(cellQtd);

            // 2. Responsável
            PdfPCell cellResp = new PdfPCell(new Phrase(responsavel, FONT_CELL_BOLD));
            cellResp.setHorizontalAlignment(Element.ALIGN_LEFT);
            cellResp.setBackgroundColor(bg);
            cellResp.setPadding(4);
            cellResp.setBorderColor(new Color(226, 232, 240));
            table.addCell(cellResp);

            // 3. Nome do Produto
            PdfPCell cellProd = new PdfPCell(new Phrase(produto, FONT_CELL));
            cellProd.setHorizontalAlignment(Element.ALIGN_LEFT);
            cellProd.setBackgroundColor(bg);
            cellProd.setPadding(4);
            cellProd.setBorderColor(new Color(226, 232, 240));
            table.addCell(cellProd);

            // 4. Fabricante / Grupo
            PdfPCell cellGrupo = new PdfPCell(new Phrase(grupo, FONT_CELL_BOLD));
            cellGrupo.setHorizontalAlignment(Element.ALIGN_LEFT);
            cellGrupo.setBackgroundColor(bg);
            cellGrupo.setPadding(4);
            cellGrupo.setBorderColor(new Color(226, 232, 240));
            table.addCell(cellGrupo);

            // 5. Estoque Restante
            PdfPCell cellRestante = new PdfPCell(new Phrase(restante, FONT_CELL_BOLD));
            cellRestante.setHorizontalAlignment(Element.ALIGN_CENTER);
            cellRestante.setBackgroundColor(bg);
            cellRestante.setPadding(4);
            cellRestante.setBorderColor(new Color(226, 232, 240));
            table.addCell(cellRestante);
        }

        // Subtotais do Dia
        String subtotalTexto;
        if (totalDiaEntradas > 0 && totalDiaSaidas > 0) {
            subtotalTexto = String.format("Entradas: +%d un.  |  Saídas: -%d un.  |  Balanço: %s%d un.",
                    totalDiaEntradas, totalDiaSaidas, (totalDiaEntradas - totalDiaSaidas >= 0 ? "+" : ""), (totalDiaEntradas - totalDiaSaidas));
        } else if (totalDiaEntradas > 0) {
            subtotalTexto = String.format("Total Adicionado no Dia: +%d un.", totalDiaEntradas);
        } else {
            subtotalTexto = String.format("Total Retirado no Dia: -%d un.", totalDiaSaidas);
        }

        PdfPCell cellSubLabel = new PdfPCell(new Phrase("Subtotal (" + dataFormatada + "):", FONT_CELL_BOLD));
        cellSubLabel.setColspan(1);
        cellSubLabel.setBackgroundColor(new Color(241, 245, 249));
        cellSubLabel.setPadding(5);
        cellSubLabel.setBorderColor(new Color(203, 213, 225));
        table.addCell(cellSubLabel);

        PdfPCell cellSubQtd = new PdfPCell(new Phrase(subtotalTexto, FONT_CELL_BOLD));
        cellSubQtd.setColspan(4);
        cellSubQtd.setBackgroundColor(new Color(241, 245, 249));
        cellSubQtd.setPadding(5);
        cellSubQtd.setBorderColor(new Color(203, 213, 225));
        table.addCell(cellSubQtd);

        document.add(table);
    }

    private void adicionarCabecalhoColuna(PdfPTable table, String texto, int alinhamento) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, FONT_HEADER_TABLE));
        cell.setBackgroundColor(new Color(30, 41, 59));
        cell.setHorizontalAlignment(alinhamento);
        cell.setPadding(5);
        cell.setBorderColor(new Color(51, 65, 85));
        table.addCell(cell);
    }

    private Map<String, List<HistoricoEstoque>> agruparPorData(List<HistoricoEstoque> lista) {
        Map<String, List<HistoricoEstoque>> mapa = new LinkedHashMap<>();

        for (HistoricoEstoque h : lista) {
            String dataHora = h.getDataHora();
            String diaStr = "Data não identificada";

            if (dataHora != null && !dataHora.trim().isEmpty()) {
                try {
                    String limpo = dataHora.replace("T", " ");
                    if (limpo.length() >= 10) {
                        String dataParte = limpo.substring(0, 10);
                        LocalDate ld = LocalDate.parse(dataParte);
                        diaStr = ld.format(DATE_FORMATTER);
                    }
                } catch (Exception e) {
                    diaStr = dataHora;
                }
            }

            mapa.computeIfAbsent(diaStr, k -> new ArrayList<>()).add(h);
        }

        return mapa;
    }
}
