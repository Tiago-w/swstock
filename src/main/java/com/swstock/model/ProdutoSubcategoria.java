package com.swstock.model;

import java.util.Objects;

/**
 * Entidade que representa uma subcategoria/variação e seu estoque individual para um produto.
 */
public class ProdutoSubcategoria {

    private Integer id;
    private Integer produtoId;
    private String nomeSubcategoria;
    private Integer quantidade;

    public ProdutoSubcategoria() {
        this.quantidade = 0;
    }

    public ProdutoSubcategoria(Integer id, Integer produtoId, String nomeSubcategoria, Integer quantidade) {
        this.id = id;
        this.produtoId = produtoId;
        this.nomeSubcategoria = nomeSubcategoria != null ? nomeSubcategoria.trim() : "";
        this.quantidade = quantidade != null ? quantidade : 0;
    }

    public ProdutoSubcategoria(Integer produtoId, String nomeSubcategoria, Integer quantidade) {
        this(null, produtoId, nomeSubcategoria, quantidade);
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

    public String getNomeSubcategoria() {
        return nomeSubcategoria;
    }

    public void setNomeSubcategoria(String nomeSubcategoria) {
        this.nomeSubcategoria = nomeSubcategoria != null ? nomeSubcategoria.trim() : "";
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
        ProdutoSubcategoria that = (ProdutoSubcategoria) o;
        return Objects.equals(id, that.id) ||
                (Objects.equals(produtoId, that.produtoId) &&
                 Objects.equals(nomeSubcategoria != null ? nomeSubcategoria.toUpperCase() : null,
                                that.nomeSubcategoria != null ? that.nomeSubcategoria.toUpperCase() : null));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, produtoId, nomeSubcategoria != null ? nomeSubcategoria.toUpperCase() : null);
    }

    @Override
    public String toString() {
        return nomeSubcategoria + " (" + quantidade + " un.)";
    }
}
