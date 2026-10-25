package com.rafael.pixapi.mensageria;

import com.rafael.pixapi.domain.Conta;
import com.rafael.pixapi.repository.ContaRepository;
import com.rafael.pixapi.service.PublicadorDeEventos;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Integração ponta a ponta com um broker Kafka de verdade, embutido no teste (sem Docker):
// POST /transferencias → commit → KafkaPublicadorDeEventos → tópico → NotificacaoConsumer.
@SpringBootTest(properties = {
        "pix.mensageria=kafka",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@EmbeddedKafka(partitions = 3, topics = KafkaConfig.TOPICO_TRANSFERENCIAS)
@AutoConfigureMockMvc
class KafkaMensageriaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContaRepository repository;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private PublicadorDeEventos publicador;

    @Autowired
    private NotificacaoConsumer notificacoes;

    @Test
    void comPropriedadeKafka_deveUsarOPublicadorKafka() {
        assertThat(publicador).isInstanceOf(KafkaPublicadorDeEventos.class);
    }

    @Test
    void transferencia_deveProduzirMensagemNoTopicoEGerarNotificacao() throws Exception {
        String origem = "kafka-" + UUID.randomUUID() + "@pix.com";
        String destino = "kafka-" + UUID.randomUUID() + "@pix.com";
        repository.save(new Conta("Origem Kafka", "12345678901", origem, new BigDecimal("100.00")));
        repository.save(new Conta("Destino Kafka", "10987654321", destino, BigDecimal.ZERO));

        // Consumidor "espião" do próprio teste, com grupo diferente do consumidor da aplicação:
        // no Kafka, cada grupo recebe sua própria cópia de todas as mensagens.
        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "teste-" + UUID.randomUUID(), false);
        props.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> espiao = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(espiao, KafkaConfig.TOPICO_TRANSFERENCIAS);

            mockMvc.perform(post("/transferencias").contentType(MediaType.APPLICATION_JSON).content("""
                            {"chaveOrigem": "%s", "chaveDestino": "%s", "valor": 12.34}
                            """.formatted(origem, destino)))
                    .andExpect(status().isOk());

            // 1) A mensagem chegou ao tópico, com a chave de origem como key (ordem por conta).
            ConsumerRecord<String, String> mensagem = KafkaTestUtils.getSingleRecord(
                    espiao, KafkaConfig.TOPICO_TRANSFERENCIAS, Duration.ofSeconds(15));
            assertThat(mensagem.key()).isEqualTo(origem);
            assertThat(mensagem.value())
                    .contains("\"chaveOrigem\":\"" + origem + "\"")
                    .contains("\"chaveDestino\":\"" + destino + "\"")
                    .contains("\"valor\":12.34")
                    .contains("\"id\":");
        }

        // 2) O consumidor de exemplo da aplicação leu o tópico e registrou a notificação.
        //    É assíncrono, então esperamos (com limite) em vez de usar sleep fixo.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(notificacoes.notificacoes())
                        .anyMatch(n -> n.contains(destino) && n.contains("R$ 12,34")));
    }

    @Test
    void consumidor_deveIgnorarEventoRepetido() {
        String json = """
                {"id":"6f1b7c1e-0000-4000-8000-000000000001","chaveOrigem":"a@pix.com",
                 "chaveDestino":"dup@pix.com","valor":1.00,"realizadaEm":"2026-10-25T12:00:00Z"}
                """;
        int antes = notificacoes.notificacoes().size();

        notificacoes.receber(json);
        notificacoes.receber(json); // o Kafka garante "pelo menos uma vez": duplicatas acontecem

        assertThat(notificacoes.notificacoes()).hasSize(antes + 1);
    }
}
