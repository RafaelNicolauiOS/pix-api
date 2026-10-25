package com.rafael.pixapi.service;

import java.math.BigDecimal;
import java.time.Instant;

// Resultado da transferência. Instant = momento exato em UTC (como Date em Swift).
public record ComprovanteTransferencia(
        String chaveOrigem,
        String chaveDestino,
        BigDecimal valor,
        BigDecimal saldoOrigem,
        Instant realizadaEm
) {
}
