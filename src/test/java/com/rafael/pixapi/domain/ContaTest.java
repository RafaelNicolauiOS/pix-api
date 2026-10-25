package com.rafael.pixapi.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Teste de domínio puro: nem Spring, nem Mockito. É o tipo de teste mais rápido e barato.
class ContaTest {

    private Conta contaCom(String saldo) {
        return new Conta("Rafael", "12345678901", "rafael@email.com", new BigDecimal(saldo));
    }

    @Test
    void debitar_deveReduzirSaldo() {
        Conta conta = contaCom("100.00");

        conta.debitar(new BigDecimal("30.50"));

        assertThat(conta.getSaldo()).isEqualByComparingTo("69.50");
    }

    @Test
    void debitar_devePermitirZerarSaldo() {
        Conta conta = contaCom("100.00");

        conta.debitar(new BigDecimal("100.00"));

        assertThat(conta.getSaldo()).isEqualByComparingTo("0");
    }

    @Test
    void debitar_deveLancarExcecaoESemAlterarSaldo_quandoSaldoInsuficiente() {
        Conta conta = contaCom("10.00");

        assertThatThrownBy(() -> conta.debitar(new BigDecimal("10.01")))
                .isInstanceOf(SaldoInsuficienteException.class);
        assertThat(conta.getSaldo()).isEqualByComparingTo("10.00");
    }

    @Test
    void creditar_deveAumentarSaldo() {
        Conta conta = contaCom("10.00");

        conta.creditar(new BigDecimal("0.10"));

        assertThat(conta.getSaldo()).isEqualByComparingTo("10.10");
    }
}
