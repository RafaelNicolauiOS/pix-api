package com.rafael.pixapi.controller.dto;

import com.rafael.pixapi.domain.Conta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

// record = tipo imutável com construtor, getters, equals e hashCode gerados (parecido com struct em Swift).
// As anotações de validação são checadas pelo @Valid no controller.
public record ContaRequest(
        @NotBlank String titular,
        @NotBlank @Pattern(regexp = "\\d{11}", message = "deve conter 11 dígitos") String cpf,
        @NotBlank String chavePix,
        @NotNull @PositiveOrZero BigDecimal saldo
) {
    public Conta toEntity() {
        return new Conta(titular, cpf, chavePix, saldo);
    }
}
