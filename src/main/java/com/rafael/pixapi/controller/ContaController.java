package com.rafael.pixapi.controller;

import com.rafael.pixapi.controller.dto.ContaRequest;
import com.rafael.pixapi.controller.dto.ContaResponse;
import com.rafael.pixapi.domain.Conta;
import com.rafael.pixapi.service.ContaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

// @RestController: cada método responde JSON. @RequestMapping define o prefixo das rotas.
@RestController
@RequestMapping("/contas")
public class ContaController {

    private final ContaService service;

    public ContaController(ContaService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ContaResponse> criar(@Valid @RequestBody ContaRequest request) {
        Conta criada = service.criar(request.toEntity());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criada.getId()).toUri();
        return ResponseEntity.created(location).body(ContaResponse.from(criada));
    }

    @GetMapping
    public List<ContaResponse> listar() {
        return service.listar().stream().map(ContaResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ContaResponse buscar(@PathVariable Long id) {
        return ContaResponse.from(service.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ContaResponse atualizar(@PathVariable Long id, @Valid @RequestBody ContaRequest request) {
        return ContaResponse.from(service.atualizar(id, request.toEntity()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable Long id) {
        service.remover(id);
        return ResponseEntity.noContent().build();
    }
}
