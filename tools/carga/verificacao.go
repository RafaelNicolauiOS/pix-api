package main

import (
	"fmt"
	"sort"
)

// Transferencia que a API confirmou (HTTP 200).
type Transferencia struct {
	Origem   string
	Destino  string
	Centavos int64
}

// Violacao de uma regra de consistência. Chave "TOTAL" indica o conjunto das contas.
type Violacao struct {
	Chave     string
	Descricao string
}

func (v Violacao) String() string { return v.Chave + ": " + v.Descricao }

// Verificar confere as invariantes depois da carga:
//  1. cada conta termina com: saldo inicial − enviado + recebido (só transferências confirmadas);
//  2. nenhuma conta fica negativa;
//  3. a soma dos saldos não muda (transferência move dinheiro, não cria nem destrói).
//
// Se a API tivesse uma condição de corrida (duas transferências lendo o mesmo saldo ao mesmo
// tempo), a regra 1 quebraria: a API diria 200 para as duas, mas o saldo refletiria só uma.
func Verificar(iniciais map[string]int64, confirmadas []Transferencia, finais map[string]int64) []Violacao {
	esperados := make(map[string]int64, len(iniciais))
	var totalInicial, totalFinal int64
	for chave, saldo := range iniciais {
		esperados[chave] = saldo
		totalInicial += saldo
	}
	for _, t := range confirmadas {
		esperados[t.Origem] -= t.Centavos
		esperados[t.Destino] += t.Centavos
	}

	// Mapas em Go não têm ordem; ordenamos as chaves para o relatório ser estável.
	chaves := make([]string, 0, len(esperados))
	for chave := range esperados {
		chaves = append(chaves, chave)
	}
	sort.Strings(chaves)

	var violacoes []Violacao
	for _, chave := range chaves {
		final, existe := finais[chave] // "comma ok": como um Optional em Swift
		if !existe {
			violacoes = append(violacoes, Violacao{chave, "conta não encontrada ao final"})
			continue
		}
		totalFinal += final
		if final != esperados[chave] {
			violacoes = append(violacoes, Violacao{chave, fmt.Sprintf("saldo %s, esperado %s",
				Reais(final), Reais(esperados[chave]))})
		}
		if final < 0 {
			violacoes = append(violacoes, Violacao{chave, "saldo negativo: " + Reais(final)})
		}
	}

	if totalFinal != totalInicial && len(finais) == len(iniciais) {
		violacoes = append(violacoes, Violacao{"TOTAL", fmt.Sprintf("soma dos saldos %s, deveria continuar %s",
			Reais(totalFinal), Reais(totalInicial))})
	}
	return violacoes
}
