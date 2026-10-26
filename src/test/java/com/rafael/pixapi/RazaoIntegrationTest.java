package com.rafael.pixapi;

import com.rafael.pixapi.domain.Conta;
import com.rafael.pixapi.domain.Lancamento;
import com.rafael.pixapi.repository.ContaRepository;
import com.rafael.pixapi.repository.LancamentoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// A invariante que o batch noturno em COBOL confere: soma do razão de uma conta == saldo dela.
@SpringBootTest
@AutoConfigureMockMvc
class RazaoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContaRepository contas;

    @Autowired
    private LancamentoRepository lancamentos;

    private Long criarConta(String chave, String saldo) throws Exception {
        mockMvc.perform(post("/contas").contentType(MediaType.APPLICATION_JSON).content("""
                        {"titular": "Razao", "cpf": "12345678901", "chavePix": "%s", "saldo": %s}
                        """.formatted(chave, saldo)))
                .andExpect(status().isCreated());
        return contas.findAll().stream().filter(c -> c.getChavePix().equals(chave)).findFirst().orElseThrow().getId();
    }

    private void transferir(String origem, String destino, String valor, int status) throws Exception {
        mockMvc.perform(post("/transferencias").contentType(MediaType.APPLICATION_JSON).content("""
                        {"chaveOrigem": "%s", "chaveDestino": "%s", "valor": %s}
                        """.formatted(origem, destino, valor)))
                .andExpect(status().is(status));
    }

    // Créditos somam, débitos subtraem.
    private BigDecimal somaDoRazao(Long contaId) {
        return lancamentos.findAll().stream()
                .filter(l -> l.getContaId().equals(contaId))
                .map(Lancamento::valorComSinal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal saldo(Long contaId) {
        return contas.findById(contaId).map(Conta::getSaldo).orElseThrow();
    }

    @Test
    void razaoDeveFecharComOSaldo_aposAberturaTransferenciasERecusa() throws Exception {
        String a = "razao-" + UUID.randomUUID() + "@pix.com";
        String b = "razao-" + UUID.randomUUID() + "@pix.com";
        Long idA = criarConta(a, "300.00");
        Long idB = criarConta(b, "20.00");

        transferir(a, b, "120.50", 200);
        transferir(b, a, "40.00", 200);
        transferir(b, a, "999.00", 422); // recusada: não pode gerar lançamento

        assertThat(saldo(idA)).isEqualByComparingTo("219.50");
        assertThat(somaDoRazao(idA)).isEqualByComparingTo(saldo(idA));
        assertThat(saldo(idB)).isEqualByComparingTo("100.50");
        assertThat(somaDoRazao(idB)).isEqualByComparingTo(saldo(idB));
    }

    @Test
    void contaEncerrada_deveTerRazaoZerado() throws Exception {
        String chave = "razao-" + UUID.randomUUID() + "@pix.com";
        Long id = criarConta(chave, "75.00");

        mockMvc.perform(delete("/contas/" + id)).andExpect(status().isNoContent());

        assertThat(contas.findById(id)).isEmpty();
        assertThat(somaDoRazao(id)).isEqualByComparingTo("0");
    }

    @Test
    void contasDeExemplo_devemTerLancamentoDeAbertura() {
        Conta ana = contas.findAll().stream().filter(c -> c.getChavePix().equals("ana@pix.com")).findFirst().orElseThrow();

        assertThat(somaDoRazao(ana.getId())).isEqualByComparingTo(ana.getSaldo());
    }
}
