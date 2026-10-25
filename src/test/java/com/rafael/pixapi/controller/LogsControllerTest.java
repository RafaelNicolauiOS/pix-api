package com.rafael.pixapi.controller;

import com.rafael.pixapi.monitoramento.LinhaDeLog;
import com.rafael.pixapi.monitoramento.LogsEmMemoria;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LogsControllerTest {

    // @Nested agrupa cenários com configurações diferentes (como "describe" em outros frameworks).
    @Nested
    @WebMvcTest(LogsController.class)
    @TestPropertySource(properties = "pix.admin.token=segredo-de-teste")
    class ComTokenConfigurado {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private LogsEmMemoria logs;

        @Test
        void semToken_deveRetornar401() throws Exception {
            mockMvc.perform(get("/logs"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Token inválido"));
            verify(logs, never()).ultimas(anyInt());
        }

        @Test
        void comTokenErrado_deveRetornar401() throws Exception {
            mockMvc.perform(get("/logs").header("X-Admin-Token", "chute"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void comTokenCorreto_deveRetornarAsLinhas() throws Exception {
            when(logs.ultimas(200)).thenReturn(List.of(
                    new LinhaDeLog(Instant.parse("2026-10-25T12:00:00Z"), "INFO", "PixApiApplication", "Started")));

            mockMvc.perform(get("/logs").header("X-Admin-Token", "segredo-de-teste"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].nivel").value("INFO"))
                    .andExpect(jsonPath("$[0].logger").value("PixApiApplication"))
                    .andExpect(jsonPath("$[0].mensagem").value("Started"))
                    .andExpect(jsonPath("$[0].instante").value("2026-10-25T12:00:00Z"));
        }

        @Test
        void deveRespeitarOLimite_eNuncaPassarDe500() throws Exception {
            mockMvc.perform(get("/logs?limite=50").header("X-Admin-Token", "segredo-de-teste"))
                    .andExpect(status().isOk());
            verify(logs).ultimas(50);

            mockMvc.perform(get("/logs?limite=99999").header("X-Admin-Token", "segredo-de-teste"))
                    .andExpect(status().isOk());
            verify(logs).ultimas(500);
        }
    }

    @Nested
    @WebMvcTest(LogsController.class)
    @TestPropertySource(properties = "pix.admin.token=")
    class SemTokenConfigurado {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private LogsEmMemoria logs;

        @Test
        void endpointDeveFicarDesativado() throws Exception {
            mockMvc.perform(get("/logs").header("X-Admin-Token", ""))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Logs desativados"));
            verify(logs, never()).ultimas(anyInt());
        }
    }
}
