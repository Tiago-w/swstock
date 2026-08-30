package com.swstock.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Representa um registro no histórico de movimentações (adições e subtrações) de estoque.
 */
public class HistoricoEstoque {

    private Integer id;
    private Integer produtoId;
    private String dataHora;
    private String tipo; // "ENTRADA", "SAIDA", "AJUSTE"
    private Integer quantidadeAlterada; // ex: +5, -2
    private Integer quantidadeAnterior;
    private Integer quantidadeNova;
    private String motivo; // ex: "Edição Rápida (+)", "Edição Rápida (-)", "Ajuste Manual"
    private String responsavel; // Nome do funcionário que realizou a alteração

    // Metadados do Produto para o Extrato Global Unificado
    private String produtoNome;
    private String produtoCodigo;
    private String produtoLocalizacao;
    private String produtoGrupo;

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public HistoricoEstoque() {
    }

    public HistoricoEstoque(Integer produtoId, String tipo, Integer quantidadeAlterada,
                            Integer quantidadeAnterior, Integer quantidadeNova, String motivo) {
        this(produtoId, tipo, quantidadeAlterada, quantidadeAnterior, quantidadeNova, motivo, "Não informado");
    }

    public HistoricoEstoque(Integer produtoId, String tipo, Integer quantidadeAlterada,
                            Integer quantidadeAnterior, Integer quantidadeNova, String motivo, String responsavel) {
        this.produtoId = produtoId;
        this.dataHora = LocalDateTime.now().format(FORMATTER);
        this.tipo = tipo;
        this.quantidadeAlterada = quantidadeAlterada;
        this.quantidadeAnterior = quantidadeAnterior;
        this.quantidadeNova = quantidadeNova;
        this.motivo = motivo;
        this.responsavel = responsavel;
    }

    public HistoricoEstoque(Integer id, Integer produtoId, String dataHora, String tipo,
                            Integer quantidadeAlterada, Integer quantidadeAnterior,
                            Integer quantidadeNova, String motivo, String responsavel) {
        this(id, produtoId, dataHora, tipo, quantidadeAlterada, quantidadeAnterior, quantidadeNova, motivo, responsavel, null, null, null, null);
    }

    public HistoricoEstoque(Integer id, Integer produtoId, String dataHora, String tipo,
                            Integer quantidadeAlterada, Integer quantidadeAnterior,
                            Integer quantidadeNova, String motivo, String responsavel,
                            String produtoNome, String produtoCodigo, String produtoLocalizacao) {
        this(id, produtoId, dataHora, tipo, quantidadeAlterada, quantidadeAnterior, quantidadeNova, motivo, responsavel, produtoNome, produtoCodigo, produtoLocalizacao, "GERAL");
    }

    public HistoricoEstoque(Integer id, Integer produtoId, String dataHora, String tipo,
                            Integer quantidadeAlterada, Integer quantidadeAnterior,
                            Integer quantidadeNova, String motivo, String responsavel,
                            String produtoNome, String produtoCodigo, String produtoLocalizacao,
                            String produtoGrupo) {
        this.id = id;
        this.produtoId = produtoId;
        this.dataHora = dataHora;
        this.tipo = tipo;
        this.quantidadeAlterada = quantidadeAlterada;
        this.quantidadeAnterior = quantidadeAnterior;
        this.quantidadeNova = quantidadeNova;
        this.motivo = motivo;
        this.responsavel = responsavel;
        this.produtoNome = produtoNome;
        this.produtoCodigo = produtoCodigo;
        this.produtoLocalizacao = produtoLocalizacao;
        this.produtoGrupo = produtoGrupo != null && !produtoGrupo.trim().isEmpty() ? produtoGrupo : "GERAL";
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getProdutoId() {
        return produtoId;
    }

    public void setProdutoId(Integer produtoId) {
        this.produtoId = produtoId;
    }

    public String getDataHora() {
        return dataHora;
    }

    public void setDataHora(String dataHora) {
        this.dataHora = dataHora;
    }

    public String getDataHoraFormatada() {
        if (dataHora == null || dataHora.trim().isEmpty()) return "";
        try {
            String limpo = dataHora.replace("T", " ");
            if (limpo.length() > 19) limpo = limpo.substring(0, 19);
            LocalDateTime dt = LocalDateTime.parse(limpo, FORMATTER);
            return dt.format(DISPLAY_FORMATTER);
        } catch (Exception e) {
            return dataHora;
        }
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Integer getQuantidadeAlterada() {
        return quantidadeAlterada;
    }

    public void setQuantidadeAlterada(Integer quantidadeAlterada) {
        this.quantidadeAlterada = quantidadeAlterada;
    }

    public String getMovimentoFormatado() {
        if (quantidadeAlterada == null) return "0 un.";
        if (quantidadeAlterada > 0) {
            return "+" + quantidadeAlterada + " un.";
        }
        return quantidadeAlterada + " un.";
    }

    public Integer getQuantidadeAnterior() {
        return quantidadeAnterior;
    }

    public void setQuantidadeAnterior(Integer quantidadeAnterior) {
        this.quantidadeAnterior = quantidadeAnterior;
    }

    public Integer getQuantidadeNova() {
        return quantidadeNova;
    }

    public void setQuantidadeNova(Integer quantidadeNova) {
        this.quantidadeNova = quantidadeNova;
    }

    public String getSaldoFormatado() {
        int ant = quantidadeAnterior != null ? quantidadeAnterior : 0;
        int nv = quantidadeNova != null ? quantidadeNova : 0;
        return ant + " -> " + nv + " un.";
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String getProdutoNome() {
        return produtoNome != null ? produtoNome : "Produto #" + produtoId;
    }

    public void setProdutoNome(String produtoNome) {
        this.produtoNome = produtoNome;
    }

    public String getProdutoCodigo() {
        return produtoCodigo != null ? produtoCodigo : "-";
    }

    public void setProdutoCodigo(String produtoCodigo) {
        this.produtoCodigo = produtoCodigo;
    }

    public String getProdutoLocalizacao() {
        return (produtoLocalizacao != null && !produtoLocalizacao.trim().isEmpty()) ? produtoLocalizacao : "Sem local";
    }

    public void setProdutoLocalizacao(String produtoLocalizacao) {
        this.produtoLocalizacao = produtoLocalizacao;
    }

    public String getProdutoGrupo() {
        return (produtoGrupo != null && !produtoGrupo.trim().isEmpty()) ? produtoGrupo : "GERAL";
    }

    public void setProdutoGrupo(String produtoGrupo) {
        this.produtoGrupo = produtoGrupo != null && !produtoGrupo.trim().isEmpty() ? produtoGrupo : "GERAL";
    }

    public String getResponsavel() {
        return (responsavel != null && !responsavel.trim().isEmpty()) ? responsavel : "Não informado";
    }

    public void setResponsavel(String responsavel) {
        this.responsavel = responsavel;
    }

    @Override
    public String toString() {
        return "HistoricoEstoque{" +
                "id=" + id +
                ", produtoId=" + produtoId +
                ", dataHora='" + dataHora + '\'' +
                ", tipo='" + tipo + '\'' +
                ", quantidadeAlterada=" + quantidadeAlterada +
                ", quantidadeAnterior=" + quantidadeAnterior +
                ", quantidadeNova=" + quantidadeNova +
                ", motivo='" + motivo + '\'' +
                ", responsavel='" + responsavel + '\'' +
                ", produtoNome='" + produtoNome + '\'' +
                ", produtoCodigo='" + produtoCodigo + '\'' +
                ", produtoLocalizacao='" + produtoLocalizacao + '\'' +
                ", produtoGrupo='" + produtoGrupo + '\'' +
                '}';
    }
}
