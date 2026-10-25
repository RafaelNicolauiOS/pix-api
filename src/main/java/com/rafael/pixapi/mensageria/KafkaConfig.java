package com.rafael.pixapi.mensageria;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// Só é carregada com pix.mensageria=kafka. No modo "log" (Render) a aplicação nem tenta
// conectar a um broker, porque não existe nenhum bean de Kafka que precise dele.
@Configuration
@ConditionalOnProperty(name = "pix.mensageria", havingValue = "kafka")
public class KafkaConfig {

    public static final String TOPICO_TRANSFERENCIAS = "pix.transferencias";

    // O Spring cria o tópico no broker ao iniciar, se ainda não existir.
    // 3 partições permitem até 3 consumidores do mesmo grupo lendo em paralelo.
    @Bean
    NewTopic topicoTransferencias() {
        return TopicBuilder.name(TOPICO_TRANSFERENCIAS).partitions(3).replicas(1).build();
    }
}
