package com.rafael.pixapi.service;

// "Porta" de saída: o service diz O QUE precisa (publicar um evento), sem saber COMO.
// É o mesmo papel de um protocol em Swift: as implementações (Kafka, log...) ficam no
// pacote mensageria e podem ser trocadas por configuração, sem mexer na regra de negócio.
public interface PublicadorDeEventos {

    void publicar(TransferenciaRealizadaEvent evento);
}
