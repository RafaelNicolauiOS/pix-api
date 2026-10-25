package com.rafael.pixapi.monitoramento;

import com.rafael.pixapi.service.TransferenciaRealizadaEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

// Guarda os últimos eventos em memória (um "buffer circular": ao encher, descarta o mais antigo).
// É só para visualização: zera quando a aplicação reinicia. O histórico oficial das
// transferências continua sendo o banco (e, no modo kafka, o tópico).
public class HistoricoDeEventos {

    private final int capacidade;
    private final Deque<TransferenciaRealizadaEvent> eventos = new ArrayDeque<>();

    public HistoricoDeEventos(int capacidade) {
        this.capacidade = capacidade;
    }

    // synchronized: várias requisições (threads) podem registrar ao mesmo tempo.
    // Em Swift seria como proteger o array com um actor ou uma serial queue.
    public synchronized void registrar(TransferenciaRealizadaEvent evento) {
        eventos.addFirst(evento);
        while (eventos.size() > capacidade) {
            eventos.removeLast();
        }
    }

    public synchronized List<TransferenciaRealizadaEvent> recentes() {
        return List.copyOf(eventos);
    }
}
