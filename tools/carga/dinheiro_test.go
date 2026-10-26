package main

import (
	"encoding/json"
	"testing"
)

// "Table-driven tests": o padrão em Go. Uma lista de casos e um único laço que testa todos.
func TestParseCentavos(t *testing.T) {
	casos := []struct {
		entrada string
		quer    int64
	}{
		{"1000", 100000},
		{"1000.00", 100000},
		{"899.5", 89950},
		{"0.01", 1},
		{"0", 0},
		{"12.34", 1234},
	}
	for _, c := range casos {
		obtido, err := ParseCentavos(json.Number(c.entrada))
		if err != nil || obtido != c.quer {
			t.Errorf("ParseCentavos(%q) = %d, %v; quer %d", c.entrada, obtido, err, c.quer)
		}
	}
}

func TestParseCentavos_recusaMaisDeDuasCasas(t *testing.T) {
	if _, err := ParseCentavos(json.Number("1.234")); err == nil {
		t.Fatal("esperava erro para 3 casas decimais")
	}
}

func TestValorJSON(t *testing.T) {
	casos := map[int64]string{1: "0.01", 1234: "12.34", 100000: "1000.00", 0: "0.00"}
	for centavos, quer := range casos {
		if obtido := string(ValorJSON(centavos)); obtido != quer {
			t.Errorf("ValorJSON(%d) = %s; quer %s", centavos, obtido, quer)
		}
	}
}

func TestReais(t *testing.T) {
	casos := map[int64]string{1: "R$ 0,01", 123456: "R$ 1.234,56", 100000000: "R$ 1.000.000,00", -500: "-R$ 5,00"}
	for centavos, quer := range casos {
		if obtido := Reais(centavos); obtido != quer {
			t.Errorf("Reais(%d) = %s; quer %s", centavos, obtido, quer)
		}
	}
}
