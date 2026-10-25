package com.rafael.pixapi.controller;

import com.rafael.pixapi.controller.dto.TransferenciaRequest;
import com.rafael.pixapi.service.ComprovanteTransferencia;
import com.rafael.pixapi.service.TransferenciaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transferencias")
public class TransferenciaController {

    private final TransferenciaService service;

    public TransferenciaController(TransferenciaService service) {
        this.service = service;
    }

    // Retorna 200 com o comprovante. Os erros viram ProblemDetail no ApiExceptionHandler.
    @PostMapping
    public ComprovanteTransferencia transferir(@Valid @RequestBody TransferenciaRequest request) {
        return service.transferir(request.chaveOrigem(), request.chaveDestino(), request.valor());
    }
}
