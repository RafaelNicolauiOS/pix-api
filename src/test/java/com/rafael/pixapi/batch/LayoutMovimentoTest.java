package com.rafael.pixapi.batch;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// O layout é o contrato com o COBOL (copybook MOVIMENTO.cpy): cada campo numa posição fixa.
// Os testes conferem posição por posição, como um analista de mainframe confere um layout.
class LayoutMovimentoTest {

    // Posições "humanas" (1-based, inclusivas), como na documentação de layout de mainframe.
    private static String campo(String registro, int de, int ate) {
        return registro.substring(de - 1, ate);
    }

    @Test
    void header_deveTerDataDeReferenciaGeracaoESistema() {
        String h = LayoutMovimento.header(LocalDate.of(2026, 10, 25), LocalDateTime.of(2026, 10, 26, 3, 0, 5));

        assertThat(h).hasSize(150);
        assertThat(campo(h, 1, 1)).isEqualTo("H");
        assertThat(campo(h, 2, 9)).isEqualTo("20261025");
        assertThat(campo(h, 10, 23)).isEqualTo("20261026030005");
        assertThat(campo(h, 24, 33)).isEqualTo("PIX-API   ");
        assertThat(campo(h, 34, 150)).isBlank();
    }

    @Test
    void conta_deveAlinharCamposERemoverAcentos() {
        String c = LayoutMovimento.conta(42L, "conceição@pix.com", "Maria da Conceição Ávila", new BigDecimal("1234.5"));

        assertThat(c).hasSize(150);
        assertThat(campo(c, 1, 1)).isEqualTo("C");
        assertThat(campo(c, 2, 11)).isEqualTo("0000000042");
        assertThat(campo(c, 12, 88)).isEqualTo(String.format("%-77s", "conceicao@pix.com"));
        assertThat(campo(c, 89, 118)).isEqualTo(String.format("%-30s", "Maria da Conceicao Avila"));
        assertThat(campo(c, 119, 133)).isEqualTo("000000000123450"); // 9(13)V99: vírgula implícita
        assertThat(c).matches("\\p{ASCII}+");
    }

    @Test
    void conta_deveTruncarTitularLongo() {
        String c = LayoutMovimento.conta(1L, "a@pix.com", "Nome Muito Comprido Que Passa De Trinta Caracteres", BigDecimal.TEN);

        assertThat(campo(c, 89, 118)).isEqualTo("Nome Muito Comprido Que Passa ");
        assertThat(c).hasSize(150);
    }

    @Test
    void lancamento_deveTerNaturezaTipoValorEId() {
        UUID id = UUID.fromString("6f1b7c1e-0000-4000-8000-000000000001");
        String l = LayoutMovimento.lancamento(7L, LocalDateTime.of(2026, 10, 25, 14, 30, 9), true, "PIX_RECEBIDO",
                new BigDecimal("30.00"), id);

        assertThat(l).hasSize(150);
        assertThat(campo(l, 1, 1)).isEqualTo("L");
        assertThat(campo(l, 2, 11)).isEqualTo("0000000007");
        assertThat(campo(l, 12, 19)).isEqualTo("20261025");
        assertThat(campo(l, 20, 25)).isEqualTo("143009");
        assertThat(campo(l, 26, 26)).isEqualTo("C");
        assertThat(campo(l, 27, 38)).isEqualTo("PIX_RECEBIDO");
        assertThat(campo(l, 39, 53)).isEqualTo("000000000003000");
        assertThat(campo(l, 54, 89)).isEqualTo(id.toString());
    }

    @Test
    void lancamento_semTransferencia_deixaIdEmBranco() {
        String l = LayoutMovimento.lancamento(7L, LocalDateTime.of(2026, 10, 25, 8, 0), false, "ENCERRAMENTO",
                BigDecimal.ONE, null);

        assertThat(campo(l, 26, 26)).isEqualTo("D");
        assertThat(campo(l, 54, 89)).isBlank();
    }

    @Test
    void trailer_deveTerQuantidadesETotaisDeControle() {
        String t = LayoutMovimento.trailer(3, 12, new BigDecimal("1500.25"), new BigDecimal("200.00"));

        assertThat(t).hasSize(150);
        assertThat(campo(t, 1, 1)).isEqualTo("T");
        assertThat(campo(t, 2, 10)).isEqualTo("000000003");
        assertThat(campo(t, 11, 19)).isEqualTo("000000012");
        assertThat(campo(t, 20, 36)).isEqualTo("00000000000150025");
        assertThat(campo(t, 37, 53)).isEqualTo("00000000000020000");
    }
}
