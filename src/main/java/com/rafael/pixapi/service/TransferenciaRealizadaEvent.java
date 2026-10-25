package com.rafael.pixapi.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Evento de domínio: um fato que JÁ aconteceu (por isso o verbo no passado).
// É imutável (record), como uma struct com "let" em Swift, e pode ser enviado a outros sistemas.
// O id único permite que os consumidores ignorem duplicatas (idempotência).
public record TransferenciaRealizadaEvent(
        UUID id,
        String chaveOrigem,
        String chaveDestino,
        BigDecimal valor,
        Instant realizadaEm
) {
}
