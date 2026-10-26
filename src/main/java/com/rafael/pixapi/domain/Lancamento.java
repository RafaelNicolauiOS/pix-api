package com.rafael.pixapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Linha do razão. Imutável: sem setters. Um erro contábil se corrige com um novo lançamento,
// nunca editando ou apagando o antigo.
@Entity
public class Lancamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conta_id", nullable = false)
    private Long contaId;

    @Enumerated(EnumType.STRING) // grava "PIX_ENVIADO" (legível), não a posição do enum
    @Column(nullable = false, length = 12)
    private TipoLancamento tipo;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(name = "realizado_em", nullable = false)
    private Instant realizadoEm;

    @Column(name = "transferencia_id")
    private UUID transferenciaId;

    protected Lancamento() {
    }

    private Lancamento(Long contaId, TipoLancamento tipo, BigDecimal valor, Instant realizadoEm, UUID transferenciaId) {
        this.contaId = contaId;
        this.tipo = tipo;
        this.valor = valor;
        this.realizadoEm = realizadoEm;
        this.transferenciaId = transferenciaId;
    }

    // "Static factory methods": nomes que dizem a intenção, como inits nomeados em Swift.
    public static Lancamento abertura(Long contaId, BigDecimal valor, Instant em) {
        return new Lancamento(contaId, TipoLancamento.ABERTURA, valor, em, null);
    }

    public static Lancamento pixEnviado(Long contaId, BigDecimal valor, Instant em, UUID transferenciaId) {
        return new Lancamento(contaId, TipoLancamento.PIX_ENVIADO, valor, em, transferenciaId);
    }

    public static Lancamento pixRecebido(Long contaId, BigDecimal valor, Instant em, UUID transferenciaId) {
        return new Lancamento(contaId, TipoLancamento.PIX_RECEBIDO, valor, em, transferenciaId);
    }

    public static Lancamento encerramento(Long contaId, BigDecimal valor, Instant em) {
        return new Lancamento(contaId, TipoLancamento.ENCERRAMENTO, valor, em, null);
    }

    // Crédito soma, débito subtrai: somar valorComSinal() de uma conta dá o saldo dela.
    public BigDecimal valorComSinal() {
        return tipo.isCredito() ? valor : valor.negate();
    }

    public Long getId() {
        return id;
    }

    public Long getContaId() {
        return contaId;
    }

    public TipoLancamento getTipo() {
        return tipo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public Instant getRealizadoEm() {
        return realizadoEm;
    }

    public UUID getTransferenciaId() {
        return transferenciaId;
    }
}
