package com.rafael.pixapi.config;

import com.rafael.pixapi.domain.Conta;
import com.rafael.pixapi.repository.ContaRepository;
import com.rafael.pixapi.service.ContaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

// Carrega contas de exemplo ao iniciar. Escolhi CommandLineRunner em vez de data.sql porque:
//  - usa o construtor da entidade (mesmas regras do domínio, sem SQL duplicando o modelo);
//  - não depende da ordem "Hibernate cria tabela → data.sql roda";
//  - só insere se o banco estiver vazio e pode ser desligado com pix.seed.enabled=false.
@Configuration
@ConditionalOnProperty(name = "pix.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DadosIniciais {

    private static final Logger log = LoggerFactory.getLogger(DadosIniciais.class);

    @Bean
    CommandLineRunner carregarContasDeExemplo(ContaRepository repository, ContaService contas) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }
            // Pelo service (e não direto no repositório) para gerar o lançamento de abertura no razão.
            List.of(
                    new Conta("Ana Souza", "11111111111", "ana@pix.com", new BigDecimal("1000.00")),
                    new Conta("Bruno Lima", "22222222222", "bruno@pix.com", new BigDecimal("250.00")),
                    new Conta("Carla Dias", "33333333333", "+5511999990000", new BigDecimal("50.00"))
            ).forEach(contas::criar);
            log.info("Contas de exemplo carregadas: ana@pix.com, bruno@pix.com, +5511999990000");
        };
    }
}
