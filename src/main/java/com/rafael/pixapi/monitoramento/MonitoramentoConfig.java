package com.rafael.pixapi.monitoramento;

import com.rafael.pixapi.service.TransferenciaRealizadaEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Configuration
public class MonitoramentoConfig {

    // initMethod/destroyMethod: o Spring chama instalar() ao criar o bean e desinstalar() ao encerrar
    // (parecido com viewDidLoad / deinit), para o appender não ficar "pendurado" no Logback.
    @Bean(initMethod = "instalar", destroyMethod = "desinstalar")
    LogsEmMemoria logsEmMemoria() {
        return new LogsEmMemoria(500);
    }

    @Bean
    HistoricoDeEventos historicoDeEventos() {
        return new HistoricoDeEventos(100);
    }

    // Um segundo listener do mesmo evento, independente do PublicadorDeEventos: funciona tanto no
    // modo "log" quanto no "kafka". Também é AFTER_COMMIT, então só registra transferências gravadas.
    @Bean
    RegistroDeEventos registroDeEventos(HistoricoDeEventos historico) {
        return new RegistroDeEventos(historico);
    }

    public static class RegistroDeEventos {

        private final HistoricoDeEventos historico;

        RegistroDeEventos(HistoricoDeEventos historico) {
            this.historico = historico;
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void aoRealizarTransferencia(TransferenciaRealizadaEvent evento) {
            historico.registrar(evento);
        }
    }
}
