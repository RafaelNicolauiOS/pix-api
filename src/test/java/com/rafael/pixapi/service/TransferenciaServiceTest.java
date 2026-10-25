package com.rafael.pixapi.service;

import com.rafael.pixapi.domain.Conta;
import com.rafael.pixapi.domain.SaldoInsuficienteException;
import com.rafael.pixapi.repository.ContaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferenciaServiceTest {

    @Mock
    private ContaRepository repository;

    @InjectMocks
    private TransferenciaService service;

    private final Conta ana = new Conta("Ana", "11111111111", "ana@pix.com", new BigDecimal("100.00"));
    private final Conta bruno = new Conta("Bruno", "22222222222", "bruno@pix.com", new BigDecimal("50.00"));

    @Test
    void transferir_deveDebitarOrigemECreditarDestino() {
        when(repository.findComLockByChavePix("ana@pix.com")).thenReturn(Optional.of(ana));
        when(repository.findComLockByChavePix("bruno@pix.com")).thenReturn(Optional.of(bruno));

        ComprovanteTransferencia comprovante =
                service.transferir("ana@pix.com", "bruno@pix.com", new BigDecimal("30.00"));

        assertThat(ana.getSaldo()).isEqualByComparingTo("70.00");
        assertThat(bruno.getSaldo()).isEqualByComparingTo("80.00");
        assertThat(comprovante.valor()).isEqualByComparingTo("30.00");
        assertThat(comprovante.saldoOrigem()).isEqualByComparingTo("70.00");
        assertThat(comprovante.realizadaEm()).isNotNull();
    }

    @Test
    void transferir_deveLancarExcecaoSemAlterarSaldos_quandoSaldoInsuficiente() {
        when(repository.findComLockByChavePix("ana@pix.com")).thenReturn(Optional.of(ana));
        when(repository.findComLockByChavePix("bruno@pix.com")).thenReturn(Optional.of(bruno));

        assertThatThrownBy(() -> service.transferir("ana@pix.com", "bruno@pix.com", new BigDecimal("100.01")))
                .isInstanceOf(SaldoInsuficienteException.class);
        assertThat(ana.getSaldo()).isEqualByComparingTo("100.00");
        assertThat(bruno.getSaldo()).isEqualByComparingTo("50.00");
    }

    @Test
    void transferir_deveLancar404_quandoChaveOrigemNaoExiste() {
        when(repository.findComLockByChavePix("bruno@pix.com")).thenReturn(Optional.of(bruno));
        when(repository.findComLockByChavePix("zzz@pix.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transferir("zzz@pix.com", "bruno@pix.com", BigDecimal.ONE))
                .isInstanceOf(ChavePixNaoEncontradaException.class)
                .hasMessageContaining("zzz@pix.com");
        assertThat(bruno.getSaldo()).isEqualByComparingTo("50.00");
    }

    @Test
    void transferir_deveLancar404_quandoChaveDestinoNaoExiste() {
        when(repository.findComLockByChavePix("ana@pix.com")).thenReturn(Optional.of(ana));
        when(repository.findComLockByChavePix("zzz@pix.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transferir("ana@pix.com", "zzz@pix.com", BigDecimal.ONE))
                .isInstanceOf(ChavePixNaoEncontradaException.class)
                .hasMessageContaining("zzz@pix.com");
        assertThat(ana.getSaldo()).isEqualByComparingTo("100.00");
    }

    @Test
    void transferir_deveRecusar_quandoOrigemIgualDestino() {
        assertThatThrownBy(() -> service.transferir("ana@pix.com", "ana@pix.com", BigDecimal.ONE))
                .isInstanceOf(TransferenciaInvalidaException.class);
        verify(repository, never()).findComLockByChavePix(anyString());
    }
}
