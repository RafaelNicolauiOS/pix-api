package com.rafael.pixapi.controller;

import com.rafael.pixapi.domain.Conta;
import com.rafael.pixapi.service.ChavePixDuplicadaException;
import com.rafael.pixapi.service.ContaNaoEncontradaException;
import com.rafael.pixapi.service.ContaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Sobe apenas a camada web (controller + JSON + validação), sem banco.
@WebMvcTest(ContaController.class)
class ContaControllerTest {

    @Autowired
    private MockMvc mockMvc; // simula requisições HTTP sem abrir porta

    @MockitoBean
    private ContaService service; // o Spring injeta este mock no controller

    private static final String JSON_VALIDO = """
            {"titular": "Rafael Nicolau", "cpf": "12345678901",
             "chavePix": "rafael@email.com", "saldo": 100.00}
            """;

    private Conta contaComId(Long id) {
        Conta conta = new Conta("Rafael Nicolau", "12345678901", "rafael@email.com", new BigDecimal("100.00"));
        ReflectionTestUtils.setField(conta, "id", id); // id normalmente é gerado pelo banco
        return conta;
    }

    @Test
    void post_deveCriarConta_eRetornar201ComLocation() throws Exception {
        when(service.criar(any(Conta.class))).thenReturn(contaComId(1L));

        mockMvc.perform(post("/contas").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/contas/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.titular").value("Rafael Nicolau"));
    }

    @Test
    void post_deveRetornar400_quandoDadosInvalidos() throws Exception {
        String invalido = """
                {"titular": "", "cpf": "123", "chavePix": "", "saldo": -5}
                """;

        mockMvc.perform(post("/contas").contentType(MediaType.APPLICATION_JSON).content(invalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.titular").exists())
                .andExpect(jsonPath("$.erros.cpf").exists())
                .andExpect(jsonPath("$.erros.saldo").exists());
        verify(service, never()).criar(any());
    }

    @Test
    void post_deveRetornar409_quandoChavePixDuplicada() throws Exception {
        when(service.criar(any(Conta.class))).thenThrow(new ChavePixDuplicadaException("rafael@email.com"));

        mockMvc.perform(post("/contas").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict());
    }

    @Test
    void get_deveListarContas() throws Exception {
        when(service.listar()).thenReturn(List.of(contaComId(1L), contaComId(2L)));

        mockMvc.perform(get("/contas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getPorId_deveRetornarConta() throws Exception {
        when(service.buscarPorId(1L)).thenReturn(contaComId(1L));

        mockMvc.perform(get("/contas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chavePix").value("rafael@email.com"));
    }

    @Test
    void getPorId_deveRetornar404_quandoNaoExiste() throws Exception {
        when(service.buscarPorId(99L)).thenThrow(new ContaNaoEncontradaException(99L));

        mockMvc.perform(get("/contas/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void put_deveAtualizarConta() throws Exception {
        when(service.atualizar(eq(1L), any(Conta.class))).thenReturn(contaComId(1L));

        mockMvc.perform(put("/contas/1").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void delete_deveRetornar204() throws Exception {
        mockMvc.perform(delete("/contas/1"))
                .andExpect(status().isNoContent());
        verify(service).remover(1L);
    }

    @Test
    void delete_deveRetornar404_quandoNaoExiste() throws Exception {
        doThrow(new ContaNaoEncontradaException(99L)).when(service).remover(99L);

        mockMvc.perform(delete("/contas/99"))
                .andExpect(status().isNotFound());
    }
}
