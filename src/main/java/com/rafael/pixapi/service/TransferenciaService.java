package com.rafael.pixapi.service;

import com.rafael.pixapi.domain.Conta;
import com.rafael.pixapi.repository.ContaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class TransferenciaService {

    private final ContaRepository repository;
    private final ApplicationEventPublisher eventos;

    // ApplicationEventPublisher é o "NotificationCenter" interno do Spring: o service só avisa
    // que algo aconteceu, sem conhecer quem vai reagir (listener, Kafka, log...).
    public TransferenciaService(ContaRepository repository, ApplicationEventPublisher eventos) {
        this.repository = repository;
        this.eventos = eventos;
    }

    // @Transactional: tudo aqui dentro é "tudo ou nada". Se qualquer exceção (unchecked)
    // for lançada, o Spring faz rollback e nenhum saldo muda no banco.
    // Não há chamada a save(): o JPA detecta que os objetos mudaram ("dirty checking")
    // e grava os UPDATEs sozinho no commit.
    @Transactional
    public ComprovanteTransferencia transferir(String chaveOrigem, String chaveDestino, BigDecimal valor) {
        if (chaveOrigem.equals(chaveDestino)) {
            throw new TransferenciaInvalidaException("Chave de origem e de destino devem ser diferentes");
        }

        // As contas são travadas sempre na mesma ordem (alfabética). Assim, duas transferências
        // simultâneas A→B e B→A não ficam esperando uma pela outra para sempre (deadlock).
        Conta origem;
        Conta destino;
        if (chaveOrigem.compareTo(chaveDestino) < 0) {
            origem = buscarComLock(chaveOrigem);
            destino = buscarComLock(chaveDestino);
        } else {
            destino = buscarComLock(chaveDestino);
            origem = buscarComLock(chaveOrigem);
        }

        origem.debitar(valor);   // lança SaldoInsuficienteException se não houver saldo
        destino.creditar(valor);

        Instant agora = Instant.now();
        // Publicado DENTRO da transação: o listener (AFTER_COMMIT) só o repassa se o commit der certo.
        eventos.publishEvent(new TransferenciaRealizadaEvent(UUID.randomUUID(), chaveOrigem, chaveDestino, valor, agora));

        return new ComprovanteTransferencia(chaveOrigem, chaveDestino, valor, origem.getSaldo(), agora);
    }

    private Conta buscarComLock(String chavePix) {
        return repository.findComLockByChavePix(chavePix)
                .orElseThrow(() -> new ChavePixNaoEncontradaException(chavePix));
    }
}
