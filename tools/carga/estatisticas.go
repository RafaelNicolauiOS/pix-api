package main

import (
	"math"
	"slices"
	"time"
)

// Percentil pelo método "nearest-rank": p95 = o valor abaixo do qual estão 95% das amostras.
// Média esconde lentidão; o p95/p99 mostra o que os usuários mais azarados sentem.
func Percentil(latencias []time.Duration, p float64) time.Duration {
	if len(latencias) == 0 {
		return 0
	}
	ordenadas := slices.Clone(latencias) // não altera a lista de quem chamou
	slices.Sort(ordenadas)
	posicao := int(math.Ceil(p/100*float64(len(ordenadas)))) - 1
	return ordenadas[max(0, min(posicao, len(ordenadas)-1))]
}
