package com.rafael.pixapi.mensageria;

import com.rafael.pixapi.service.PublicadorDeEventos;
import com.rafael.pixapi.service.TransferenciaRealizadaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// Envia o evento como JSON para o tópico pix.transferencias.
// Serializamos nós mesmos (JsonMapper + String) em vez de usar o serializer JSON do Kafka:
// a mensagem fica legível em qualquer ferramenta e não carrega nomes de classes Java,
// então consumidores escritos em outras linguagens também conseguem ler.
@Component
@ConditionalOnProperty(name = "pix.mensageria", havingValue = "kafka")
public class KafkaPublicadorDeEventos implements PublicadorDeEventos {

    private static final Logger log = LoggerFactory.getLogger(KafkaPublicadorDeEventos.class);

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;

    public KafkaPublicadorDeEventos(KafkaTemplate<String, String> kafka, JsonMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    @Override
    public void publicar(TransferenciaRealizadaEvent evento) {
        String payload = json.writeValueAsString(evento);
        // A chave da mensagem é a chave Pix de origem: o Kafka manda mensagens com a mesma chave
        // para a mesma partição, então os eventos de uma conta são lidos na ordem em que ocorreram.
        kafka.send(KafkaConfig.TOPICO_TRANSFERENCIAS, evento.chaveOrigem(), payload)
                // send() é assíncrono (devolve um CompletableFuture, parecido com uma Task em Swift).
                .whenComplete((resultado, erro) -> {
                    if (erro != null) {
                        // A transferência já foi gravada; se o envio falhar, o evento se perde.
                        // O padrão Transactional Outbox (ver README) resolve esse caso.
                        log.error("Falha ao publicar evento {} no Kafka", evento.id(), erro);
                    } else {
                        log.info("Evento {} publicado em {}-{}@{}", evento.id(),
                                resultado.getRecordMetadata().topic(),
                                resultado.getRecordMetadata().partition(),
                                resultado.getRecordMetadata().offset());
                    }
                });
    }
}
