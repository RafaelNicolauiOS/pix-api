package com.rafael.pixapi.mensageria;

import com.rafael.pixapi.service.PublicadorDeEventos;
import com.rafael.pixapi.service.TransferenciaRealizadaEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Ponte entre o evento interno do Spring e o mundo externo (Kafka, log...).
//
// @TransactionalEventListener(AFTER_COMMIT): o Spring "segura" o evento publicado dentro da
// transação e só chama este método depois que o COMMIT no banco deu certo. Se houver rollback,
// o evento é descartado. Assim nunca avisamos "transferência realizada" de algo que não foi gravado.
@Component
public class TransferenciaRealizadaListener {

    private final PublicadorDeEventos publicador;

    public TransferenciaRealizadaListener(PublicadorDeEventos publicador) {
        this.publicador = publicador;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoRealizarTransferencia(TransferenciaRealizadaEvent evento) {
        publicador.publicar(evento);
    }
}
