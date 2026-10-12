package com.swstock.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Representa uma movimentação no histórico de estoque específico para Bases.
 */
public class HistoricoBase {

    private Integer id;
    private Integer baseId;
    private String dataHora;
    private String tipo; // "ENTRADA", "SAIDA", "AJUSTE"
    private Integer quantidadeAlterada;
    private Integer quantidadeAnterior;
    private Integer quantidadeNova;
    private String motivo;
    private String responsavel;

    // Metadados da Base para exibição tabular
    private String baseCodigo;
    private String baseNome;
    private String baseTipo;
    private String baseFabricante;

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public HistoricoBase() {
    }

    public HistoricoBase(Integer baseId, String tipo, Integer quantidadeAlterada, Integer quantidadeAnterior, Integer quantidadeNova, String motivo, String responsavel) {
        this.baseId = baseId;
        this.dataHora = LocalDateTime.now().format(FORMATTER);
        this.tipo = tipo;
        this.quantidadeAlterada = quantidadeAlterada;
        this.quantidadeAnterior = quantidadeAnterior;
        this.quantidadeNova = quantidadeNova;
        this.motivo = motivo;
        this.responsavel = (responsavel != null && !responsavel.trim().isEmpty()) ? responsavel : "Não informado";
    }

    public HistoricoBase(Integer id, Integer baseId, String dataHora, String tipo, Integer quantidadeAlterada, Integer quantidadeAnterior, Integer quantidadeNova, String motivo, String responsavel, String baseCodigo, String baseNome, String baseTipo, String baseFabricante) {
        this.id = id;
        this.baseId = baseId;
        this.dataHora = dataHora;
        this.tipo = tipo;
        this.quantidadeAlterada = quantidadeAlterada;
        this.quantidadeAnterior = quantidadeAnterior;
        this.quantidadeNova = quantidadeNova;
        this.motivo = motivo;
        this.responsavel = (responsavel != null && !responsavel.trim().isEmpty()) ? responsavel : "Não informado";
        this.baseCodigo = baseCodigo;
        this.baseNome = baseNome;
        this.baseTipo = baseTipo;
        this.baseFabricante = baseFabricante;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getBaseId() {
        return baseId;
    }

    public void setBaseId(Integer baseId) {
        this.baseId = baseId;
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
        if (quantidadeAlterada > 0) return "+" + quantidadeAlterada + " un.";
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
        return (motivo != null && !motivo.trim().isEmpty()) ? motivo : "-";
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String getResponsavel() {
        return (responsavel != null && !responsavel.trim().isEmpty()) ? responsavel : "Não informado";
    }

    public void setResponsavel(String responsavel) {
        this.responsavel = responsavel;
    }

    public String getBaseTamanho() {
        return baseCodigo != null ? baseCodigo : "-";
    }

    public void setBaseTamanho(String baseTamanho) {
        this.baseCodigo = baseTamanho;
    }

    public String getBaseCodigo() {
        return getBaseTamanho();
    }

    public void setBaseCodigo(String baseCodigo) {
        setBaseTamanho(baseCodigo);
    }

    public String getBaseNome() {
        return baseNome != null ? baseNome : "-";
    }

    public void setBaseNome(String baseNome) {
        this.baseNome = baseNome;
    }

    public String getBaseTipo() {
        return baseTipo != null ? baseTipo : "AUTOMOTIVA";
    }

    public void setBaseTipo(String baseTipo) {
        this.baseTipo = baseTipo;
    }

    public String getBaseTipoFormatado() {
        if ("AUTOMOTIVA".equalsIgnoreCase(baseTipo)) return "Automotiva";
        if ("PREDIAL".equalsIgnoreCase(baseTipo)) return "Predial";
        return baseTipo;
    }

    public String getBaseFabricante() {
        return baseFabricante != null ? baseFabricante : "Geral";
    }

    public void setBaseFabricante(String baseFabricante) {
        this.baseFabricante = baseFabricante;
    }
}
