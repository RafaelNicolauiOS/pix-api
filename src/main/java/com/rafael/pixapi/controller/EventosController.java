package com.rafael.pixapi.controller;

import com.rafael.pixapi.monitoramento.HistoricoDeEventos;
import com.rafael.pixapi.service.TransferenciaRealizadaEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Feed público com as últimas transferências confirmadas (mais recente primeiro).
// Mostra só os dados do evento, que já são visíveis em GET /contas: nada interno é exposto.
@RestController
@RequestMapping("/eventos")
public class EventosController {

    private final HistoricoDeEventos historico;

    public EventosController(HistoricoDeEventos historico) {
        this.historico = historico;
    }

    @GetMapping
    public List<TransferenciaRealizadaEvent> recentes() {
        return historico.recentes();
    }
}
