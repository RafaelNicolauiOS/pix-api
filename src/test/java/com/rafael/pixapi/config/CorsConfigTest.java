package com.rafael.pixapi.config;

import com.rafael.pixapi.controller.ContaController;
import com.rafael.pixapi.service.ContaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Simula o "preflight" que o navegador envia antes de um POST vindo de outro domínio.
@WebMvcTest(ContaController.class)
class CorsConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContaService service;

    @Test
    void devePermitir_githubPages() throws Exception {
        mockMvc.perform(options("/contas")
                        .header("Origin", "https://rafaelnicolauios.github.io")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://rafaelnicolauios.github.io"));
    }

    @Test
    void devePermitir_localhostEmQualquerPorta() throws Exception {
        mockMvc.perform(options("/transferencias")
                        .header("Origin", "http://localhost:8000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk());
    }

    @Test
    void devePermitir_cabecalhoDoTokenDeAdmin() throws Exception {
        mockMvc.perform(options("/logs")
                        .header("Origin", "https://rafaelnicolauios.github.io")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "x-admin-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", "x-admin-token"));
    }

    @Test
    void deveBloquear_outrosDominios() throws Exception {
        mockMvc.perform(options("/contas")
                        .header("Origin", "https://site-malicioso.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
