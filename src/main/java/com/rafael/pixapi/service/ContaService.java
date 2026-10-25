package com.rafael.pixapi.service;

import com.rafael.pixapi.domain.Conta;
import com.rafael.pixapi.repository.ContaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// @Service registra a classe no container do Spring para ser injetada no controller.
@Service
public class ContaService {

    private final ContaRepository repository;

    // Injeção de dependência pelo construtor (como passar a dependência no init em Swift).
    public ContaService(ContaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Conta criar(Conta conta) {
        if (repository.existsByChavePix(conta.getChavePix())) {
            throw new ChavePixDuplicadaException(conta.getChavePix());
        }
        return repository.save(conta);
    }

    @Transactional(readOnly = true)
    public Conta buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ContaNaoEncontradaException(id));
    }

    @Transactional(readOnly = true)
    public List<Conta> listar() {
        return repository.findAll();
    }

    @Transactional
    public Conta atualizar(Long id, Conta dados) {
        Conta conta = buscarPorId(id);
        boolean trocouChave = !conta.getChavePix().equals(dados.getChavePix());
        if (trocouChave && repository.existsByChavePix(dados.getChavePix())) {
            throw new ChavePixDuplicadaException(dados.getChavePix());
        }
        conta.atualizarDados(dados.getTitular(), dados.getChavePix());
        return repository.save(conta);
    }

    @Transactional
    public void remover(Long id) {
        if (!repository.existsById(id)) {
            throw new ContaNaoEncontradaException(id);
        }
        repository.deleteById(id);
    }
}
