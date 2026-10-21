package com.swstock.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.swstock.model.HistoricoBase;
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

        // Tabela com as 7 colunas:
        // 1. Qtd. Movimento | 2. Responsável | 3. Nome do Produto (SKU) | 4. Cor | 5. Fabricante/Grupo | 6. Motivo / Origem | 7. Estoque Restante
        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{10f, 13f, 21f, 10f, 14f, 20f, 12f});
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
        adicionarCabecalhoColuna(table, "COR", Element.ALIGN_CENTER);
        adicionarCabecalhoColuna(table, "FABRICANTE / GRUPO", Element.ALIGN_LEFT);
        adicionarCabecalhoColuna(table, "MOTIVO / ORIGEM", Element.ALIGN_LEFT);
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
            String cor = extrairCor(h);
            String grupo = h.getProdutoGrupo() != null ? h.getProdutoGrupo() : "GERAL";
            String motivo = h.getMotivo() != null && !h.getMotivo().trim().isEmpty() ? h.getMotivo().trim() : "Modificação de estoque";
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

            // 4. Cor
            PdfPCell cellCor = new PdfPCell(new Phrase(cor, FONT_CELL_BOLD));
            cellCor.setHorizontalAlignment(Element.ALIGN_CENTER);
            cellCor.setBackgroundColor(bg);
            cellCor.setPadding(4);
            cellCor.setBorderColor(new Color(226, 232, 240));
            table.addCell(cellCor);

            // 5. Fabricante / Grupo
            PdfPCell cellGrupo = new PdfPCell(new Phrase(grupo, FONT_CELL_BOLD));
            cellGrupo.setHorizontalAlignment(Element.ALIGN_LEFT);
            cellGrupo.setBackgroundColor(bg);
            cellGrupo.setPadding(4);
            cellGrupo.setBorderColor(new Color(226, 232, 240));
            table.addCell(cellGrupo);

            // 6. Motivo / Origem
            PdfPCell cellMotivo = new PdfPCell(new Phrase(motivo, FONT_CELL));
            cellMotivo.setHorizontalAlignment(Element.ALIGN_LEFT);
            cellMotivo.setBackgroundColor(bg);
            cellMotivo.setPadding(4);
            cellMotivo.setBorderColor(new Color(226, 232, 240));
            table.addCell(cellMotivo);

            // 7. Estoque Restante
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
        cellSubQtd.setColspan(6);
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

    /**
     * Extrai a ramificação de cor do histórico (geralmente salva no motivo como '[AZUL] ...' ou 'Cadastro de cor [AZUL]')
     * ou retorna 'ÚNICA' caso o produto não possua variação de cor.
     */
    private String extrairCor(HistoricoEstoque h) {
        if (h == null) {
            return "ÚNICA";
        }
        String motivo = h.getMotivo();
        if (motivo != null) {
            String m = motivo.trim();
            int start = m.indexOf("[");
            int end = m.indexOf("]");
            if (start >= 0 && end > start) {
                String cor = m.substring(start + 1, end).trim();
                if (!cor.isEmpty() && !cor.equalsIgnoreCase("GERAL") && !cor.equalsIgnoreCase("TODAS") && !cor.equalsIgnoreCase("ÚNICA") && !cor.equalsIgnoreCase("UNICA")) {
                    return cor.toUpperCase();
                }
            }
        }
        return "ÚNICA";
    }

    /**
     * Gera o relatório PDF de Levantamento de Estoque e Ficha de Pedido.
     * Colunas: CÓDIGO DA LOJA, NOME DO PRODUTO, CORES, QUANTIDADE EM ESTOQUE, PREÇO À VISTA, PEDIDO (CANETA).
     */
    public void gerarRelatorioLevantamentoEstoque(File arquivoDestino, List<com.swstock.model.Produto> produtos,
                                                  Map<Integer, List<com.swstock.model.ProdutoCor>> mapaCores,
                                                  String grupoFiltro, String termoBusca) throws Exception {
        gerarRelatorioLevantamentoEstoque(arquivoDestino, produtos, mapaCores, Collections.emptyMap(), grupoFiltro, "Todas as Subcategorias", termoBusca);
    }

    /**
     * Gera o relatório PDF de Levantamento de Estoque e Ficha de Pedido.
     * Colunas: CÓDIGO DA LOJA, NOME DO PRODUTO, SUBCATEGORIA, CORES, QUANTIDADE EM ESTOQUE, PREÇO À VISTA, PEDIDO (CANETA).
     */
    public void gerarRelatorioLevantamentoEstoque(File arquivoDestino, List<com.swstock.model.Produto> produtos,
                                                  Map<Integer, List<com.swstock.model.ProdutoCor>> mapaCores,
                                                  Map<Integer, List<com.swstock.model.ProdutoSubcategoria>> mapaSubcategorias,
                                                  String grupoFiltro, String subcategoriaFiltro, String termoBusca) throws Exception {
        Document document = new Document(PageSize.A4, 24, 24, 28, 28);

        try (FileOutputStream out = new FileOutputStream(arquivoDestino)) {
            PdfWriter writer = PdfWriter.getInstance(document, out);

            // Rodapé com numeração de páginas
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter writer, Document doc) {
                    PdfContentByte cb = writer.getDirectContent();
                    ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                            new Phrase(String.format("SWStock - Levantamento de Estoque & Ficha de Pedido | Página %d", writer.getPageNumber()), FONT_FOOTER),
                            (doc.right() - doc.left()) / 2 + doc.leftMargin(), doc.bottom() - 14, 0);
                }
            });

            document.open();

            // 1. Cabeçalho
            Paragraph pTitulo = new Paragraph("SWStock - Levantamento de Estoque & Ficha de Pedido", FONT_TITLE);
            pTitulo.setSpacingAfter(3);
            document.add(pTitulo);

            String dataHora = LocalDateTime.now().format(DATE_TIME_FORMATTER);
            String grupoTxt = (grupoFiltro != null && !grupoFiltro.trim().isEmpty() && !grupoFiltro.toUpperCase().contains("TODOS") && !grupoFiltro.toUpperCase().contains("TODAS"))
                    ? grupoFiltro.toUpperCase() : "TODOS OS GRUPOS / FABRICANTES";

            boolean filtrarSub = subcategoriaFiltro != null && !subcategoriaFiltro.trim().isEmpty()
                    && !subcategoriaFiltro.toUpperCase().contains("TODAS") && !subcategoriaFiltro.toUpperCase().contains("TODOS");
            String subUpper = filtrarSub ? subcategoriaFiltro.trim().toUpperCase() : "";

            int totalQtdGeral = produtos.stream().mapToInt(p -> p.getQuantidade() != null ? p.getQuantidade() : 0).sum();

            StringBuilder sub = new StringBuilder();
            sub.append("Emissão: ").append(dataHora).append("  |  Grupo: ").append(grupoTxt);
            if (filtrarSub) {
                sub.append("  |  Subcategoria: ").append(subcategoriaFiltro.trim().toUpperCase());
            }
            if (termoBusca != null && !termoBusca.trim().isEmpty()) {
                sub.append("  |  Busca: '").append(termoBusca.trim()).append("'");
            }
            sub.append("\nTotal de Itens: ").append(produtos.size()).append(" produtos  |  Estoque Total: ").append(totalQtdGeral).append(" un.");

            Paragraph pSub = new Paragraph(sub.toString(), FONT_SUBTITLE);
            pSub.setSpacingAfter(10);
            document.add(pSub);

            if (produtos.isEmpty()) {
                Paragraph pVazio = new Paragraph("\nNenhum produto encontrado para o levantamento com os filtros selecionados.", FONT_CELL);
                pVazio.setAlignment(Element.ALIGN_CENTER);
                document.add(pVazio);
            } else {
                // Agrupa produtos por Fabricante / Grupo para separação organizada
                Map<String, List<com.swstock.model.Produto>> agrupadoPorGrupo = new LinkedHashMap<>();
                for (com.swstock.model.Produto p : produtos) {
                    String g = p.getGrupo() != null && !p.getGrupo().trim().isEmpty() ? p.getGrupo().toUpperCase() : "GERAL";
                    agrupadoPorGrupo.computeIfAbsent(g, k -> new ArrayList<>()).add(p);
                }

                for (Map.Entry<String, List<com.swstock.model.Produto>> entry : agrupadoPorGrupo.entrySet()) {
                    String nomeGrupo = entry.getKey();
                    List<com.swstock.model.Produto> listaGrupo = entry.getValue();

                    Paragraph pGrupo = new Paragraph("Fabricante / Grupo: " + nomeGrupo + " (" + listaGrupo.size() + " itens)", FONT_SECTION_DATE);
                    pGrupo.setSpacingBefore(8);
                    pGrupo.setSpacingAfter(4);
                    document.add(pGrupo);

                    // Tabela com 7 colunas:
                    // 1. Código (SKU) | 2. Nome do Produto | 3. Quantidade (Estoque) | 4. Subcategoria | 5. Cores | 6. Preço à Vista | 7. Pedido (Caneta)
                    PdfPTable table = new PdfPTable(7);
                    table.setWidthPercentage(100);
                    table.setWidths(new float[]{10f, 30f, 10f, 14f, 14f, 10f, 12f});
                    table.setSpacingAfter(8);

                    adicionarCabecalhoColuna(table, "CÓDIGO", Element.ALIGN_CENTER);
                    adicionarCabecalhoColuna(table, "NOME DO PRODUTO", Element.ALIGN_LEFT);
                    adicionarCabecalhoColuna(table, "QUANTIDADE", Element.ALIGN_CENTER);
                    adicionarCabecalhoColuna(table, "SUBCATEGORIA", Element.ALIGN_CENTER);
                    adicionarCabecalhoColuna(table, "COR", Element.ALIGN_CENTER);
                    adicionarCabecalhoColuna(table, "PREÇO À VISTA", Element.ALIGN_RIGHT);
                    adicionarCabecalhoColuna(table, "PEDIDO", Element.ALIGN_CENTER);

                    int subtotalQtdGrupo = 0;
                    int totalLinhasGrupo = 0;
                    int linhaIndex = 0;

                    for (int i = 0; i < listaGrupo.size(); i++) {
                        com.swstock.model.Produto p = listaGrupo.get(i);
                        double precoV = p.getPrecoVista() != null ? p.getPrecoVista() : 0.0;
                        String subProd = (p.getSubcategoria() != null && !p.getSubcategoria().trim().isEmpty()) ? p.getSubcategoria().trim() : "-";
                        List<com.swstock.model.ProdutoSubcategoria> subs = (mapaSubcategorias != null && p.getId() != null) ? mapaSubcategorias.get(p.getId()) : null;
                        List<com.swstock.model.ProdutoCor> cores = (mapaCores != null && p.getId() != null) ? mapaCores.get(p.getId()) : null;

                        if (subs != null && !subs.isEmpty()) {
                            // Produto com variações de subcategoria
                            for (com.swstock.model.ProdutoSubcategoria s : subs) {
                                String nomeSub = s.getNomeSubcategoria() != null ? s.getNomeSubcategoria() : "-";
                                if (filtrarSub && !nomeSub.equalsIgnoreCase(subUpper)) {
                                    continue;
                                }
                                Color bg = (linhaIndex % 2 == 0) ? Color.WHITE : new Color(248, 250, 252);
                                linhaIndex++;
                                totalLinhasGrupo++;

                                int qtdSub = s.getQuantidade();
                                subtotalQtdGrupo += qtdSub;

                                // 1. Código
                                PdfPCell cellCod = new PdfPCell(new Phrase(p.getCodigoLoja() != null ? p.getCodigoLoja() : "-", FONT_CELL_BOLD));
                                cellCod.setHorizontalAlignment(Element.ALIGN_CENTER);
                                cellCod.setBackgroundColor(bg);
                                cellCod.setPadding(4);
                                cellCod.setBorderColor(new Color(226, 232, 240));
                                table.addCell(cellCod);

                                // 2. Nome
                                PdfPCell cellNome = new PdfPCell(new Phrase(p.getNome() != null ? p.getNome() : "-", FONT_CELL));
                                cellNome.setHorizontalAlignment(Element.ALIGN_LEFT);
                                cellNome.setBackgroundColor(bg);
                                cellNome.setPadding(4);
                                cellNome.setBorderColor(new Color(226, 232, 240));
                                table.addCell(cellNome);

                                // 3. Quantidade (Estoque)
                                Font fontEstoque = qtdSub > 0 ? FONT_CELL_GREEN : FONT_CELL_RED;
                                PdfPCell cellEstoque = new PdfPCell(new Phrase(qtdSub + " un.", fontEstoque));
                                cellEstoque.setHorizontalAlignment(Element.ALIGN_CENTER);
                                cellEstoque.setBackgroundColor(bg);
                                cellEstoque.setPadding(4);
                                cellEstoque.setBorderColor(new Color(226, 232, 240));
                                table.addCell(cellEstoque);

                                // 4. Subcategoria
                                PdfPCell cellSubcat = new PdfPCell(new Phrase(nomeSub, FONT_CELL_BOLD));
                                cellSubcat.setHorizontalAlignment(Element.ALIGN_CENTER);
                                cellSubcat.setBackgroundColor(bg);
                                cellSubcat.setPadding(4);
                                cellSubcat.setBorderColor(new Color(226, 232, 240));
                                table.addCell(cellSubcat);

                                // 5. Cor
                                PdfPCell cellCor = new PdfPCell(new Phrase("PADRÃO", FONT_CELL));
                                cellCor.setHorizontalAlignment(Element.ALIGN_CENTER);
                                cellCor.setBackgroundColor(bg);
                                cellCor.setPadding(4);
                                cellCor.setBorderColor(new Color(226, 232, 240));
                                table.addCell(cellCor);

                                // 6. Preço à Vista
                                PdfPCell cellPreco = new PdfPCell(new Phrase(String.format(Locale.of("pt", "BR"), "R$ %.2f", precoV), FONT_CELL_BOLD));
                                cellPreco.setHorizontalAlignment(Element.ALIGN_RIGHT);
                                cellPreco.setBackgroundColor(bg);
                                cellPreco.setPadding(4);
                                cellPreco.setBorderColor(new Color(226, 232, 240));
                                table.addCell(cellPreco);

                                // 7. Pedido (Espaço para caneta)
                                PdfPCell cellPedido = new PdfPCell(new Phrase(" ", FONT_CELL));
                                cellPedido.setHorizontalAlignment(Element.ALIGN_CENTER);
                                cellPedido.setBackgroundColor(Color.WHITE);
                                cellPedido.setPadding(4);
                                cellPedido.setMinimumHeight(18f);
                                cellPedido.setBorderColor(new Color(148, 163, 184));
                                cellPedido.setBorderWidth(1f);
                                table.addCell(cellPedido);
                            }
                        } else if (cores != null && !cores.isEmpty()) {
                            // Produto com variações de cor
                            if (filtrarSub && !subProd.equalsIgnoreCase(subUpper)) {
                                continue;
                            }
                            for (com.swstock.model.ProdutoCor cor : cores) {
                                Color bg = (linhaIndex % 2 == 0) ? Color.WHITE : new Color(248, 250, 252);
                                linhaIndex++;
                                totalLinhasGrupo++;

                                int qtdCor = cor.getQuantidade();
                                subtotalQtdGrupo += qtdCor;

                                // 1. Código
                                PdfPCell cellCod = new PdfPCell(new Phrase(p.getCodigoLoja() != null ? p.getCodigoLoja() : "-", FONT_CELL_BOLD));
                                cellCod.setHorizontalAlignment(Element.ALIGN_CENTER);
                                cellCod.setBackgroundColor(bg);
                                cellCod.setPadding(4);
                                cellCod.setBorderColor(new Color(226, 232, 240));
                                table.addCell(cellCod);

                                // 2. Nome
                                PdfPCell cellNome = new PdfPCell(new Phrase(p.getNome() != null ? p.getNome() : "-", FONT_CELL));
                                cellNome.setHorizontalAlignment(Element.ALIGN_LEFT);
                                cellNome.setBackgroundColor(bg);
                                cellNome.setPadding(4);
                                cellNome.setBorderColor(new Color(226, 232, 240));
                                table.addCell(cellNome);

                                // 3. Quantidade (Estoque)
                                Font fontEstoque = qtdCor > 0 ? FONT_CELL_GREEN : FONT_CELL_RED;
                                PdfPCell cellEstoque = new PdfPCell(new Phrase(qtdCor + " un.", fontEstoque));
                                cellEstoque.setHorizontalAlignment(Element.ALIGN_CENTER);
                                cellEstoque.setBackgroundColor(bg);
                                cellEstoque.setPadding(4);
                                cellEstoque.setBorderColor(new Color(226, 232, 240));
                                table.addCell(cellEstoque);

                                // 4. Subcategoria
                                PdfPCell cellSubcat = new PdfPCell(new Phrase(subProd, FONT_CELL));
                                cellSubcat.setHorizontalAlignment(Element.ALIGN_CENTER);
                                cellSubcat.setBackgroundColor(bg);
                                cellSubcat.setPadding(4);
                                cellSubcat.setBorderColor(new Color(226, 232, 240));
                                table.addCell(cellSubcat);

                                // 5. Cor
                                PdfPCell cellCor = new PdfPCell(new Phrase(cor.getNomeCor(), FONT_CELL_BOLD));
                                cellCor.setHorizontalAlignment(Element.ALIGN_CENTER);
                                cellCor.setBackgroundColor(bg);
                                cellCor.setPadding(4);
                                cellCor.setBorderColor(new Color(226, 232, 240));
                                table.addCell(cellCor);

                                // 6. Preço à Vista
                                PdfPCell cellPreco = new PdfPCell(new Phrase(String.format(Locale.of("pt", "BR"), "R$ %.2f", precoV), FONT_CELL_BOLD));
                                cellPreco.setHorizontalAlignment(Element.ALIGN_RIGHT);
                                cellPreco.setBackgroundColor(bg);
                                cellPreco.setPadding(4);
                                cellPreco.setBorderColor(new Color(226, 232, 240));
                                table.addCell(cellPreco);

                                // 7. Pedido (Espaço para caneta)
                                PdfPCell cellPedido = new PdfPCell(new Phrase(" ", FONT_CELL));
                                cellPedido.setHorizontalAlignment(Element.ALIGN_CENTER);
                                cellPedido.setBackgroundColor(Color.WHITE);
                                cellPedido.setPadding(4);
                                cellPedido.setMinimumHeight(18f);
                                cellPedido.setBorderColor(new Color(148, 163, 184));
                                cellPedido.setBorderWidth(1f);
                                table.addCell(cellPedido);
                            }
                        } else {
                            // Produto sem variações de cor nem subcategoria (ÚNICA)
                            if (filtrarSub && !subProd.equalsIgnoreCase(subUpper)) {
                                continue;
                            }
                            Color bg = (linhaIndex % 2 == 0) ? Color.WHITE : new Color(248, 250, 252);
                            linhaIndex++;
                            totalLinhasGrupo++;

                            int qtd = p.getQuantidade() != null ? p.getQuantidade() : 0;
                            subtotalQtdGrupo += qtd;

                            // 1. Código
                            PdfPCell cellCod = new PdfPCell(new Phrase(p.getCodigoLoja() != null ? p.getCodigoLoja() : "-", FONT_CELL_BOLD));
                            cellCod.setHorizontalAlignment(Element.ALIGN_CENTER);
                            cellCod.setBackgroundColor(bg);
                            cellCod.setPadding(4);
                            cellCod.setBorderColor(new Color(226, 232, 240));
                            table.addCell(cellCod);

                            // 2. Nome
                            PdfPCell cellNome = new PdfPCell(new Phrase(p.getNome() != null ? p.getNome() : "-", FONT_CELL));
                            cellNome.setHorizontalAlignment(Element.ALIGN_LEFT);
                            cellNome.setBackgroundColor(bg);
                            cellNome.setPadding(4);
                            cellNome.setBorderColor(new Color(226, 232, 240));
                            table.addCell(cellNome);

                            // 3. Quantidade (Estoque)
                            Font fontEstoque = qtd > 0 ? FONT_CELL_GREEN : FONT_CELL_RED;
                            PdfPCell cellEstoque = new PdfPCell(new Phrase(qtd + " un.", fontEstoque));
                            cellEstoque.setHorizontalAlignment(Element.ALIGN_CENTER);
                            cellEstoque.setBackgroundColor(bg);
                            cellEstoque.setPadding(4);
                            cellEstoque.setBorderColor(new Color(226, 232, 240));
                            table.addCell(cellEstoque);

                            // 4. Subcategoria
                            PdfPCell cellSubcat = new PdfPCell(new Phrase(subProd, FONT_CELL));
                            cellSubcat.setHorizontalAlignment(Element.ALIGN_CENTER);
                            cellSubcat.setBackgroundColor(bg);
                            cellSubcat.setPadding(4);
                            cellSubcat.setBorderColor(new Color(226, 232, 240));
                            table.addCell(cellSubcat);

                            // 5. Cor
                            PdfPCell cellCor = new PdfPCell(new Phrase("ÚNICA", FONT_CELL));
                            cellCor.setHorizontalAlignment(Element.ALIGN_CENTER);
                            cellCor.setBackgroundColor(bg);
                            cellCor.setPadding(4);
                            cellCor.setBorderColor(new Color(226, 232, 240));
                            table.addCell(cellCor);

                            // 6. Preço à Vista
                            PdfPCell cellPreco = new PdfPCell(new Phrase(String.format(Locale.of("pt", "BR"), "R$ %.2f", precoV), FONT_CELL_BOLD));
                            cellPreco.setHorizontalAlignment(Element.ALIGN_RIGHT);
                            cellPreco.setBackgroundColor(bg);
                            cellPreco.setPadding(4);
                            cellPreco.setBorderColor(new Color(226, 232, 240));
                            table.addCell(cellPreco);

                            // 7. Pedido (Espaço para caneta)
                            PdfPCell cellPedido = new PdfPCell(new Phrase(" ", FONT_CELL));
                            cellPedido.setHorizontalAlignment(Element.ALIGN_CENTER);
                            cellPedido.setBackgroundColor(Color.WHITE);
                            cellPedido.setPadding(4);
                            cellPedido.setMinimumHeight(18f);
                            cellPedido.setBorderColor(new Color(148, 163, 184));
                            cellPedido.setBorderWidth(1f);
                            table.addCell(cellPedido);
                        }
                    }

                    // Subtotal do Grupo (Apenas quantitativo)
                    PdfPCell cellSubLabel = new PdfPCell(new Phrase("Subtotal (" + nomeGrupo + "): " + totalLinhasGrupo + " itens/variações", FONT_CELL_BOLD));
                    cellSubLabel.setColspan(4);
                    cellSubLabel.setBackgroundColor(new Color(241, 245, 249));
                    cellSubLabel.setPadding(5);
                    cellSubLabel.setBorderColor(new Color(203, 213, 225));
                    table.addCell(cellSubLabel);

                    PdfPCell cellSubEstoque = new PdfPCell(new Phrase(subtotalQtdGrupo + " un.", FONT_CELL_BOLD));
                    cellSubEstoque.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cellSubEstoque.setBackgroundColor(new Color(241, 245, 249));
                    cellSubEstoque.setPadding(5);
                    cellSubEstoque.setBorderColor(new Color(203, 213, 225));
                    table.addCell(cellSubEstoque);

                    PdfPCell cellSubPreco = new PdfPCell(new Phrase("-", FONT_CELL));
                    cellSubPreco.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cellSubPreco.setBackgroundColor(new Color(241, 245, 249));
                    cellSubPreco.setPadding(5);
                    cellSubPreco.setBorderColor(new Color(203, 213, 225));
                    table.addCell(cellSubPreco);

                    PdfPCell cellSubVazio = new PdfPCell(new Phrase(" ", FONT_CELL));
                    cellSubVazio.setBackgroundColor(new Color(241, 245, 249));
                    cellSubVazio.setPadding(5);
                    cellSubVazio.setBorderColor(new Color(203, 213, 225));
                    table.addCell(cellSubVazio);

                    document.add(table);
                }

                // Bloco de Assinatura e Conferência
                Paragraph pAssinatura = new Paragraph("\n\nConferido por: __________________________________________________   Data: ____/____/________   Visto: _____________", FONT_CELL_BOLD);
                pAssinatura.setSpacingBefore(12);
                document.add(pAssinatura);
            }

            document.close();
            LOGGER.info("Relatório PDF de Levantamento de Estoque gerado com sucesso: " + arquivoDestino.getAbsolutePath());
        }
    }

    /**
     * Gera o relatório PDF de Levantamento de Bases e Ficha de Pedido.
     * Colunas: TAMANHO, NOME DA BASE, QUANTIDADE, TIPO, FABRICANTE, VALOR DE COMPRA, PEDIDO.
     */
    public void gerarRelatorioLevantamentoBases(File arquivoDestino, List<com.swstock.model.Base> bases,
                                               String tipoFiltro, String fabricanteFiltro,
                                               String tamanhoFiltro, String termoBusca) throws Exception {
        Document document = new Document(PageSize.A4, 24, 24, 28, 28);

        try (FileOutputStream out = new FileOutputStream(arquivoDestino)) {
            PdfWriter writer = PdfWriter.getInstance(document, out);

            // Rodapé com numeração de páginas
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter writer, Document doc) {
                    PdfContentByte cb = writer.getDirectContent();
                    ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                            new Phrase(String.format("SWStock - Levantamento de Bases & Ficha de Pedido | Página %d", writer.getPageNumber()), FONT_FOOTER),
                            (doc.right() - doc.left()) / 2 + doc.leftMargin(), doc.bottom() - 14, 0);
                }
            });

            document.open();

            // 1. Cabeçalho
            Paragraph pTitulo = new Paragraph("SWStock - Levantamento de Bases & Ficha de Pedido", FONT_TITLE);
            pTitulo.setSpacingAfter(3);
            document.add(pTitulo);

            String dataHora = LocalDateTime.now().format(DATE_TIME_FORMATTER);
            String tipoTxt = (tipoFiltro != null && !tipoFiltro.trim().isEmpty() && !tipoFiltro.toUpperCase().contains("TODOS") && !tipoFiltro.toUpperCase().contains("TODAS"))
                    ? tipoFiltro.toUpperCase() : "TODOS OS TIPOS (PREDIAL E AUTOMOTIVA)";
            String fabTxt = (fabricanteFiltro != null && !fabricanteFiltro.trim().isEmpty() && !fabricanteFiltro.toUpperCase().contains("TODOS") && !fabricanteFiltro.toUpperCase().contains("TODAS"))
                    ? fabricanteFiltro.toUpperCase() : "TODOS OS FABRICANTES";
            String tamTxt = (tamanhoFiltro != null && !tamanhoFiltro.trim().isEmpty() && !tamanhoFiltro.toUpperCase().contains("TODOS") && !tamanhoFiltro.toUpperCase().contains("TODAS"))
                    ? tamanhoFiltro.toUpperCase() : "TODOS OS TAMANHOS";

            int totalQtdGeral = bases.stream().mapToInt(b -> b.getQuantidade() != null ? b.getQuantidade() : 0).sum();
            double totalValorCompra = bases.stream().mapToDouble(b -> (b.getQuantidade() != null ? b.getQuantidade() : 0) * (b.getValorCompra() != null ? b.getValorCompra() : 0.0)).sum();

            StringBuilder sub = new StringBuilder();
            sub.append("Emissão: ").append(dataHora).append("  |  Tipo: ").append(tipoTxt);
            sub.append("  |  Fabricante: ").append(fabTxt);
            sub.append("  |  Tamanho: ").append(tamTxt);
            if (termoBusca != null && !termoBusca.trim().isEmpty()) {
                sub.append("  |  Busca: '").append(termoBusca.trim()).append("'");
            }
            sub.append("\nTotal de Itens: ").append(bases.size()).append(" bases  |  Estoque Total: ").append(totalQtdGeral)
               .append(" un.  |  Valor Total de Compra: ").append(String.format(Locale.of("pt", "BR"), "R$ %.2f", totalValorCompra));

            Paragraph pSub = new Paragraph(sub.toString(), FONT_SUBTITLE);
            pSub.setSpacingAfter(10);
            document.add(pSub);

            if (bases.isEmpty()) {
                Paragraph pVazio = new Paragraph("\nNenhuma base encontrada para o levantamento com os filtros selecionados.", FONT_CELL);
                pVazio.setAlignment(Element.ALIGN_CENTER);
                document.add(pVazio);
            } else {
                // Agrupa bases por Fabricante / Marca para separação organizada
                Map<String, List<com.swstock.model.Base>> agrupadoPorFabricante = new LinkedHashMap<>();
                for (com.swstock.model.Base b : bases) {
                    String f = b.getFabricante() != null && !b.getFabricante().trim().isEmpty() ? b.getFabricante().toUpperCase() : "GERAL";
                    agrupadoPorFabricante.computeIfAbsent(f, k -> new ArrayList<>()).add(b);
                }

                for (Map.Entry<String, List<com.swstock.model.Base>> entry : agrupadoPorFabricante.entrySet()) {
                    String nomeFab = entry.getKey();
                    List<com.swstock.model.Base> listaFab = entry.getValue();

                    Paragraph pFab = new Paragraph("Fabricante / Marca: " + nomeFab + " (" + listaFab.size() + " bases)", FONT_SECTION_DATE);
                    pFab.setSpacingBefore(8);
                    pFab.setSpacingAfter(4);
                    document.add(pFab);

                    // Tabela com 7 colunas:
                    // 1. Tamanho | 2. Nome da Base | 3. Quantidade (Estoque) | 4. Tipo | 5. Fabricante | 6. Valor de Compra | 7. Pedido (Caneta)
                    PdfPTable table = new PdfPTable(7);
                    table.setWidthPercentage(100);
                    table.setWidths(new float[]{12f, 32f, 10f, 13f, 15f, 12f, 14f});
                    table.setSpacingAfter(8);

                    adicionarCabecalhoColuna(table, "TAMANHO", Element.ALIGN_CENTER);
                    adicionarCabecalhoColuna(table, "NOME DA BASE", Element.ALIGN_LEFT);
                    adicionarCabecalhoColuna(table, "QUANTIDADE", Element.ALIGN_CENTER);
                    adicionarCabecalhoColuna(table, "TIPO", Element.ALIGN_CENTER);
                    adicionarCabecalhoColuna(table, "FABRICANTE", Element.ALIGN_LEFT);
                    adicionarCabecalhoColuna(table, "VALOR COMPRA", Element.ALIGN_RIGHT);
                    adicionarCabecalhoColuna(table, "PEDIDO", Element.ALIGN_CENTER);

                    int subtotalQtdFab = 0;
                    double subtotalValorFab = 0.0;
                    int linhaIndex = 0;

                    for (com.swstock.model.Base b : listaFab) {
                        Color bg = (linhaIndex % 2 == 0) ? Color.WHITE : new Color(248, 250, 252);
                        linhaIndex++;

                        int qtd = b.getQuantidade() != null ? b.getQuantidade() : 0;
                        double vlrCompra = b.getValorCompra() != null ? b.getValorCompra() : 0.0;
                        subtotalQtdFab += qtd;
                        subtotalValorFab += (qtd * vlrCompra);

                        // 1. Tamanho
                        PdfPCell cellTam = new PdfPCell(new Phrase(b.getTamanho() != null ? b.getTamanho() : "-", FONT_CELL_BOLD));
                        cellTam.setHorizontalAlignment(Element.ALIGN_CENTER);
                        cellTam.setBackgroundColor(bg);
                        cellTam.setPadding(4);
                        cellTam.setBorderColor(new Color(226, 232, 240));
                        table.addCell(cellTam);

                        // 2. Nome da Base
                        PdfPCell cellNome = new PdfPCell(new Phrase(b.getNome() != null ? b.getNome() : "-", FONT_CELL));
                        cellNome.setHorizontalAlignment(Element.ALIGN_LEFT);
                        cellNome.setBackgroundColor(bg);
                        cellNome.setPadding(4);
                        cellNome.setBorderColor(new Color(226, 232, 240));
                        table.addCell(cellNome);

                        // 3. Quantidade (Estoque)
                        Font fontEstoque = qtd > 0 ? FONT_CELL_GREEN : FONT_CELL_RED;
                        PdfPCell cellEstoque = new PdfPCell(new Phrase(qtd + " un.", fontEstoque));
                        cellEstoque.setHorizontalAlignment(Element.ALIGN_CENTER);
                        cellEstoque.setBackgroundColor(bg);
                        cellEstoque.setPadding(4);
                        cellEstoque.setBorderColor(new Color(226, 232, 240));
                        table.addCell(cellEstoque);

                        // 4. Tipo
                        PdfPCell cellTipo = new PdfPCell(new Phrase(b.getTipoFormatado() != null ? b.getTipoFormatado() : "-", FONT_CELL));
                        cellTipo.setHorizontalAlignment(Element.ALIGN_CENTER);
                        cellTipo.setBackgroundColor(bg);
                        cellTipo.setPadding(4);
                        cellTipo.setBorderColor(new Color(226, 232, 240));
                        table.addCell(cellTipo);

                        // 5. Fabricante
                        PdfPCell cellFab = new PdfPCell(new Phrase(b.getFabricante() != null ? b.getFabricante() : "-", FONT_CELL));
                        cellFab.setHorizontalAlignment(Element.ALIGN_LEFT);
                        cellFab.setBackgroundColor(bg);
                        cellFab.setPadding(4);
                        cellFab.setBorderColor(new Color(226, 232, 240));
                        table.addCell(cellFab);

                        // 6. Valor Compra
                        PdfPCell cellPreco = new PdfPCell(new Phrase(String.format(Locale.of("pt", "BR"), "R$ %.2f", vlrCompra), FONT_CELL));
                        cellPreco.setHorizontalAlignment(Element.ALIGN_RIGHT);
                        cellPreco.setBackgroundColor(bg);
                        cellPreco.setPadding(4);
                        cellPreco.setBorderColor(new Color(226, 232, 240));
                        table.addCell(cellPreco);

                        // 7. Pedido (Espaço para caneta)
                        PdfPCell cellPedido = new PdfPCell(new Phrase(" ", FONT_CELL));
                        cellPedido.setHorizontalAlignment(Element.ALIGN_CENTER);
                        cellPedido.setBackgroundColor(Color.WHITE);
                        cellPedido.setPadding(4);
                        cellPedido.setMinimumHeight(18f);
                        cellPedido.setBorderColor(new Color(148, 163, 184));
                        cellPedido.setBorderWidth(1f);
                        table.addCell(cellPedido);
                    }

                    // Linha de Subtotal do Fabricante
                    PdfPCell cellSubLabel = new PdfPCell(new Phrase("Subtotal (" + nomeFab + "): " + listaFab.size() + " bases", FONT_CELL_BOLD));
                    cellSubLabel.setColspan(2);
                    cellSubLabel.setBackgroundColor(new Color(241, 245, 249));
                    cellSubLabel.setPadding(5);
                    cellSubLabel.setBorderColor(new Color(203, 213, 225));
                    table.addCell(cellSubLabel);

                    PdfPCell cellSubEstoque = new PdfPCell(new Phrase(subtotalQtdFab + " un.", FONT_CELL_BOLD));
                    cellSubEstoque.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cellSubEstoque.setBackgroundColor(new Color(241, 245, 249));
                    cellSubEstoque.setPadding(5);
                    cellSubEstoque.setBorderColor(new Color(203, 213, 225));
                    table.addCell(cellSubEstoque);

                    PdfPCell cellSubTipoFab = new PdfPCell(new Phrase("-", FONT_CELL));
                    cellSubTipoFab.setColspan(2);
                    cellSubTipoFab.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cellSubTipoFab.setBackgroundColor(new Color(241, 245, 249));
                    cellSubTipoFab.setPadding(5);
                    cellSubTipoFab.setBorderColor(new Color(203, 213, 225));
                    table.addCell(cellSubTipoFab);

                    PdfPCell cellSubTotalVlr = new PdfPCell(new Phrase(String.format(Locale.of("pt", "BR"), "R$ %.2f", subtotalValorFab), FONT_CELL_BOLD));
                    cellSubTotalVlr.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    cellSubTotalVlr.setBackgroundColor(new Color(241, 245, 249));
                    cellSubTotalVlr.setPadding(5);
                    cellSubTotalVlr.setBorderColor(new Color(203, 213, 225));
                    table.addCell(cellSubTotalVlr);

                    PdfPCell cellSubVazio = new PdfPCell(new Phrase(" ", FONT_CELL));
                    cellSubVazio.setBackgroundColor(new Color(241, 245, 249));
                    cellSubVazio.setPadding(5);
                    cellSubVazio.setBorderColor(new Color(203, 213, 225));
                    table.addCell(cellSubVazio);

                    document.add(table);
                }

                // Bloco de Assinatura e Conferência
                Paragraph pAssinatura = new Paragraph("\n\nConferido por: __________________________________________________   Data: ____/____/________   Visto: _____________", FONT_CELL_BOLD);
                pAssinatura.setSpacingBefore(12);
                document.add(pAssinatura);
            }

            document.close();
            LOGGER.info("Relatório PDF de Levantamento de Bases gerado com sucesso: " + arquivoDestino.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao gerar relatório PDF de levantamento de bases.", e);
            throw e;
        }
    }

    /**
     * Gera o relatório PDF das movimentações do Histórico Geral de Bases de Tintas (Automotivas & Prediais).
     */
    public void gerarRelatorioBases(File arquivoDestino, List<HistoricoBase> movimentos,
                                   LocalDate dataInicio, LocalDate dataFim,
                                   String tipoBaseFiltro, String tipoOperacaoFiltro, String termoBusca) throws Exception {
        Document document = new Document(PageSize.A4, 28, 28, 28, 28);

        try (FileOutputStream out = new FileOutputStream(arquivoDestino)) {
            PdfWriter writer = PdfWriter.getInstance(document, out);

            // Rodapé com numeração de páginas
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter writer, Document doc) {
                    PdfContentByte cb = writer.getDirectContent();
                    ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                            new Phrase(String.format("SWStock - Histórico Geral de Bases de Tintas | Página %d", writer.getPageNumber()), FONT_FOOTER),
                            (doc.right() - doc.left()) / 2 + doc.leftMargin(), doc.bottom() - 14, 0);
                }
            });

            document.open();

            // 1. Cabeçalho
            String titulo = "SWStock - Relatório de Histórico das Bases";
            if (tipoBaseFiltro != null && !tipoBaseFiltro.trim().isEmpty() && !tipoBaseFiltro.contains("Todas")) {
                titulo += " (" + tipoBaseFiltro.trim() + ")";
            }
            Paragraph pTitulo = new Paragraph(titulo, FONT_TITLE);
            document.add(pTitulo);

            StringBuilder sub = new StringBuilder();
            if (dataInicio != null && dataFim != null) {
                sub.append("Período: ").append(dataInicio.format(DATE_FORMATTER)).append(" até ").append(dataFim.format(DATE_FORMATTER));
            } else if (dataInicio != null) {
                sub.append("Data: ").append(dataInicio.format(DATE_FORMATTER));
            } else {
                sub.append("Período: Todo o Histórico");
            }

            if (tipoBaseFiltro != null && !tipoBaseFiltro.trim().isEmpty()) {
                sub.append(" | Tipo: ").append(tipoBaseFiltro);
            }
            if (tipoOperacaoFiltro != null && !tipoOperacaoFiltro.trim().isEmpty()) {
                sub.append(" | Operação: ").append(tipoOperacaoFiltro);
            }
            if (termoBusca != null && !termoBusca.trim().isEmpty()) {
                sub.append(" | Busca: '").append(termoBusca.trim()).append("'");
            }
            sub.append(" | Emitido em: ").append(LocalDateTime.now().format(DATE_TIME_FORMATTER));

            Paragraph pSub = new Paragraph(sub.toString(), FONT_SUBTITLE);
            pSub.setSpacingAfter(8);
            document.add(pSub);

            // Métricas
            int totalEntradas = 0;
            int totalSaidas = 0;
            for (HistoricoBase h : movimentos) {
                int delta = h.getQuantidadeAlterada() != null ? h.getQuantidadeAlterada() : 0;
                if (delta > 0) totalEntradas += delta;
                else if (delta < 0) totalSaidas += Math.abs(delta);
            }
            int balanco = totalEntradas - totalSaidas;

            PdfPTable tblResumo = new PdfPTable(4);
            tblResumo.setWidthPercentage(100);
            tblResumo.setWidths(new float[]{25f, 25f, 25f, 25f});
            tblResumo.setSpacingBefore(2);
            tblResumo.setSpacingAfter(10);

            tblResumo.addCell(criarCardResumo("REGISTROS", movimentos.size() + " mov.", new Color(241, 245, 249), new Color(15, 23, 42)));
            tblResumo.addCell(criarCardResumo("TOTAL ENTRADAS", "+" + totalEntradas + " un.", new Color(240, 253, 244), new Color(22, 101, 52)));
            tblResumo.addCell(criarCardResumo("TOTAL SAÍDAS", "-" + totalSaidas + " un.", new Color(254, 242, 242), new Color(185, 28, 28)));
            tblResumo.addCell(criarCardResumo("BALANÇO LÍQUIDO", (balanco >= 0 ? "+" : "") + balanco + " un.", new Color(239, 246, 255), new Color(29, 78, 216)));
            document.add(tblResumo);

            // Agrupamento por Dia
            Map<String, List<HistoricoBase>> agrupadoPorDia = agruparBasesPorData(movimentos);

            if (agrupadoPorDia.isEmpty()) {
                Paragraph pVazio = new Paragraph("\nNenhuma movimentação de base encontrada para os filtros selecionados.", FONT_CELL);
                pVazio.setAlignment(Element.ALIGN_CENTER);
                document.add(pVazio);
            } else {
                for (Map.Entry<String, List<HistoricoBase>> entry : agrupadoPorDia.entrySet()) {
                    String dataFormatada = entry.getKey();
                    List<HistoricoBase> listaDia = entry.getValue();

                    Paragraph pDia = new Paragraph("Data: " + dataFormatada + " (" + listaDia.size() + " movimentações)", FONT_SECTION_DATE);
                    pDia.setSpacingBefore(8);
                    pDia.setSpacingAfter(4);
                    document.add(pDia);

                    // Tabela: Movimento | Responsável | Tamanho & Nome da Base | Tipo | Saldo | Motivo
                    PdfPTable table = new PdfPTable(6);
                    table.setWidthPercentage(100);
                    table.setWidths(new float[]{11f, 15f, 32f, 12f, 14f, 16f});
                    table.setSpacingAfter(8);

                    adicionarCabecalhoColuna(table, "MOVIMENTO", Element.ALIGN_CENTER);
                    adicionarCabecalhoColuna(table, "RESPONSÁVEL", Element.ALIGN_LEFT);
                    adicionarCabecalhoColuna(table, "BASE DE TINTA (TAMANHO & NOME)", Element.ALIGN_LEFT);
                    adicionarCabecalhoColuna(table, "TIPO", Element.ALIGN_CENTER);
                    adicionarCabecalhoColuna(table, "SALDO", Element.ALIGN_CENTER);
                    adicionarCabecalhoColuna(table, "MOTIVO / ORIGEM", Element.ALIGN_LEFT);

                    int totalDiaEntradas = 0;
                    int totalDiaSaidas = 0;

                    for (int i = 0; i < listaDia.size(); i++) {
                        HistoricoBase h = listaDia.get(i);
                        Color bg = (i % 2 == 0) ? Color.WHITE : new Color(248, 250, 252);

                        int delta = h.getQuantidadeAlterada() != null ? h.getQuantidadeAlterada() : 0;
                        if (delta > 0) totalDiaEntradas += delta;
                        else if (delta < 0) totalDiaSaidas += Math.abs(delta);

                        String responsavel = h.getResponsavel();
                        String baseDesc = "[" + (h.getBaseTamanho() != null ? h.getBaseTamanho() : h.getBaseCodigo()) + "] " + h.getBaseNome();
                        String tipoBase = h.getBaseTipoFormatado();
                        String saldo = h.getSaldoFormatado();
                        String motivo = h.getMotivo();

                        Font movFont = delta > 0 ? FONT_CELL_GREEN : (delta < 0 ? FONT_CELL_RED : FONT_CELL_BOLD);
                        PdfPCell cellQtd = new PdfPCell(new Phrase(h.getMovimentoFormatado(), movFont));
                        cellQtd.setHorizontalAlignment(Element.ALIGN_CENTER);
                        cellQtd.setBackgroundColor(bg);
                        cellQtd.setPadding(4);
                        cellQtd.setBorderColor(new Color(226, 232, 240));
                        table.addCell(cellQtd);

                        PdfPCell cellResp = new PdfPCell(new Phrase(responsavel, FONT_CELL_BOLD));
                        cellResp.setHorizontalAlignment(Element.ALIGN_LEFT);
                        cellResp.setBackgroundColor(bg);
                        cellResp.setPadding(4);
                        cellResp.setBorderColor(new Color(226, 232, 240));
                        table.addCell(cellResp);

                        PdfPCell cellBase = new PdfPCell(new Phrase(baseDesc, FONT_CELL));
                        cellBase.setHorizontalAlignment(Element.ALIGN_LEFT);
                        cellBase.setBackgroundColor(bg);
                        cellBase.setPadding(4);
                        cellBase.setBorderColor(new Color(226, 232, 240));
                        table.addCell(cellBase);

                        PdfPCell cellTipo = new PdfPCell(new Phrase(tipoBase, FONT_CELL_BOLD));
                        cellTipo.setHorizontalAlignment(Element.ALIGN_CENTER);
                        cellTipo.setBackgroundColor(bg);
                        cellTipo.setPadding(4);
                        cellTipo.setBorderColor(new Color(226, 232, 240));
                        table.addCell(cellTipo);

                        PdfPCell cellSaldo = new PdfPCell(new Phrase(saldo, FONT_CELL));
                        cellSaldo.setHorizontalAlignment(Element.ALIGN_CENTER);
                        cellSaldo.setBackgroundColor(bg);
                        cellSaldo.setPadding(4);
                        cellSaldo.setBorderColor(new Color(226, 232, 240));
                        table.addCell(cellSaldo);

                        PdfPCell cellMotivo = new PdfPCell(new Phrase(motivo, FONT_CELL));
                        cellMotivo.setHorizontalAlignment(Element.ALIGN_LEFT);
                        cellMotivo.setBackgroundColor(bg);
                        cellMotivo.setPadding(4);
                        cellMotivo.setBorderColor(new Color(226, 232, 240));
                        table.addCell(cellMotivo);
                    }

                    // Subtotal
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
                    cellSubQtd.setColspan(5);
                    cellSubQtd.setBackgroundColor(new Color(241, 245, 249));
                    cellSubQtd.setPadding(5);
                    cellSubQtd.setBorderColor(new Color(203, 213, 225));
                    table.addCell(cellSubQtd);

                    document.add(table);
                }
            }

            document.close();
            LOGGER.info("Relatório PDF de histórico de bases gerado com sucesso: " + arquivoDestino.getAbsolutePath());
        }
    }

    private Map<String, List<HistoricoBase>> agruparBasesPorData(List<HistoricoBase> lista) {
        Map<String, List<HistoricoBase>> mapa = new LinkedHashMap<>();
        for (HistoricoBase h : lista) {
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

    /**
     * Modelo de item para o Relatório Financeiro de Valor de Estoque.
     */
    public static class ItemValorEstoque {
        private final String codigo;
        private final String nome;
        private final String grupo;
        private final String cor;
        private final Integer estoque;
        private final Double precoVista;
        private final Double subtotalVista;
        private final Double precoPrazo;
        private final Double subtotalPrazo;

        public ItemValorEstoque(String codigo, String nome, String grupo, String cor, Integer estoque, Double precoVista, Double subtotalVista, Double precoPrazo, Double subtotalPrazo) {
            this.codigo = codigo;
            this.nome = nome;
            this.grupo = grupo;
            this.cor = cor;
            this.estoque = estoque;
            this.precoVista = precoVista;
            this.subtotalVista = subtotalVista;
            this.precoPrazo = precoPrazo;
            this.subtotalPrazo = subtotalPrazo;
        }

        public String getCodigo() { return codigo; }
        public String getNome() { return nome; }
        public String getGrupo() { return grupo; }
        public String getCor() { return cor; }
        public Integer getEstoque() { return estoque; }
        public Double getPrecoVista() { return precoVista; }
        public Double getSubtotalVista() { return subtotalVista; }
        public Double getPrecoPrazo() { return precoPrazo; }
        public Double getSubtotalPrazo() { return subtotalPrazo; }
    }

    /**
     * Gera o relatório PDF de Avaliação Financeira / Valor de Estoque.
     */
    public void gerarRelatorioValorEstoque(File arquivoDestino, List<ItemValorEstoque> itens,
                                          String grupoFiltro, String termoBusca,
                                          double totalVista, double totalPrazo) throws Exception {
        Document document = new Document(PageSize.A4.rotate(), 24, 24, 28, 28);

        try (FileOutputStream out = new FileOutputStream(arquivoDestino)) {
            PdfWriter writer = PdfWriter.getInstance(document, out);

            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter writer, Document doc) {
                    PdfContentByte cb = writer.getDirectContent();
                    ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                            new Phrase(String.format("SWStock - Extrato de Avaliação de Valor de Estoque | Página %d", writer.getPageNumber()), FONT_FOOTER),
                            (doc.right() - doc.left()) / 2 + doc.leftMargin(), doc.bottom() - 14, 0);
                }
            });

            document.open();

            // Cabeçalho
            Paragraph pTitulo = new Paragraph("SWStock - Relatório de Avaliação Financeira de Estoque", FONT_TITLE);
            pTitulo.setSpacingAfter(3);
            document.add(pTitulo);

            String dataHora = LocalDateTime.now().format(DATE_TIME_FORMATTER);
            String grupoTxt = (grupoFiltro != null && !grupoFiltro.trim().isEmpty() && !grupoFiltro.toUpperCase().contains("TODOS") && !grupoFiltro.toUpperCase().contains("TODAS"))
                    ? grupoFiltro.toUpperCase() : "TODOS OS FABRICANTES / GRUPOS";

            int totalUnidades = itens.stream().mapToInt(i -> i.getEstoque() != null ? i.getEstoque() : 0).sum();

            StringBuilder sub = new StringBuilder();
            sub.append("Emissão: ").append(dataHora).append("  |  Filtro: ").append(grupoTxt);
            if (termoBusca != null && !termoBusca.trim().isEmpty()) {
                sub.append("  |  Busca: '").append(termoBusca.trim()).append("'");
            }
            Paragraph pSub = new Paragraph(sub.toString(), FONT_SUBTITLE);
            pSub.setSpacingAfter(8);
            document.add(pSub);

            // Resumo
            PdfPTable tblResumo = new PdfPTable(4);
            tblResumo.setWidthPercentage(100);
            tblResumo.setWidths(new float[]{25f, 25f, 25f, 25f});
            tblResumo.setSpacingBefore(2);
            tblResumo.setSpacingAfter(10);

            tblResumo.addCell(criarCardResumo("ITENS LISTADOS", itens.size() + " produtos", new Color(241, 245, 249), new Color(15, 23, 42)));
            tblResumo.addCell(criarCardResumo("TOTAL UNIDADES", totalUnidades + " un.", new Color(239, 246, 255), new Color(29, 78, 216)));
            tblResumo.addCell(criarCardResumo("VALOR TOTAL (À VISTA)", String.format(Locale.of("pt", "BR"), "R$ %,.2f", totalVista), new Color(240, 253, 244), new Color(22, 101, 52)));
            tblResumo.addCell(criarCardResumo("VALOR TOTAL (A PRAZO)", String.format(Locale.of("pt", "BR"), "R$ %,.2f", totalPrazo), new Color(254, 243, 199), new Color(180, 83, 9)));
            document.add(tblResumo);

            if (itens.isEmpty()) {
                Paragraph pVazio = new Paragraph("\nNenhum produto com estoque encontrado para os filtros selecionados.", FONT_CELL);
                pVazio.setAlignment(Element.ALIGN_CENTER);
                document.add(pVazio);
            } else {
                // Tabela em modo paisagem:
                // CÓDIGO (10%) | PRODUTO (28%) | QUANTIDADE (8%) | GRUPO (13%) | COR (11%) | UNIT. VISTA (10%) | TOTAL VISTA (10%) | UNIT. PRAZO (10%)
                PdfPTable table = new PdfPTable(8);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{10f, 28f, 8f, 13f, 11f, 10f, 10f, 10f});
                table.setSpacingAfter(8);

                adicionarCabecalhoColuna(table, "CÓDIGO", Element.ALIGN_CENTER);
                adicionarCabecalhoColuna(table, "NOME DO PRODUTO", Element.ALIGN_LEFT);
                adicionarCabecalhoColuna(table, "QUANTIDADE", Element.ALIGN_CENTER);
                adicionarCabecalhoColuna(table, "GRUPO", Element.ALIGN_LEFT);
                adicionarCabecalhoColuna(table, "COR", Element.ALIGN_CENTER);
                adicionarCabecalhoColuna(table, "UNIT. VISTA", Element.ALIGN_RIGHT);
                adicionarCabecalhoColuna(table, "SUBTOTAL VISTA", Element.ALIGN_RIGHT);
                adicionarCabecalhoColuna(table, "SUBTOTAL PRAZO", Element.ALIGN_RIGHT);

                for (int i = 0; i < itens.size(); i++) {
                    ItemValorEstoque it = itens.get(i);
                    Color bg = (i % 2 == 0) ? Color.WHITE : new Color(248, 250, 252);

                    PdfPCell c1 = new PdfPCell(new Phrase(it.getCodigo(), FONT_CELL_BOLD));
                    c1.setHorizontalAlignment(Element.ALIGN_CENTER);
                    c1.setBackgroundColor(bg); c1.setPadding(4); c1.setBorderColor(new Color(226, 232, 240));
                    table.addCell(c1);

                    PdfPCell c2 = new PdfPCell(new Phrase(it.getNome(), FONT_CELL));
                    c2.setHorizontalAlignment(Element.ALIGN_LEFT);
                    c2.setBackgroundColor(bg); c2.setPadding(4); c2.setBorderColor(new Color(226, 232, 240));
                    table.addCell(c2);

                    PdfPCell c5 = new PdfPCell(new Phrase(it.getEstoque() + " un.", it.getEstoque() > 0 ? FONT_CELL_GREEN : FONT_CELL_RED));
                    c5.setHorizontalAlignment(Element.ALIGN_CENTER);
                    c5.setBackgroundColor(bg); c5.setPadding(4); c5.setBorderColor(new Color(226, 232, 240));
                    table.addCell(c5);

                    PdfPCell c3 = new PdfPCell(new Phrase(it.getGrupo(), FONT_CELL_BOLD));
                    c3.setHorizontalAlignment(Element.ALIGN_LEFT);
                    c3.setBackgroundColor(bg); c3.setPadding(4); c3.setBorderColor(new Color(226, 232, 240));
                    table.addCell(c3);

                    PdfPCell c4 = new PdfPCell(new Phrase(it.getCor(), FONT_CELL));
                    c4.setHorizontalAlignment(Element.ALIGN_CENTER);
                    c4.setBackgroundColor(bg); c4.setPadding(4); c4.setBorderColor(new Color(226, 232, 240));
                    table.addCell(c4);

                    PdfPCell c6 = new PdfPCell(new Phrase(String.format(Locale.of("pt", "BR"), "R$ %.2f", it.getPrecoVista()), FONT_CELL));
                    c6.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    c6.setBackgroundColor(bg); c6.setPadding(4); c6.setBorderColor(new Color(226, 232, 240));
                    table.addCell(c6);

                    PdfPCell c7 = new PdfPCell(new Phrase(String.format(Locale.of("pt", "BR"), "R$ %.2f", it.getSubtotalVista()), FONT_CELL_BOLD));
                    c7.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    c7.setBackgroundColor(bg); c7.setPadding(4); c7.setBorderColor(new Color(226, 232, 240));
                    table.addCell(c7);

                    PdfPCell c8 = new PdfPCell(new Phrase(String.format(Locale.of("pt", "BR"), "R$ %.2f", it.getSubtotalPrazo()), FONT_CELL_BOLD));
                    c8.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    c8.setBackgroundColor(bg); c8.setPadding(4); c8.setBorderColor(new Color(226, 232, 240));
                    table.addCell(c8);
                }

                // Linha de Total Geral
                PdfPCell cTotLabel = new PdfPCell(new Phrase("TOTAL GERAL CONSOLIDADO (" + itens.size() + " itens):", FONT_CELL_BOLD));
                cTotLabel.setColspan(2);
                cTotLabel.setBackgroundColor(new Color(241, 245, 249));
                cTotLabel.setPadding(6);
                cTotLabel.setBorderColor(new Color(203, 213, 225));
                table.addCell(cTotLabel);

                PdfPCell cTotQtd = new PdfPCell(new Phrase(totalUnidades + " un.", FONT_CELL_BOLD));
                cTotQtd.setHorizontalAlignment(Element.ALIGN_CENTER);
                cTotQtd.setBackgroundColor(new Color(241, 245, 249));
                cTotQtd.setPadding(6);
                cTotQtd.setBorderColor(new Color(203, 213, 225));
                table.addCell(cTotQtd);

                PdfPCell cTotVazio = new PdfPCell(new Phrase("-", FONT_CELL));
                cTotVazio.setColspan(3);
                cTotVazio.setHorizontalAlignment(Element.ALIGN_CENTER);
                cTotVazio.setBackgroundColor(new Color(241, 245, 249));
                cTotVazio.setPadding(6);
                cTotVazio.setBorderColor(new Color(203, 213, 225));
                table.addCell(cTotVazio);

                PdfPCell cTotVista = new PdfPCell(new Phrase(String.format(Locale.of("pt", "BR"), "R$ %,.2f", totalVista), FONT_CELL_GREEN));
                cTotVista.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cTotVista.setBackgroundColor(new Color(241, 245, 249));
                cTotVista.setPadding(6);
                cTotVista.setBorderColor(new Color(203, 213, 225));
                table.addCell(cTotVista);

                PdfPCell cTotPrazo = new PdfPCell(new Phrase(String.format(Locale.of("pt", "BR"), "R$ %,.2f", totalPrazo), FONT_CELL_BOLD));
                cTotPrazo.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cTotPrazo.setBackgroundColor(new Color(241, 245, 249));
                cTotPrazo.setPadding(6);
                cTotPrazo.setBorderColor(new Color(203, 213, 225));
                table.addCell(cTotPrazo);

                document.add(table);
            }

            document.close();
            LOGGER.info("Relatório PDF de Avaliação de Estoque gerado com sucesso: " + arquivoDestino.getAbsolutePath());
        }
    }
}
