package com.rafael.pixapi.service;

import com.rafael.pixapi.domain.Conta;
import com.rafael.pixapi.domain.Lancamento;
import com.rafael.pixapi.repository.ContaRepository;
import com.rafael.pixapi.repository.LancamentoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// @Service registra a classe no container do Spring para ser injetada no controller.
@Service
public class ContaService {

    private final ContaRepository repository;
    private final LancamentoRepository lancamentos;

    // Injeção de dependência pelo construtor (como passar a dependência no init em Swift).
    public ContaService(ContaRepository repository, LancamentoRepository lancamentos) {
        this.repository = repository;
        this.lancamentos = lancamentos;
    }

    @Transactional
    public Conta criar(Conta conta) {
        if (repository.existsByChavePix(conta.getChavePix())) {
            throw new ChavePixDuplicadaException(conta.getChavePix());
        }
        Conta criada = repository.save(conta);
        // Mesma transação: ou a conta e a abertura no razão são gravadas juntas, ou nada é.
        lancamentos.save(Lancamento.abertura(criada.getId(), criada.getSaldo(), Instant.now()));
        return criada;
    }

    @Transactional(readOnly = true)
    public Conta buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ContaNaoEncontradaException(id));
    }

    @Transactional(readOnly = true)
    public List<Conta> listar() {
        // Sem ORDER BY o banco devolve as linhas em qualquer ordem (o Postgres põe as alteradas no fim).
        return repository.findAll(Sort.by("id"));
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
        Conta conta = buscarPorId(id);
        // O saldo restante "sai" no encerramento, zerando a conta no razão (o histórico fica).
        if (conta.getSaldo().compareTo(BigDecimal.ZERO) > 0) {
            lancamentos.save(Lancamento.encerramento(id, conta.getSaldo(), Instant.now()));
        }
        repository.delete(conta);
    }
}
