package com.rafael.pixapi.mensageria;

import com.rafael.pixapi.domain.Conta;
import com.rafael.pixapi.domain.SaldoInsuficienteException;
import com.rafael.pixapi.repository.ContaRepository;
import com.rafael.pixapi.service.PublicadorDeEventos;
import com.rafael.pixapi.service.TransferenciaRealizadaEvent;
import com.rafael.pixapi.service.TransferenciaService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// Aplicação real (banco + transações), com o PublicadorDeEventos trocado por um mock.
// Prova a regra principal: o evento só sai DEPOIS do commit, e nunca se houver rollback.
@SpringBootTest
class PublicacaoAposCommitTest {

    @Autowired
    private TransferenciaService service;

    @Autowired
    private ContaRepository repository;

    @Autowired
    private TransactionTemplate transacao; // abre transações "na mão" dentro do teste

    @MockitoBean
    private PublicadorDeEventos publicador;

    // Chaves únicas por teste, porque o banco H2 é compartilhado entre os testes da classe.
    private String criarConta(String saldo) {
        String chave = "evt-" + UUID.randomUUID() + "@pix.com";
        repository.save(new Conta("Teste", "12345678901", chave, new BigDecimal(saldo)));
        return chave;
    }

    @Test
    void transferenciaComSucesso_devePublicarEventoAposCommit() {
        String origem = criarConta("100.00");
        String destino = criarConta("0.00");

        service.transferir(origem, destino, new BigDecimal("40.00"));

        ArgumentCaptor<TransferenciaRealizadaEvent> captor = ArgumentCaptor.forClass(TransferenciaRealizadaEvent.class);
        verify(publicador).publicar(captor.capture());
        assertThat(captor.getValue().chaveOrigem()).isEqualTo(origem);
        assertThat(captor.getValue().valor()).isEqualByComparingTo("40.00");
    }

    @Test
    void saldoInsuficiente_naoDevePublicarEvento() {
        String origem = criarConta("10.00");
        String destino = criarConta("0.00");

        assertThatThrownBy(() -> service.transferir(origem, destino, new BigDecimal("10.01")))
                .isInstanceOf(SaldoInsuficienteException.class);

        verify(publicador, never()).publicar(any());
    }

    @Test
    void eventoNaoDeveSairAntesDoCommit_nemSeATransacaoForDesfeita() {
        String origem = criarConta("100.00");
        String destino = criarConta("0.00");

        // O service publica o evento normalmente, mas a transação externa termina em rollback
        // (simula uma falha qualquer depois da transferência, ainda dentro da mesma transação).
        transacao.executeWithoutResult(status -> {
            service.transferir(origem, destino, new BigDecimal("25.00"));
            verify(publicador, never()).publicar(any()); // ainda não houve commit
            status.setRollbackOnly();
        });

        verify(publicador, never()).publicar(any());
        assertThat(saldoDe(origem)).isEqualByComparingTo("100.00"); // rollback desfez o débito
    }

    private BigDecimal saldoDe(String chave) {
        return repository.findAll().stream()
                .filter(c -> c.getChavePix().equals(chave))
                .findFirst().orElseThrow().getSaldo();
    }
}
