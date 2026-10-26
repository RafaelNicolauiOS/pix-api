package main

import (
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
)

// Go não tem BigDecimal. Para dinheiro usamos int64 em CENTAVOS: inteiros não têm erro de
// arredondamento (em float64, 0.1 + 0.2 == 0.30000000000000004).

// ParseCentavos converte o número JSON da API ("899.5", "1000.00") em centavos, sem passar por float.
func ParseCentavos(n json.Number) (int64, error) {
	texto := n.String()
	negativo := strings.HasPrefix(texto, "-")
	texto = strings.TrimPrefix(texto, "-")

	inteiro, fracao, _ := strings.Cut(texto, ".")
	if len(fracao) > 2 {
		return 0, fmt.Errorf("valor %q tem mais de 2 casas decimais", n)
	}
	fracao += strings.Repeat("0", 2-len(fracao)) // "5" → "50"

	centavos, err := strconv.ParseInt(inteiro+fracao, 10, 64)
	if err != nil {
		return 0, fmt.Errorf("valor inválido %q: %w", n, err)
	}
	if negativo {
		centavos = -centavos
	}
	return centavos, nil
}

// ValorJSON escreve centavos como número JSON com 2 casas (1234 → 12.34), aceito como BigDecimal pela API.
func ValorJSON(centavos int64) json.RawMessage {
	return json.RawMessage(fmt.Sprintf("%d.%02d", centavos/100, centavos%100))
}

// Reais formata para exibição: 123456 → "R$ 1.234,56".
func Reais(centavos int64) string {
	sinal := ""
	if centavos < 0 {
		sinal, centavos = "-", -centavos
	}
	inteiro := strconv.FormatInt(centavos/100, 10)
	var comPontos strings.Builder
	for i, digito := range inteiro {
		if i > 0 && (len(inteiro)-i)%3 == 0 {
			comPontos.WriteByte('.')
		}
		comPontos.WriteRune(digito)
	}
	return fmt.Sprintf("%sR$ %s,%02d", sinal, comPontos.String(), centavos%100)
}
