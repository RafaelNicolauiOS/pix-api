package com.rafael.pixapi.monitoramento;

import com.rafael.pixapi.service.TransferenciaRealizadaEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricoDeEventosTest {

    private TransferenciaRealizadaEvent evento(String destino) {
        return new TransferenciaRealizadaEvent(UUID.randomUUID(), "ana@pix.com", destino, BigDecimal.TEN, Instant.now());
    }

    @Test
    void deveListarDoMaisRecenteParaOMaisAntigo() {
        HistoricoDeEventos historico = new HistoricoDeEventos(10);

        historico.registrar(evento("primeiro@pix.com"));
        historico.registrar(evento("segundo@pix.com"));

        assertThat(historico.recentes()).extracting(TransferenciaRealizadaEvent::chaveDestino)
                .containsExactly("segundo@pix.com", "primeiro@pix.com");
    }

    @Test
    void deveDescartarOsMaisAntigos_quandoPassarDaCapacidade() {
        HistoricoDeEventos historico = new HistoricoDeEventos(2);

        historico.registrar(evento("a@pix.com"));
        historico.registrar(evento("b@pix.com"));
        historico.registrar(evento("c@pix.com"));

        assertThat(historico.recentes()).extracting(TransferenciaRealizadaEvent::chaveDestino)
                .containsExactly("c@pix.com", "b@pix.com");
    }
}
