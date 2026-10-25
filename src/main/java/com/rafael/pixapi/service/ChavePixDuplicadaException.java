package com.rafael.pixapi.service;

public class ChavePixDuplicadaException extends RuntimeException {

    public ChavePixDuplicadaException(String chavePix) {
        super("Chave Pix já cadastrada: " + chavePix);
    }
}
