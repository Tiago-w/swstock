package com.swstock.model;

/**
 * Representa um funcionário cadastrado no sistema para registro de responsabilidade nas movimentações de estoque.
 */
public class Funcionario {

    private Integer id;
    private String nome;
    private String createdAt;

    public Funcionario() {
    }

    public Funcionario(String nome) {
        this.nome = nome;
    }

    public Funcionario(Integer id, String nome, String createdAt) {
        this.id = id;
        this.nome = nome;
        this.createdAt = createdAt;
    }

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

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return nome != null ? nome : "";
    }
}
