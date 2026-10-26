package com.rafael.pixapi.controller;

import com.rafael.pixapi.batch.DataDeReferenciaInvalidaException;
import com.rafael.pixapi.domain.SaldoInsuficienteException;
import com.rafael.pixapi.service.ChavePixDuplicadaException;
import com.rafael.pixapi.service.ChavePixNaoEncontradaException;
import com.rafael.pixapi.service.ContaNaoEncontradaException;
import com.rafael.pixapi.service.TransferenciaInvalidaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.TreeMap;

// Converte exceções em respostas HTTP padronizadas (RFC 9457 "Problem Details").
// O Spring preenche "instance" com o caminho da requisição e usa Content-Type application/problem+json.
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ContaNaoEncontradaException.class)
    public ProblemDetail naoEncontrada(ContaNaoEncontradaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ChavePixNaoEncontradaException.class)
    public ProblemDetail chaveNaoEncontrada(ChavePixNaoEncontradaException e) {
        return problema(HttpStatus.NOT_FOUND, "Chave Pix não encontrada", e);
    }

    @ExceptionHandler(ChavePixDuplicadaException.class)
    public ProblemDetail chaveDuplicada(ChavePixDuplicadaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    // 422: a requisição está bem formada, mas viola uma regra de negócio.
    @ExceptionHandler(SaldoInsuficienteException.class)
    public ProblemDetail saldoInsuficiente(SaldoInsuficienteException e) {
        return problema(HttpStatus.UNPROCESSABLE_CONTENT, "Saldo insuficiente", e);
    }

    @ExceptionHandler(TransferenciaInvalidaException.class)
    public ProblemDetail transferenciaInvalida(TransferenciaInvalidaException e) {
        return problema(HttpStatus.UNPROCESSABLE_CONTENT, "Transferência inválida", e);
    }

    @ExceptionHandler(DataDeReferenciaInvalidaException.class)
    public ProblemDetail dataInvalida(DataDeReferenciaInvalidaException e) {
        return problema(HttpStatus.BAD_REQUEST, "Data de referência inválida", e);
    }

    // 400: o JSON recebido não passa nas validações (@NotBlank, @Positive...).
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacao(MethodArgumentNotValidException e) {
        Map<String, String> erros = new TreeMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(erro -> erros.put(erro.getField(), erro.getDefaultMessage()));

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados inválidos");
        problema.setProperty("erros", erros);
        return problema;
    }

    private ProblemDetail problema(HttpStatus status, String titulo, RuntimeException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problema.setTitle(titulo);
        return problema;
    }
}
