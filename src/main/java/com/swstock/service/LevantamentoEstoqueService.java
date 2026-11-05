package com.swstock.service;

import com.swstock.model.Produto;
import com.swstock.model.ProdutoCor;
import com.swstock.model.ProdutoSubcategoria;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Monta as linhas do Levantamento de Estoque (uma linha por variação de cor, por variação de
 * subcategoria ou por produto simples). É a ÚNICA fonte de linhas usada tanto pela tela quanto
 * pelo PDF exportado, garantindo que ambos exibam exatamente os mesmos itens.
 */
public final class LevantamentoEstoqueService {

    /** Filtro de estoque aplicado a cada linha do levantamento. */
    public enum FiltroEstoque {
        TODOS("Todos (≥ 0)"),
        MAIOR_QUE_ZERO("Maior que zero (> 0)"),
        IGUAL_A_ZERO("Igual a zero (= 0)");

        private final String rotulo;

        FiltroEstoque(String rotulo) {
            this.rotulo = rotulo;
        }

        public String getRotulo() {
            return rotulo;
        }

        public boolean aceita(int quantidade) {
            return switch (this) {
                case MAIOR_QUE_ZERO -> quantidade > 0;
                case IGUAL_A_ZERO -> quantidade == 0;
                default -> true;
            };
        }

        /** Converte o texto exibido no ComboBox (ou textos legados) para o filtro correspondente. */
        public static FiltroEstoque deRotulo(String texto) {
            if (texto == null) return TODOS;
            String t = texto.trim().toUpperCase();
            if (t.contains("≥") || t.startsWith("TODO") || t.startsWith("TODA")) return TODOS;
            if (t.contains(">") || t.contains("MAIOR") || t.contains("COM ESTOQUE") || t.contains("APENAS_ESTOQUE")) return MAIOR_QUE_ZERO;
            if (t.contains("= 0") || t.contains("IGUAL") || t.contains("ZERAD")) return IGUAL_A_ZERO;
            return TODOS;
        }

        @Override
        public String toString() {
            return rotulo;
        }
    }

    /** Linha individual do levantamento (tela e PDF). */
    public record Linha(Integer produtoId, String codigo, String nome, String grupo,
                        String subcategoria, String cor, int quantidade,
                        double precoVista, double precoPrazo) {
    }

    private LevantamentoEstoqueService() {
    }

    public static boolean isFiltroSubcategoriaAtivo(String subcategoriaFiltro) {
        return subcategoriaFiltro != null && !subcategoriaFiltro.trim().isEmpty()
                && !subcategoriaFiltro.toUpperCase().contains("TODAS")
                && !subcategoriaFiltro.toUpperCase().contains("TODOS");
    }

    /**
     * Gera as linhas do levantamento.
     * <ul>
     *   <li>Produtos com cores: uma linha por cor (se o produto pertence à subcategoria filtrada).</li>
     *   <li>Produtos com variações de subcategoria: uma linha por variação (apenas as que casam com o filtro),
     *       inclusive quando o produto também possui cores e inclusive quando a variação está zerada.</li>
     *   <li>Produtos sem variações: uma linha única.</li>
     * </ul>
     * O filtro de estoque é aplicado por linha.
     */
    public static List<Linha> montarLinhas(List<Produto> produtos,
                                           Map<Integer, List<ProdutoCor>> mapaCores,
                                           Map<Integer, List<ProdutoSubcategoria>> mapaSubcategorias,
                                           String subcategoriaFiltro,
                                           FiltroEstoque filtroEstoque) {
        List<Linha> linhas = new ArrayList<>();
        if (produtos == null) return linhas;

        FiltroEstoque filtro = filtroEstoque != null ? filtroEstoque : FiltroEstoque.TODOS;
        boolean filtrarSub = isFiltroSubcategoriaAtivo(subcategoriaFiltro);
        String subFiltro = filtrarSub ? subcategoriaFiltro.trim() : "";

        for (Produto p : produtos) {
            double pv = p.getPrecoVista() != null ? p.getPrecoVista() : 0.0;
            double pp = p.getPrecoPrazo() != null ? p.getPrecoPrazo() : 0.0;
            String cod = p.getCodigoLoja() != null && !p.getCodigoLoja().isEmpty() ? p.getCodigoLoja() : "-";
            String grp = p.getGrupo() != null && !p.getGrupo().trim().isEmpty() ? p.getGrupo() : "GERAL";
            String nome = p.getNome() != null ? p.getNome() : "-";
            String subProd = p.getSubcategoria() != null && !p.getSubcategoria().trim().isEmpty() ? p.getSubcategoria().trim() : "-";

            List<ProdutoSubcategoria> subs = lista(mapaSubcategorias, p.getId());
            List<ProdutoCor> cores = lista(mapaCores, p.getId());

            boolean variacaoCasaFiltro = subs.stream().anyMatch(s -> nomeSub(s).equalsIgnoreCase(subFiltro));
            boolean pertenceSub = !filtrarSub || subProd.equalsIgnoreCase(subFiltro) || variacaoCasaFiltro;

            // 1. Variações por cor
            if (!cores.isEmpty() && pertenceSub) {
                String rotuloSub = filtrarSub ? subFiltro.toUpperCase()
                        : (!"-".equals(subProd) ? subProd : (!subs.isEmpty() ? nomeSub(subs.get(0)) : "-"));
                for (ProdutoCor cor : cores) {
                    int qtd = cor.getQuantidade() != null ? cor.getQuantidade() : 0;
                    if (!filtro.aceita(qtd)) continue;
                    linhas.add(new Linha(p.getId(), cod, nome, grp, rotuloSub, cor.getNomeCor(), qtd, pv, pp));
                }
            }

            // 2. Variações por subcategoria (inclusive zeradas e inclusive quando o produto também tem cores)
            for (ProdutoSubcategoria s : subs) {
                String nomeSub = nomeSub(s);
                if (filtrarSub && !nomeSub.equalsIgnoreCase(subFiltro)) continue;
                int qtd = s.getQuantidade() != null ? s.getQuantidade() : 0;
                if (!filtro.aceita(qtd)) continue;
                linhas.add(new Linha(p.getId(), cod, nome, grp, nomeSub, "PADRÃO", qtd, pv, pp));
            }

            // 3. Produto simples (sem cores nem variações de subcategoria)
            if (cores.isEmpty() && subs.isEmpty() && pertenceSub) {
                int qtd = p.getQuantidade() != null ? p.getQuantidade() : 0;
                if (!filtro.aceita(qtd)) continue;
                linhas.add(new Linha(p.getId(), cod, nome, grp, subProd, "ÚNICA", qtd, pv, pp));
            }
        }
        return linhas;
    }

    private static <T> List<T> lista(Map<Integer, List<T>> mapa, Integer id) {
        if (mapa == null || id == null) return Collections.emptyList();
        List<T> l = mapa.get(id);
        return l != null ? l : Collections.emptyList();
    }

    private static String nomeSub(ProdutoSubcategoria s) {
        return s.getNomeSubcategoria() != null && !s.getNomeSubcategoria().trim().isEmpty()
                ? s.getNomeSubcategoria().trim() : "-";
    }
}
