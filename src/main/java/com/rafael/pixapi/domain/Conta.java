package com.rafael.pixapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.math.BigDecimal;

// @Entity: o JPA/Hibernate mapeia esta classe para a tabela "conta".
@Entity
public class Conta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto-incremento no banco
    private Long id;

    @Column(nullable = false)
    private String titular;

    @Column(nullable = false, length = 11)
    private String cpf;

    @Column(nullable = false, unique = true)
    private String chavePix;

    // BigDecimal para dinheiro: double tem erro de arredondamento (0.1 + 0.2 != 0.3).
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal saldo;

    // O JPA exige um construtor sem argumentos para instanciar a entidade ao ler do banco.
    protected Conta() {
    }

    public Conta(String titular, String cpf, String chavePix, BigDecimal saldo) {
        this.titular = titular;
        this.cpf = cpf;
        this.chavePix = chavePix;
        this.saldo = saldo;
    }

    // Regra de negócio fica no domínio: saldo e CPF não mudam por atualização cadastral.
    public void atualizarDados(String titular, String chavePix) {
        this.titular = titular;
        this.chavePix = chavePix;
    }

    // A própria conta protege a regra "não pode ficar negativa" (encapsulamento):
    // ninguém de fora consegue alterar o saldo sem passar por aqui.
    public void debitar(BigDecimal valor) {
        if (saldo.compareTo(valor) < 0) {
            throw new SaldoInsuficienteException(chavePix, valor);
        }
        saldo = saldo.subtract(valor);
    }

    public void creditar(BigDecimal valor) {
        saldo = saldo.add(valor);
    }

    public Long getId() {
        return id;
    }

    public String getTitular() {
        return titular;
    }

    public String getCpf() {
        return cpf;
    }

    public String getChavePix() {
        return chavePix;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }
}
