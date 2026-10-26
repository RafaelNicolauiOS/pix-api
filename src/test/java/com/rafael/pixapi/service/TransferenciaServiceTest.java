package com.rafael.pixapi.service;

import com.rafael.pixapi.domain.Conta;
import com.rafael.pixapi.domain.Lancamento;
import com.rafael.pixapi.domain.TipoLancamento;
import com.rafael.pixapi.domain.SaldoInsuficienteException;
import com.rafael.pixapi.repository.ContaRepository;
import com.rafael.pixapi.repository.LancamentoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferenciaServiceTest {

    @Mock
    private ContaRepository repository;

    @Mock
    private ApplicationEventPublisher eventos; // "NotificationCenter" do Spring

    @Mock
    private LancamentoRepository lancamentos;

    @InjectMocks
    private TransferenciaService service;

    private final Conta ana = new Conta("Ana", "11111111111", "ana@pix.com", new BigDecimal("100.00"));
    private final Conta bruno = new Conta("Bruno", "22222222222", "bruno@pix.com", new BigDecimal("50.00"));

    {
        // Bloco de inicialização: roda a cada teste, como um setUp. Dá ids às contas.
        ReflectionTestUtils.setField(ana, "id", 1L);
        ReflectionTestUtils.setField(bruno, "id", 2L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void transferir_deveRegistrarDebitoECreditoNoRazao_comOIdDoEvento() {
        when(repository.findComLockByChavePix("ana@pix.com")).thenReturn(Optional.of(ana));
        when(repository.findComLockByChavePix("bruno@pix.com")).thenReturn(Optional.of(bruno));

        service.transferir("ana@pix.com", "bruno@pix.com", new BigDecimal("30.00"));

        ArgumentCaptor<TransferenciaRealizadaEvent> evento = ArgumentCaptor.forClass(TransferenciaRealizadaEvent.class);
        verify(eventos).publishEvent(evento.capture());
        ArgumentCaptor<List<Lancamento>> captor = ArgumentCaptor.forClass(List.class);
        verify(lancamentos).saveAll(captor.capture());

        List<Lancamento> registrados = captor.getValue();
        assertThat(registrados).hasSize(2);
        Lancamento debito = registrados.get(0);
        Lancamento credito = registrados.get(1);
        assertThat(debito.getContaId()).isEqualTo(1L);
        assertThat(debito.getTipo()).isEqualTo(TipoLancamento.PIX_ENVIADO);
        assertThat(credito.getContaId()).isEqualTo(2L);
        assertThat(credito.getTipo()).isEqualTo(TipoLancamento.PIX_RECEBIDO);
        assertThat(registrados).allSatisfy(l -> {
            assertThat(l.getValor()).isEqualByComparingTo("30.00");
            assertThat(l.getTransferenciaId()).isEqualTo(evento.getValue().id());
            assertThat(l.getRealizadoEm()).isEqualTo(evento.getValue().realizadaEm());
        });
    }

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
    void transferir_devePublicarEventoTransferenciaRealizada() {
        when(repository.findComLockByChavePix("ana@pix.com")).thenReturn(Optional.of(ana));
        when(repository.findComLockByChavePix("bruno@pix.com")).thenReturn(Optional.of(bruno));

        ComprovanteTransferencia comprovante =
                service.transferir("ana@pix.com", "bruno@pix.com", new BigDecimal("30.00"));

        // ArgumentCaptor "captura" o objeto passado ao mock para inspecionarmos depois.
        ArgumentCaptor<TransferenciaRealizadaEvent> captor = ArgumentCaptor.forClass(TransferenciaRealizadaEvent.class);
        verify(eventos).publishEvent(captor.capture());
        TransferenciaRealizadaEvent evento = captor.getValue();
        assertThat(evento.id()).isNotNull();
        assertThat(evento.chaveOrigem()).isEqualTo("ana@pix.com");
        assertThat(evento.chaveDestino()).isEqualTo("bruno@pix.com");
        assertThat(evento.valor()).isEqualByComparingTo("30.00");
        assertThat(evento.realizadaEm()).isEqualTo(comprovante.realizadaEm());
    }

    @Test
    void transferir_deveLancarExcecaoSemAlterarSaldos_quandoSaldoInsuficiente() {
        when(repository.findComLockByChavePix("ana@pix.com")).thenReturn(Optional.of(ana));
        when(repository.findComLockByChavePix("bruno@pix.com")).thenReturn(Optional.of(bruno));

        assertThatThrownBy(() -> service.transferir("ana@pix.com", "bruno@pix.com", new BigDecimal("100.01")))
                .isInstanceOf(SaldoInsuficienteException.class);
        assertThat(ana.getSaldo()).isEqualByComparingTo("100.00");
        assertThat(bruno.getSaldo()).isEqualByComparingTo("50.00");
        verify(eventos, never()).publishEvent(any());
        verify(lancamentos, never()).saveAll(any());
    }

    @Test
    void transferir_deveLancar404_quandoChaveOrigemNaoExiste() {
        when(repository.findComLockByChavePix("bruno@pix.com")).thenReturn(Optional.of(bruno));
        when(repository.findComLockByChavePix("zzz@pix.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transferir("zzz@pix.com", "bruno@pix.com", BigDecimal.ONE))
                .isInstanceOf(ChavePixNaoEncontradaException.class)
                .hasMessageContaining("zzz@pix.com");
        assertThat(bruno.getSaldo()).isEqualByComparingTo("50.00");
        verify(eventos, never()).publishEvent(any());
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
        verify(eventos, never()).publishEvent(any());
    }
}
