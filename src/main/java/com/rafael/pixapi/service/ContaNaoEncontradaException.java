package com.rafael.pixapi.service;

// RuntimeException = "unchecked": não obriga quem chama a declarar throws (como em Swift sem "throws").
public class ContaNaoEncontradaException extends RuntimeException {

    public ContaNaoEncontradaException(Long id) {
        super("Conta não encontrada: id=" + id);
    }
}
