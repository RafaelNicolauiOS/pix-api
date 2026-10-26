package main

import (
	"strings"
	"testing"
)

// Em Go, testes são funções TestXxx(t *testing.T) num arquivo _test.go, rodadas com "go test".
// Não há framework de asserções: comparamos com if e chamamos t.Errorf (como XCTAssert, mas manual).

func TestVerificar_semViolacoes_quandoSaldosBatem(t *testing.T) {
	iniciais := map[string]int64{"a": 10000, "b": 5000}
	confirmadas := []Transferencia{{Origem: "a", Destino: "b", Centavos: 2550}}
	finais := map[string]int64{"a": 7450, "b": 7550}

	violacoes := Verificar(iniciais, confirmadas, finais)

	if len(violacoes) != 0 {
		t.Fatalf("esperava nenhuma violação, veio %v", violacoes)
	}
}

func TestVerificar_detectaSaldoDiferenteDoEsperado(t *testing.T) {
	// Simula "lost update": duas transferências confirmadas, mas só uma refletida no saldo.
	iniciais := map[string]int64{"a": 10000, "b": 0}
	confirmadas := []Transferencia{
		{Origem: "a", Destino: "b", Centavos: 1000},
		{Origem: "a", Destino: "b", Centavos: 1000},
	}
	finais := map[string]int64{"a": 9000, "b": 1000}

	violacoes := Verificar(iniciais, confirmadas, finais)

	if !contem(violacoes, "a", "esperado R$ 80,00") || !contem(violacoes, "b", "esperado R$ 20,00") {
		t.Fatalf("esperava divergência em a e b, veio %v", violacoes)
	}
}

func TestVerificar_detectaDinheiroCriadoOuDestruido(t *testing.T) {
	iniciais := map[string]int64{"a": 10000, "b": 0}
	finais := map[string]int64{"a": 10000, "b": 500} // surgiram R$ 5,00 do nada

	violacoes := Verificar(iniciais, nil, finais)

	if !contem(violacoes, "TOTAL", "R$ 100,00") {
		t.Fatalf("esperava violação no total, veio %v", violacoes)
	}
}

func TestVerificar_detectaSaldoNegativo(t *testing.T) {
	iniciais := map[string]int64{"a": 1000, "b": 0}
	confirmadas := []Transferencia{{Origem: "a", Destino: "b", Centavos: 1500}}
	finais := map[string]int64{"a": -500, "b": 1500}

	violacoes := Verificar(iniciais, confirmadas, finais)

	if !contem(violacoes, "a", "negativo") {
		t.Fatalf("esperava violação de saldo negativo, veio %v", violacoes)
	}
}

func TestVerificar_detectaContaQueSumiu(t *testing.T) {
	iniciais := map[string]int64{"a": 1000}

	violacoes := Verificar(iniciais, nil, map[string]int64{})

	if !contem(violacoes, "a", "não encontrada") {
		t.Fatalf("esperava violação de conta ausente, veio %v", violacoes)
	}
}

func contem(violacoes []Violacao, chave, trecho string) bool {
	for _, v := range violacoes {
		if v.Chave == chave && strings.Contains(v.Descricao, trecho) {
			return true
		}
	}
	return false
}
