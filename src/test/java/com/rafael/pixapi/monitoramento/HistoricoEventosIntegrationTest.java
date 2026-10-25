package com.rafael.pixapi.monitoramento;

import com.rafael.pixapi.domain.Conta;
import com.rafael.pixapi.repository.ContaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Ponta a ponta: a transferência confirmada aparece em GET /eventos; a que falhou, não.
@SpringBootTest
@AutoConfigureMockMvc
class HistoricoEventosIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContaRepository repository;

    private String criarConta(String saldo) {
        String chave = "hist-" + UUID.randomUUID() + "@pix.com";
        repository.save(new Conta("Historico", "12345678901", chave, new BigDecimal(saldo)));
        return chave;
    }

    private void transferir(String origem, String destino, String valor, int status) throws Exception {
        mockMvc.perform(post("/transferencias").contentType(MediaType.APPLICATION_JSON).content("""
                        {"chaveOrigem": "%s", "chaveDestino": "%s", "valor": %s}
                        """.formatted(origem, destino, valor)))
                .andExpect(status().is(status));
    }

    @Test
    void transferenciaConfirmada_deveAparecerNoFeedDeEventos() throws Exception {
        String origem = criarConta("50.00");
        String destino = criarConta("0.00");

        transferir(origem, destino, "20.00", 200);

        mockMvc.perform(get("/eventos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].chaveOrigem").value(origem))
                .andExpect(jsonPath("$[0].chaveDestino").value(destino))
                .andExpect(jsonPath("$[0].valor").value(20.00))
                .andExpect(jsonPath("$[0].id").exists());
    }

    @Test
    void transferenciaRecusada_naoDeveAparecerNoFeed() throws Exception {
        String origem = criarConta("5.00");
        String destino = criarConta("0.00");

        transferir(origem, destino, "9.00", 422);

        mockMvc.perform(get("/eventos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].chaveOrigem", not(hasItem(origem))));
    }
}
