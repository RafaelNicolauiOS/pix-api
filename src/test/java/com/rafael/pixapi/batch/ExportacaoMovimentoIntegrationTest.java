package com.rafael.pixapi.batch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExportacaoMovimentoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final LocalDate HOJE = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    private void criarConta(String chave, String saldo) throws Exception {
        mockMvc.perform(post("/contas").contentType(MediaType.APPLICATION_JSON).content("""
                {"titular": "Exportação", "cpf": "12345678901", "chavePix": "%s", "saldo": %s}
                """.formatted(chave, saldo))).andExpect(status().isCreated());
    }

    private List<String> exportar(LocalDate data) throws Exception {
        String corpo = mockMvc.perform(get("/batch/movimento").param("data", data.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/plain")))
                .andReturn().getResponse().getContentAsString();
        return Arrays.asList(corpo.split("\n"));
    }

    private static BigDecimal centavos(String digitos) {
        return new BigDecimal(digitos).movePointLeft(2);
    }

    @Test
    void arquivo_deveTerHeaderContasLancamentosETrailerConsistentes() throws Exception {
        String a = "exp-" + UUID.randomUUID().toString().substring(0, 8) + "@pix.com";
        String b = "exp-" + UUID.randomUUID().toString().substring(0, 8) + "@pix.com";
        criarConta(a, "100.00");
        criarConta(b, "0.00");
        mockMvc.perform(post("/transferencias").contentType(MediaType.APPLICATION_JSON).content("""
                {"chaveOrigem": "%s", "chaveDestino": "%s", "valor": 33.33}
                """.formatted(a, b))).andExpect(status().isOk());

        List<String> linhas = exportar(HOJE);

        assertThat(linhas).allSatisfy(l -> assertThat(l).hasSize(150).matches("\\p{ASCII}+"));
        assertThat(linhas.get(0)).startsWith("H" + HOJE.toString().replace("-", ""));
        String trailer = linhas.get(linhas.size() - 1);
        assertThat(trailer).startsWith("T");

        List<String> contas = linhas.stream().filter(l -> l.startsWith("C")).toList();
        List<String> lancamentos = linhas.stream().filter(l -> l.startsWith("L")).toList();
        assertThat(Integer.parseInt(trailer.substring(1, 10))).isEqualTo(contas.size());
        assertThat(Integer.parseInt(trailer.substring(10, 19))).isEqualTo(lancamentos.size());

        BigDecimal creditos = lancamentos.stream().filter(l -> l.charAt(25) == 'C')
                .map(l -> centavos(l.substring(38, 53))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal debitos = lancamentos.stream().filter(l -> l.charAt(25) == 'D')
                .map(l -> centavos(l.substring(38, 53))).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(centavos(trailer.substring(19, 36))).isEqualByComparingTo(creditos);
        assertThat(centavos(trailer.substring(36, 53))).isEqualByComparingTo(debitos);

        assertThat(contas).anyMatch(l -> l.contains(a) && l.substring(118, 133).equals("000000000006667"));
        assertThat(lancamentos).anyMatch(l -> l.contains("PIX_ENVIADO") && l.substring(38, 53).equals("000000000003333"));
    }

    @Test
    void dataFutura_deveSerRecusada() throws Exception {
        mockMvc.perform(get("/batch/movimento").param("data", HOJE.plusDays(1).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dataInvalida_deveSerRecusada() throws Exception {
        mockMvc.perform(get("/batch/movimento").param("data", "26/10/2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void semData_deveUsarOntem() throws Exception {
        String corpo = mockMvc.perform(get("/batch/movimento"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).startsWith("H" + HOJE.minusDays(1).toString().replace("-", ""));
    }
}
