package com.rafael.pixapi.domain;

// enum com propriedade: em Swift seria um enum com uma computed property "isCredito".
public enum TipoLancamento {
    ABERTURA(true),      // depósito inicial ao criar a conta
    PIX_RECEBIDO(true),
    PIX_ENVIADO(false),
    ENCERRAMENTO(false); // saída do saldo restante ao excluir a conta

    private final boolean credito;

    TipoLancamento(boolean credito) {
        this.credito = credito;
    }

    public boolean isCredito() {
        return credito;
    }
}
