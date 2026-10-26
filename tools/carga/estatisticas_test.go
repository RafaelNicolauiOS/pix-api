package main

import (
	"testing"
	"time"
)

func TestPercentil(t *testing.T) {
	// 1ms, 2ms, ..., 100ms embaralhados: o percentil não pode depender da ordem de entrada.
	var latencias []time.Duration
	for i := 100; i >= 1; i-- {
		latencias = append(latencias, time.Duration(i)*time.Millisecond)
	}

	casos := map[float64]time.Duration{50: 50 * time.Millisecond, 95: 95 * time.Millisecond,
		99: 99 * time.Millisecond, 100: 100 * time.Millisecond}
	for p, quer := range casos {
		if obtido := Percentil(latencias, p); obtido != quer {
			t.Errorf("Percentil(p%.0f) = %v; quer %v", p, obtido, quer)
		}
	}
}

func TestPercentil_listaVazia(t *testing.T) {
	if obtido := Percentil(nil, 95); obtido != 0 {
		t.Errorf("lista vazia deveria dar 0, deu %v", obtido)
	}
}

func TestPercentil_naoAlteraAListaOriginal(t *testing.T) {
	latencias := []time.Duration{3, 1, 2}
	Percentil(latencias, 50)
	if latencias[0] != 3 || latencias[1] != 1 || latencias[2] != 2 {
		t.Errorf("a lista original foi alterada: %v", latencias)
	}
}
