package com.rafael.pixapi.controller;

import com.rafael.pixapi.batch.ExportacaoMovimentoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/batch")
public class BatchController {

    private final ExportacaoMovimentoService exportacao;

    public BatchController(ExportacaoMovimentoService exportacao) {
        this.exportacao = exportacao;
    }

    // Arquivo texto de largura fixa para o batch noturno. Sem "data", exporta o dia anterior
    // (o job roda de madrugada e fecha o dia que acabou).
    @GetMapping(value = "/movimento", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> movimento(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        LocalDate referencia = data != null ? data : LocalDate.now(ExportacaoMovimentoService.FUSO).minusDays(1);
        String arquivo = String.join("\n", exportacao.exportar(referencia)) + "\n";
        String nome = "MOVIMENTO.D" + referencia.toString().replace("-", "");
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.US_ASCII))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nome + "\"")
                .body(arquivo);
    }
}
