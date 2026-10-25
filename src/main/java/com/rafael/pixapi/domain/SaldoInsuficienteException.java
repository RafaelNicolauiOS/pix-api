package com.rafael.pixapi.domain;

import java.math.BigDecimal;

// Fica no domínio porque é lançada pela própria Conta ao debitar.
public class SaldoInsuficienteException extends RuntimeException {

    public SaldoInsuficienteException(String chavePix, BigDecimal valor) {
        super("Saldo insuficiente na chave " + chavePix + " para transferir R$ " + valor);
    }
}
