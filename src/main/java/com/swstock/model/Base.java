package com.swstock.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Representa uma Base de Tinta (Bases Automotivas ou Bases Prediais).
 * Possui: codigo, nome, quantidade de estoque e valor de compra.
 */
public class Base {

    private Integer id;
    private String tamanho;
    private String nome;
    private String tipo; // "AUTOMOTIVA" ou "PREDIAL"
    private String fabricante; // ex: "Geral", "Coral", "Suvinil", "Lazzuril", "Wanda", etc.
    private Integer quantidade;
    private Double valorCompra;
    private String createdAt;
    private String updatedAt;

    public Base() {
        this.quantidade = 0;
        this.valorCompra = 0.0;
        this.fabricante = "Geral";
        this.tipo = "AUTOMOTIVA";
    }

    public Base(String tamanho, String nome, String tipo, String fabricante, Integer quantidade, Double valorCompra) {
        this.tamanho = tamanho != null ? tamanho.trim() : "";
        this.nome = nome;
        this.tipo = tipo != null ? tipo.trim().toUpperCase() : "AUTOMOTIVA";
        this.fabricante = (fabricante != null && !fabricante.trim().isEmpty()) ? fabricante.trim() : "Geral";
        this.quantidade = quantidade != null ? Math.max(0, quantidade) : 0;
        this.valorCompra = valorCompra != null ? Math.max(0.0, valorCompra) : 0.0;
    }

    public Base(Integer id, String tamanho, String nome, String tipo, String fabricante, Integer quantidade, Double valorCompra, String createdAt, String updatedAt) {
        this.id = id;
        this.tamanho = tamanho != null ? tamanho.trim() : "";
        this.nome = nome;
        this.tipo = tipo != null ? tipo.trim().toUpperCase() : "AUTOMOTIVA";
        this.fabricante = (fabricante != null && !fabricante.trim().isEmpty()) ? fabricante.trim() : "Geral";
        this.quantidade = quantidade != null ? Math.max(0, quantidade) : 0;
        this.valorCompra = valorCompra != null ? Math.max(0.0, valorCompra) : 0.0;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTamanho() {
        return tamanho != null ? tamanho : "";
    }

    public void setTamanho(String tamanho) {
        this.tamanho = tamanho != null ? tamanho.trim() : "";
    }

    // Métodos de compatibilidade com versões anteriores
    public String getCodigo() {
        return getTamanho();
    }

    public void setCodigo(String codigo) {
        setTamanho(codigo);
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo != null ? tipo.trim().toUpperCase() : "AUTOMOTIVA";
    }

    public String getTipoFormatado() {
        if ("AUTOMOTIVA".equalsIgnoreCase(tipo)) return "Automotiva";
        if ("PREDIAL".equalsIgnoreCase(tipo)) return "Predial";
        return tipo;
    }

    public String getFabricante() {
        return (fabricante != null && !fabricante.trim().isEmpty()) ? fabricante : "Geral";
    }

    public void setFabricante(String fabricante) {
        this.fabricante = (fabricante != null && !fabricante.trim().isEmpty()) ? fabricante.trim() : "Geral";
    }

    public Integer getQuantidade() {
        return quantidade != null ? quantidade : 0;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade != null ? Math.max(0, quantidade) : 0;
    }

    public String getQuantidadeFormatada() {
        return (quantidade != null ? quantidade : 0) + " un.";
    }

    public Double getValorCompra() {
        return valorCompra != null ? valorCompra : 0.0;
    }

    public void setValorCompra(Double valorCompra) {
        this.valorCompra = valorCompra != null ? Math.max(0.0, valorCompra) : 0.0;
    }

    public String getValorCompraFormatado() {
        return String.format("R$ %.2f", getValorCompra()).replace(".", ",");
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "Base{" +
                "id=" + id +
                ", tamanho='" + tamanho + '\'' +
                ", nome='" + nome + '\'' +
                ", tipo='" + tipo + '\'' +
                ", fabricante='" + fabricante + '\'' +
                ", quantidade=" + quantidade +
                ", valorCompra=" + valorCompra +
                '}';
    }
}
