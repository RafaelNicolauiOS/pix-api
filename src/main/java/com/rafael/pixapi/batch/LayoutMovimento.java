package com.rafael.pixapi.batch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

// Layout do arquivo MOVIMENTO: registros de 150 bytes, campos em posições fixas.
// É o mesmo contrato do copybook cobol/copybooks/MOVIMENTO.cpy — se um mudar, o outro muda junto.
//
//  H  1     tipo 'H' | 2-9 data-ref AAAAMMDD | 10-23 gerado-em AAAAMMDDhhmmss | 24-33 sistema
//  C  1     tipo 'C' | 2-11 conta-id 9(10) | 12-88 chave X(77) | 89-118 titular X(30) | 119-133 saldo 9(13)V99
//  L  1     tipo 'L' | 2-11 conta-id | 12-19 data | 20-25 hora | 26 natureza C/D | 27-38 tipo X(12)
//                    | 39-53 valor 9(13)V99 | 54-89 transferencia-id X(36)
//  T  1     tipo 'T' | 2-10 qtd-contas 9(9) | 11-19 qtd-lancamentos 9(9)
//                    | 20-36 total-creditos 9(15)V99 | 37-53 total-debitos 9(15)V99
//
// "9(13)V99" é notação COBOL: 13 dígitos inteiros + 2 decimais, com a vírgula IMPLÍCITA
// (não é gravada). R$ 1.234,50 vira "000000000123450".
public final class LayoutMovimento {

    public static final int TAMANHO_REGISTRO = 150;

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private LayoutMovimento() {
    }

    public static String header(LocalDate dataReferencia, LocalDateTime geradoEm) {
        return registro("H" + DATA.format(dataReferencia) + DATA_HORA.format(geradoEm) + texto("PIX-API", 10));
    }

    public static String conta(Long id, String chavePix, String titular, BigDecimal saldo) {
        return registro("C" + numero(id, 10) + texto(chavePix, 77) + texto(titular, 30) + valor(saldo, 15));
    }

    public static String lancamento(Long contaId, LocalDateTime quando, boolean credito, String tipo,
                                    BigDecimal valor, UUID transferenciaId) {
        return registro("L" + numero(contaId, 10) + DATA.format(quando) + HORA.format(quando)
                + (credito ? "C" : "D") + texto(tipo, 12) + valor(valor, 15)
                + texto(transferenciaId == null ? "" : transferenciaId.toString(), 36));
    }

    public static String trailer(long qtdContas, long qtdLancamentos, BigDecimal creditos, BigDecimal debitos) {
        return registro("T" + numero(qtdContas, 9) + numero(qtdLancamentos, 9)
                + valor(creditos, 17) + valor(debitos, 17));
    }

    // Completa com espaços até 150 (o FILLER do COBOL).
    private static String registro(String conteudo) {
        if (conteudo.length() > TAMANHO_REGISTRO) {
            throw new IllegalStateException("registro maior que " + TAMANHO_REGISTRO + ": " + conteudo.length());
        }
        return String.format("%-" + TAMANHO_REGISTRO + "s", conteudo);
    }

    // PIC 9(n): zeros à esquerda.
    private static String numero(long valor, int tamanho) {
        return String.format("%0" + tamanho + "d", valor);
    }

    // PIC 9(n)V99: valor em centavos, sem vírgula, zeros à esquerda.
    private static String valor(BigDecimal valor, int tamanho) {
        long centavos = valor.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
        return numero(centavos, tamanho);
    }

    // PIC X(n): ASCII, alinhado à esquerda, completado com espaços ou truncado.
    // Acentos viram letras simples ("ç" → "c"): em UTF-8 eles ocupam 2 bytes e deslocariam
    // todas as posições seguintes, porque o COBOL lê o registro byte a byte.
    private static String texto(String valor, int tamanho) {
        String semAcento = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\x20-\\x7E]", "?");
        String cortado = semAcento.length() > tamanho ? semAcento.substring(0, tamanho) : semAcento;
        return String.format("%-" + tamanho + "s", cortado);
    }
}
