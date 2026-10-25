package com.rafael.pixapi.monitoramento;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Usa o Logback de verdade: o que for logado depois de instalar() precisa aparecer em ultimas().
class LogsEmMemoriaTest {

    private final Logger log = LoggerFactory.getLogger("com.rafael.pixapi.TesteDeLog");
    private LogsEmMemoria logs;

    @AfterEach
    void desinstalar() {
        logs.desinstalar();
    }

    @Test
    void deveCapturarLinhasComNivelLoggerEMensagem() {
        logs = new LogsEmMemoria(10);
        logs.instalar();

        log.warn("Saldo baixo na conta {}", "ana@pix.com");

        List<LinhaDeLog> linhas = logs.ultimas(10);
        LinhaDeLog linha = linhas.get(linhas.size() - 1);
        assertThat(linha.nivel()).isEqualTo("WARN");
        assertThat(linha.logger()).isEqualTo("TesteDeLog");
        assertThat(linha.mensagem()).isEqualTo("Saldo baixo na conta ana@pix.com");
        assertThat(linha.instante()).isNotNull();
    }

    @Test
    void deveIncluirAExcecaoNaMensagem() {
        logs = new LogsEmMemoria(10);
        logs.instalar();

        log.error("Falha ao publicar", new IllegalStateException("broker fora do ar"));

        assertThat(logs.ultimas(1).get(0).mensagem())
                .contains("Falha ao publicar")
                .contains("IllegalStateException: broker fora do ar");
    }

    @Test
    void deveManterSoAsMaisRecentes_emOrdemCronologica() {
        logs = new LogsEmMemoria(2);
        logs.instalar();

        log.info("um");
        log.info("dois");
        log.info("tres");

        assertThat(logs.ultimas(10)).extracting(LinhaDeLog::mensagem).containsExactly("dois", "tres");
        assertThat(logs.ultimas(1)).extracting(LinhaDeLog::mensagem).containsExactly("tres");
    }
}
