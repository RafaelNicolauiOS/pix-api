package com.rafael.pixapi;

import com.rafael.pixapi.domain.Conta;
import com.rafael.pixapi.repository.ContaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Teste de integração: sobe a aplicação inteira com o H2 real, sem mocks.
// Aqui provamos que o débito e o crédito foram de fato gravados no banco.
@SpringBootTest
@AutoConfigureMockMvc
class TransferenciaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContaRepository repository;

    private void transferir(String origem, String destino, String valor, int statusEsperado) throws Exception {
        String json = """
                {"chaveOrigem": "%s", "chaveDestino": "%s", "valor": %s}
                """.formatted(origem, destino, valor);
        mockMvc.perform(post("/transferencias").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(statusEsperado));
    }

    private BigDecimal saldoDe(String chave) {
        return repository.findAll().stream()
                .filter(c -> c.getChavePix().equals(chave))
                .findFirst().orElseThrow().getSaldo();
    }

    @Test
    void transferencia_devePersistirDebitoECredito() throws Exception {
        repository.save(new Conta("Origem", "33333333333", "it-origem@pix.com", new BigDecimal("200.00")));
        repository.save(new Conta("Destino", "44444444444", "it-destino@pix.com", new BigDecimal("0.00")));

        transferir("it-origem@pix.com", "it-destino@pix.com", "75.25", 200);

        assertThat(saldoDe("it-origem@pix.com")).isEqualByComparingTo("124.75");
        assertThat(saldoDe("it-destino@pix.com")).isEqualByComparingTo("75.25");
    }

    @Test
    void transferencia_naoDeveAlterarNada_quandoSaldoInsuficiente() throws Exception {
        repository.save(new Conta("Pobre", "55555555555", "it-pobre@pix.com", new BigDecimal("10.00")));
        repository.save(new Conta("Rico", "66666666666", "it-rico@pix.com", new BigDecimal("1000.00")));

        transferir("it-pobre@pix.com", "it-rico@pix.com", "10.01", 422);

        assertThat(saldoDe("it-pobre@pix.com")).isEqualByComparingTo("10.00");
        assertThat(saldoDe("it-rico@pix.com")).isEqualByComparingTo("1000.00");
    }

    @Test
    void contasDeExemplo_devemSerCarregadasAoIniciar() {
        assertThat(repository.existsByChavePix("ana@pix.com")).isTrue();
        assertThat(repository.existsByChavePix("bruno@pix.com")).isTrue();
    }
}
