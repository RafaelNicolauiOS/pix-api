package com.rafael.pixapi.controller;

import com.rafael.pixapi.domain.SaldoInsuficienteException;
import com.rafael.pixapi.service.ChavePixNaoEncontradaException;
import com.rafael.pixapi.service.ComprovanteTransferencia;
import com.rafael.pixapi.service.TransferenciaInvalidaException;
import com.rafael.pixapi.service.TransferenciaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferenciaController.class)
class TransferenciaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferenciaService service;

    private static final String JSON = """
            {"chaveOrigem": "ana@pix.com", "chaveDestino": "bruno@pix.com", "valor": 30.00}
            """;
    private static final BigDecimal TRINTA = new BigDecimal("30.00");

    @Test
    void post_deveRetornar200ComComprovante() throws Exception {
        when(service.transferir("ana@pix.com", "bruno@pix.com", TRINTA)).thenReturn(
                new ComprovanteTransferencia("ana@pix.com", "bruno@pix.com", TRINTA,
                        new BigDecimal("70.00"), Instant.parse("2026-10-25T12:00:00Z")));

        mockMvc.perform(post("/transferencias").contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(30.00))
                .andExpect(jsonPath("$.saldoOrigem").value(70.00))
                .andExpect(jsonPath("$.realizadaEm").value("2026-10-25T12:00:00Z"));
    }

    @Test
    void post_deveRetornar422ProblemDetail_quandoSaldoInsuficiente() throws Exception {
        when(service.transferir(anyString(), anyString(), any()))
                .thenThrow(new SaldoInsuficienteException("ana@pix.com", TRINTA));

        mockMvc.perform(post("/transferencias").contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Saldo insuficiente"))
                .andExpect(jsonPath("$.instance").value("/transferencias"));
    }

    @Test
    void post_deveRetornar404ProblemDetail_quandoChaveNaoEncontrada() throws Exception {
        when(service.transferir(anyString(), anyString(), any()))
                .thenThrow(new ChavePixNaoEncontradaException("bruno@pix.com"));

        mockMvc.perform(post("/transferencias").contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Chave Pix não encontrada"))
                .andExpect(jsonPath("$.detail").value("Chave Pix não encontrada: bruno@pix.com"));
    }

    @Test
    void post_deveRetornar422_quandoOrigemIgualDestino() throws Exception {
        when(service.transferir(anyString(), anyString(), any()))
                .thenThrow(new TransferenciaInvalidaException("Origem e destino devem ser diferentes"));

        mockMvc.perform(post("/transferencias").contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void post_deveRetornar400_quandoValorInvalido() throws Exception {
        String invalido = """
                {"chaveOrigem": "", "chaveDestino": "bruno@pix.com", "valor": 0.001}
                """;

        mockMvc.perform(post("/transferencias").contentType(MediaType.APPLICATION_JSON).content(invalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.chaveOrigem").exists())
                .andExpect(jsonPath("$.erros.valor").exists());
        verify(service, never()).transferir(anyString(), anyString(), any());
    }
}
