package com.rafael.pixapi.monitoramento;

import java.time.Instant;

// Uma linha de log já "traduzida" para JSON. logger traz só o nome curto da classe.
public record LinhaDeLog(Instant instante, String nivel, String logger, String mensagem) {
}
