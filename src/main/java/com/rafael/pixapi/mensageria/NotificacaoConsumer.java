package com.rafael.pixapi.mensageria;

import com.rafael.pixapi.service.TransferenciaRealizadaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

// Consumidor de exemplo: simula um serviço de notificações que avisa quem recebeu o Pix.
// Num sistema real ele seria outra aplicação; aqui fica no mesmo projeto para fins didáticos.
@Component
@ConditionalOnProperty(name = "pix.mensageria", havingValue = "kafka")
public class NotificacaoConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoConsumer.class);
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final int MAX_NOTIFICACOES = 50;

    private final JsonMapper json;

    // O Kafka entrega "pelo menos uma vez": a mesma mensagem pode chegar repetida (ex.: após
    // uma reconexão). Guardamos os ids já processados para não notificar duas vezes (idempotência).
    // Em produção esse controle ficaria no banco; em memória basta para o exemplo.
    private final Set<UUID> processados = ConcurrentHashMap.newKeySet();
    private final Deque<String> notificacoes = new ConcurrentLinkedDeque<>();

    public NotificacaoConsumer(JsonMapper json) {
        this.json = json;
    }

    // groupId: consumidores com o mesmo grupo dividem as mensagens entre si;
    // grupos diferentes recebem, cada um, uma cópia de todas as mensagens.
    @KafkaListener(topics = KafkaConfig.TOPICO_TRANSFERENCIAS, groupId = "pix-notificacoes")
    public void receber(String mensagem) {
        TransferenciaRealizadaEvent evento = json.readValue(mensagem, TransferenciaRealizadaEvent.class);
        if (!processados.add(evento.id())) {
            log.info("Evento {} já processado, ignorando duplicata", evento.id());
            return;
        }

        String notificacao = "Notificação para %s: você recebeu um Pix de %s de %s"
                .formatted(evento.chaveDestino(), reais(evento.valor()), evento.chaveOrigem());
        log.info(notificacao);

        notificacoes.addFirst(notificacao);
        while (notificacoes.size() > MAX_NOTIFICACOES) {
            notificacoes.pollLast();
        }
    }

    // Últimas notificações, da mais recente para a mais antiga.
    public List<String> notificacoes() {
        return List.copyOf(notificacoes);
    }

    private static String reais(BigDecimal valor) {
        return String.format(PT_BR, "R$ %,.2f", valor);
    }
}
