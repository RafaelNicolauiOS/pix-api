package com.rafael.pixapi.monitoramento;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// Guarda as últimas linhas de log em memória para o endpoint GET /logs.
//
// O Spring Boot usa o Logback para logar. Um "appender" é um destino de log (console, arquivo...);
// aqui criamos um appender extra que, além do console, copia cada linha para um buffer circular.
// Linhas logadas antes de este componente ser criado (início do boot) não são capturadas.
public class LogsEmMemoria {

    private static final String NOME_APPENDER = "MEMORIA";

    private final int capacidade;
    private final Deque<LinhaDeLog> linhas = new ArrayDeque<>();
    private final Appender appender = new Appender();

    public LogsEmMemoria(int capacidade) {
        this.capacidade = capacidade;
    }

    public void instalar() {
        LoggerContext contexto = (LoggerContext) LoggerFactory.getILoggerFactory();
        appender.setContext(contexto);
        appender.setName(NOME_APPENDER);
        appender.start();
        contexto.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
    }

    public void desinstalar() {
        LoggerContext contexto = (LoggerContext) LoggerFactory.getILoggerFactory();
        contexto.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
        appender.stop();
    }

    // As "limite" linhas mais recentes, em ordem cronológica (a mais nova por último, como num tail).
    public synchronized List<LinhaDeLog> ultimas(int limite) {
        List<LinhaDeLog> todas = new ArrayList<>(linhas);
        return List.copyOf(todas.subList(Math.max(0, todas.size() - limite), todas.size()));
    }

    private synchronized void adicionar(LinhaDeLog linha) {
        linhas.addLast(linha);
        while (linhas.size() > capacidade) {
            linhas.removeFirst();
        }
    }

    private class Appender extends AppenderBase<ILoggingEvent> {

        @Override
        protected void append(ILoggingEvent evento) {
            String loggerCompleto = evento.getLoggerName();
            String loggerCurto = loggerCompleto.substring(loggerCompleto.lastIndexOf('.') + 1);
            adicionar(new LinhaDeLog(evento.getInstant(), evento.getLevel().toString(), loggerCurto,
                    mensagemCompleta(evento)));
        }

        private String mensagemCompleta(ILoggingEvent evento) {
            IThrowableProxy erro = evento.getThrowableProxy();
            if (erro == null) {
                return evento.getFormattedMessage();
            }
            // Só a exceção e a causa, sem o stack trace inteiro (que fica no log do Render).
            String texto = evento.getFormattedMessage() + " | " + erro.getClassName() + ": " + erro.getMessage();
            if (erro.getCause() != null) {
                texto += " | causa: " + erro.getCause().getClassName() + ": " + erro.getCause().getMessage();
            }
            return texto;
        }
    }
}
