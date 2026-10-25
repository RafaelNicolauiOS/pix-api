package com.rafael.pixapi.controller;

import com.rafael.pixapi.monitoramento.LogsEmMemoria;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

// Últimas linhas de log do servidor. Logs revelam detalhes internos (erros, endereços, chaves Pix),
// então o endpoint exige o cabeçalho X-Admin-Token igual à propriedade pix.admin.token.
// Sem token configurado, o endpoint fica desativado (404).
@RestController
@RequestMapping("/logs")
public class LogsController {

    private static final int LIMITE_PADRAO = 200;
    private static final int LIMITE_MAXIMO = 500;

    private final LogsEmMemoria logs;
    private final byte[] tokenEsperado;

    public LogsController(LogsEmMemoria logs, @Value("${pix.admin.token:}") String token) {
        this.logs = logs;
        this.tokenEsperado = token.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping
    public ResponseEntity<?> ultimas(@RequestHeader(name = "X-Admin-Token", required = false) String token,
                                     @RequestParam(defaultValue = "" + LIMITE_PADRAO) int limite) {
        if (tokenEsperado.length == 0) {
            return problema(HttpStatus.NOT_FOUND, "Logs desativados",
                    "Defina a variável de ambiente PIX_ADMIN_TOKEN para habilitar este endpoint.");
        }
        if (token == null || !tokenValido(token)) {
            return problema(HttpStatus.UNAUTHORIZED, "Token inválido",
                    "Envie o token de administrador no cabeçalho X-Admin-Token.");
        }
        return ResponseEntity.ok(logs.ultimas(Math.max(1, Math.min(limite, LIMITE_MAXIMO))));
    }

    // MessageDigest.isEqual compara em tempo constante: não "vaza", pela demora da resposta,
    // quantos caracteres do token o atacante acertou (ataque de timing).
    private boolean tokenValido(String token) {
        return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), tokenEsperado);
    }

    private ResponseEntity<ProblemDetail> problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return ResponseEntity.status(status).body(problema);
    }
}
