package com.rafael.pixapi.mensageria;

import com.rafael.pixapi.service.PublicadorDeEventos;
import com.rafael.pixapi.service.TransferenciaRealizadaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// Implementação padrão (usada no Render, que não tem Kafka): só registra o evento no log.
// matchIfMissing = true → ativa quando pix.mensageria não está definida ou vale "log".
@Component
@ConditionalOnProperty(name = "pix.mensageria", havingValue = "log", matchIfMissing = true)
public class LogPublicadorDeEventos implements PublicadorDeEventos {

    private static final Logger log = LoggerFactory.getLogger(LogPublicadorDeEventos.class);

    @Override
    public void publicar(TransferenciaRealizadaEvent evento) {
        log.info("Evento TransferenciaRealizada id={} {} -> {} valor={} em={}",
                evento.id(), evento.chaveOrigem(), evento.chaveDestino(), evento.valor(), evento.realizadaEm());
    }
}
