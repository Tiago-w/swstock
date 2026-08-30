package com.swstock.model;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Entidade de domínio representando um Produto no sistema SWStock.
 */
public class Produto {

    private Integer id;
    private String nome;
    private String grupo;
    private Double precoVista;
    private Double precoPrazo;
    private String codigoLoja;
    private String localizacao;
    private Integer quantidade;
    private String urlImagem;
    private String descricaoBreve;

    private static final Locale LOCALE_BR = Locale.of("pt", "BR");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(LOCALE_BR);

    public Produto() {
        this.grupo = "GERAL";
        this.precoVista = 0.0;
        this.precoPrazo = 0.0;
        this.quantidade = 0;
    }

    public Produto(Integer id, String nome, String grupo, Double precoVista, Double precoPrazo,
                   String codigoLoja, String localizacao, Integer quantidade,
                   String urlImagem, String descricaoBreve) {
        this.id = id;
        this.nome = nome;
        this.grupo = grupo != null && !grupo.trim().isEmpty() ? grupo : "GERAL";
        this.precoVista = precoVista != null ? precoVista : 0.0;
        this.precoPrazo = precoPrazo != null ? precoPrazo : 0.0;
        this.codigoLoja = codigoLoja;
        this.localizacao = localizacao;
        this.quantidade = quantidade != null ? quantidade : 0;
        this.urlImagem = urlImagem;
        this.descricaoBreve = descricaoBreve;
    }

    public Produto(String nome, String grupo, Double precoVista, Double precoPrazo,
                   String codigoLoja, String localizacao, Integer quantidade,
                   String urlImagem, String descricaoBreve) {
        this(null, nome, grupo, precoVista, precoPrazo, codigoLoja, localizacao, quantidade, urlImagem, descricaoBreve);
    }

    public Produto(Integer id, String nome, Double precoVista, Double precoPrazo,
                   String codigoLoja, String localizacao, Integer quantidade,
                   String urlImagem, String descricaoBreve) {
        this(id, nome, "GERAL", precoVista, precoPrazo, codigoLoja, localizacao, quantidade, urlImagem, descricaoBreve);
    }

    public Produto(String nome, Double precoVista, Double precoPrazo,
                   String codigoLoja, String localizacao, Integer quantidade,
                   String urlImagem, String descricaoBreve) {
        this(null, nome, "GERAL", precoVista, precoPrazo, codigoLoja, localizacao, quantidade, urlImagem, descricaoBreve);
    }

    // Getters and Setters

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getGrupo() {
        return grupo;
    }

    public void setGrupo(String grupo) {
        this.grupo = grupo != null && !grupo.trim().isEmpty() ? grupo : "GERAL";
    }

    public Double getPrecoVista() {
        return precoVista;
    }

    public void setPrecoVista(Double precoVista) {
        this.precoVista = precoVista;
    }

    public Double getPrecoPrazo() {
        return precoPrazo;
    }

    public void setPrecoPrazo(Double precoPrazo) {
        this.precoPrazo = precoPrazo;
    }

    public String getCodigoLoja() {
        return codigoLoja;
    }

    public void setCodigoLoja(String codigoLoja) {
        this.codigoLoja = codigoLoja;
    }

    public String getLocalizacao() {
        return localizacao;
    }

    public void setLocalizacao(String localizacao) {
        this.localizacao = localizacao;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade;
    }

    public String getUrlImagem() {
        return urlImagem;
    }

    public void setUrlImagem(String urlImagem) {
        this.urlImagem = urlImagem;
    }

    public String getDescricaoBreve() {
        return descricaoBreve;
    }

    public void setDescricaoBreve(String descricaoBreve) {
        this.descricaoBreve = descricaoBreve;
    }

    // Helper formatting methods for UI presentation
    public String getPrecoVistaFormatado() {
        return precoVista != null ? CURRENCY_FORMAT.format(precoVista) : "R$ 0,00";
    }

    public String getPrecoPrazoFormatado() {
        return precoPrazo != null ? CURRENCY_FORMAT.format(precoPrazo) : "R$ 0,00";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Produto produto = (Produto) o;
        return Objects.equals(id, produto.id) ||
                (codigoLoja != null && Objects.equals(codigoLoja, produto.codigoLoja));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, codigoLoja);
    }

    @Override
    public String toString() {
        return "Produto{" +
                "id=" + id +
                ", nome='" + nome + '\'' +
                ", grupo='" + grupo + '\'' +
                ", precoVista=" + precoVista +
                ", precoPrazo=" + precoPrazo +
                ", codigoLoja='" + codigoLoja + '\'' +
                ", localizacao='" + localizacao + '\'' +
                ", quantidade=" + quantidade +
                '}';
    }
}
