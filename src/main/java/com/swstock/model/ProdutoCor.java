package com.swstock.model;

import java.util.Objects;

/**
 * Entidade que representa uma variação de cor e seu estoque individual para um produto.
 */
public class ProdutoCor {

    private Integer id;
    private Integer produtoId;
    private String nomeCor;
    private Integer quantidade;

    public ProdutoCor() {
        this.quantidade = 0;
    }

    public ProdutoCor(Integer id, Integer produtoId, String nomeCor, Integer quantidade) {
        this.id = id;
        this.produtoId = produtoId;
        this.nomeCor = nomeCor != null ? nomeCor.trim() : "";
        this.quantidade = quantidade != null ? quantidade : 0;
    }

    public ProdutoCor(Integer produtoId, String nomeCor, Integer quantidade) {
        this(null, produtoId, nomeCor, quantidade);
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

    public String getNomeCor() {
        return nomeCor;
    }

    public void setNomeCor(String nomeCor) {
        this.nomeCor = nomeCor != null ? nomeCor.trim() : "";
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade != null ? quantidade : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProdutoCor that = (ProdutoCor) o;
        return Objects.equals(id, that.id) ||
                (Objects.equals(produtoId, that.produtoId) &&
                 Objects.equals(nomeCor != null ? nomeCor.toUpperCase() : null,
                                that.nomeCor != null ? that.nomeCor.toUpperCase() : null));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, produtoId, nomeCor != null ? nomeCor.toUpperCase() : null);
    }

    @Override
    public String toString() {
        return nomeCor + " (" + quantidade + " un.)";
    }
}
