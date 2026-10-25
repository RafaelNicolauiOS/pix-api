package com.rafael.pixapi.service;

import com.rafael.pixapi.domain.Conta;
import com.rafael.pixapi.repository.ContaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Liga o Mockito ao JUnit 5: ele cria os @Mock e injeta no @InjectMocks.
@ExtendWith(MockitoExtension.class)
class ContaServiceTest {

    @Mock
    private ContaRepository repository; // "dublê" do banco: não acessa H2

    @InjectMocks
    private ContaService service; // objeto real, recebendo o mock no construtor

    private Conta novaConta() {
        return new Conta("Rafael Nicolau", "12345678901", "rafael@email.com", new BigDecimal("100.00"));
    }

    @Test
    void criar_devePersistirConta_quandoChavePixAindaNaoExiste() {
        Conta conta = novaConta();
        when(repository.existsByChavePix("rafael@email.com")).thenReturn(false);
        when(repository.save(conta)).thenReturn(conta);

        Conta criada = service.criar(conta);

        assertThat(criada.getTitular()).isEqualTo("Rafael Nicolau");
        verify(repository).save(conta);
    }

    @Test
    void criar_deveLancarExcecao_quandoChavePixJaExiste() {
        when(repository.existsByChavePix("rafael@email.com")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(novaConta()))
                .isInstanceOf(ChavePixDuplicadaException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void buscarPorId_deveRetornarConta_quandoExiste() {
        Conta conta = novaConta();
        when(repository.findById(1L)).thenReturn(Optional.of(conta));

        assertThat(service.buscarPorId(1L)).isSameAs(conta);
    }

    @Test
    void buscarPorId_deveLancarExcecao_quandoNaoExiste() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(99L))
                .isInstanceOf(ContaNaoEncontradaException.class)
                .hasMessageContaining("99");
    }

    @Test
    void listar_deveRetornarTodasAsContas() {
        when(repository.findAll()).thenReturn(List.of(novaConta(), novaConta()));

        assertThat(service.listar()).hasSize(2);
    }

    @Test
    void atualizar_deveAlterarTitularEChave_quandoContaExiste() {
        Conta existente = novaConta();
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        when(repository.existsByChavePix("novo@email.com")).thenReturn(false);
        when(repository.save(existente)).thenReturn(existente);

        Conta dados = new Conta("Rafael N.", "12345678901", "novo@email.com", BigDecimal.ZERO);
        Conta atualizada = service.atualizar(1L, dados);

        assertThat(atualizada.getTitular()).isEqualTo("Rafael N.");
        assertThat(atualizada.getChavePix()).isEqualTo("novo@email.com");
    }

    @Test
    void atualizar_deveLancarExcecao_quandoNovaChavePixPertenceAOutraConta() {
        when(repository.findById(1L)).thenReturn(Optional.of(novaConta()));
        when(repository.existsByChavePix("outra@email.com")).thenReturn(true);

        Conta dados = new Conta("Rafael", "12345678901", "outra@email.com", BigDecimal.ZERO);

        assertThatThrownBy(() -> service.atualizar(1L, dados))
                .isInstanceOf(ChavePixDuplicadaException.class);
    }

    @Test
    void remover_deveApagar_quandoContaExiste() {
        when(repository.existsById(1L)).thenReturn(true);

        service.remover(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void remover_deveLancarExcecao_quandoContaNaoExiste() {
        when(repository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.remover(99L))
                .isInstanceOf(ContaNaoEncontradaException.class);
        verify(repository, never()).deleteById(any());
    }
}
