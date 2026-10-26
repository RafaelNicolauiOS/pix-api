package com.rafael.pixapi.batch;

import com.rafael.pixapi.domain.Conta;
import com.rafael.pixapi.domain.Lancamento;
import com.rafael.pixapi.repository.ContaRepository;
import com.rafael.pixapi.repository.LancamentoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

// Gera o arquivo de movimento que o batch noturno em COBOL concilia.
@Service
public class ExportacaoMovimentoService {

    // O "dia" de um banco brasileiro é o de Brasília, não o UTC do servidor.
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final ContaRepository contas;
    private final LancamentoRepository lancamentos;

    public ExportacaoMovimentoService(ContaRepository contas, LancamentoRepository lancamentos) {
        this.contas = contas;
        this.lancamentos = lancamentos;
    }

    // REPEATABLE_READ: saldos e razão saem da MESMA "foto" do banco. Sem isso, um Pix feito
    // entre as duas leituras apareceria no saldo mas não no razão, e o batch acusaria uma
    // divergência que não existe.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<String> exportar(LocalDate dataReferencia) {
        if (dataReferencia.isAfter(LocalDate.now(FUSO))) {
            throw new DataDeReferenciaInvalidaException("A data de referência não pode estar no futuro: " + dataReferencia);
        }
        Instant corte = Instant.now();

        List<String> registros = new ArrayList<>();
        registros.add(LayoutMovimento.header(dataReferencia, LocalDateTime.ofInstant(corte, FUSO)));

        List<Conta> todasAsContas = contas.findAll(Sort.by("id"));
        for (Conta c : todasAsContas) {
            registros.add(LayoutMovimento.conta(c.getId(), c.getChavePix(), c.getTitular(), c.getSaldo()));
        }

        // O razão inteiro até o corte: o COBOL separa o que é anterior ao dia (saldo de abertura),
        // do dia (movimento) e posterior (entre a meia-noite e a hora da extração).
        BigDecimal creditos = BigDecimal.ZERO;
        BigDecimal debitos = BigDecimal.ZERO;
        List<Lancamento> razao = lancamentos.findByRealizadoEmLessThanEqualOrderByRealizadoEmAscIdAsc(corte);
        for (Lancamento l : razao) {
            boolean credito = l.getTipo().isCredito();
            registros.add(LayoutMovimento.lancamento(l.getContaId(), LocalDateTime.ofInstant(l.getRealizadoEm(), FUSO),
                    credito, l.getTipo().name(), l.getValor(), l.getTransferenciaId()));
            if (credito) {
                creditos = creditos.add(l.getValor());
            } else {
                debitos = debitos.add(l.getValor());
            }
        }

        registros.add(LayoutMovimento.trailer(todasAsContas.size(), razao.size(), creditos, debitos));
        return registros;
    }
}
