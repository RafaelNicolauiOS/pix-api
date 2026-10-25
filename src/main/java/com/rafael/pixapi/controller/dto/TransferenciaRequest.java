package com.rafael.pixapi.controller.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferenciaRequest(
        @NotBlank String chaveOrigem,
        @NotBlank String chaveDestino,
        // @Digits: no máximo 2 casas decimais (centavos) — recusa 0.001
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal valor
) {
}
