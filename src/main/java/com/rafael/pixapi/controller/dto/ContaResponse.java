package com.rafael.pixapi.controller.dto;

import com.rafael.pixapi.domain.Conta;

import java.math.BigDecimal;

// DTO de saída: a API não expõe a entidade JPA diretamente.
public record ContaResponse(Long id, String titular, String cpf, String chavePix, BigDecimal saldo) {

    public static ContaResponse from(Conta conta) {
        return new ContaResponse(conta.getId(), conta.getTitular(), conta.getCpf(),
                conta.getChavePix(), conta.getSaldo());
    }
}
