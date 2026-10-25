package com.rafael.pixapi.service;

public class ChavePixNaoEncontradaException extends RuntimeException {

    public ChavePixNaoEncontradaException(String chavePix) {
        super("Chave Pix não encontrada: " + chavePix);
    }
}
