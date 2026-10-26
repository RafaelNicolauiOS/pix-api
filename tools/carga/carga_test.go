package main

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"
)

// apiFalsa imita a pix-api em memória, para testar a ferramenta sem subir o Spring Boot.
// comBug = true reproduz o "lost update": lê o saldo, espera, e grava depois — sem trava entre
// a leitura e a escrita. É exatamente o erro que o SELECT ... FOR UPDATE da API em Java evita.
type apiFalsa struct {
	mu      sync.Mutex
	comBug  bool
	proxID  int64
	contas  map[string]*contaFalsa
	porID   map[int64]string
	excluir int
}

type contaFalsa struct {
	ID       int64
	Titular  string
	CPF      string
	Chave    string
	Centavos int64
}

func novaAPIFalsa(comBug bool) *httptest.Server {
	api := &apiFalsa{comBug: comBug, contas: map[string]*contaFalsa{}, porID: map[int64]string{}}
	mux := http.NewServeMux()
	mux.HandleFunc("POST /contas", api.criar)
	mux.HandleFunc("GET /contas", api.listar)
	mux.HandleFunc("DELETE /contas/{id}", api.remover)
	mux.HandleFunc("POST /transferencias", api.transferir)
	return httptest.NewServer(mux)
}

func (a *apiFalsa) criar(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Titular  string      `json:"titular"`
		CPF      string      `json:"cpf"`
		ChavePix string      `json:"chavePix"`
		Saldo    json.Number `json:"saldo"`
	}
	json.NewDecoder(r.Body).Decode(&req)
	centavos, _ := ParseCentavos(req.Saldo)
	a.mu.Lock()
	defer a.mu.Unlock()
	a.proxID++
	c := &contaFalsa{a.proxID, req.Titular, req.CPF, req.ChavePix, centavos}
	a.contas[c.Chave] = c
	a.porID[c.ID] = c.Chave
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(map[string]any{"id": c.ID, "chavePix": c.Chave, "saldo": ValorJSON(c.Centavos)})
}

func (a *apiFalsa) listar(w http.ResponseWriter, _ *http.Request) {
	a.mu.Lock()
	defer a.mu.Unlock()
	lista := []map[string]any{}
	for _, c := range a.contas {
		lista = append(lista, map[string]any{"id": c.ID, "titular": c.Titular, "cpf": c.CPF,
			"chavePix": c.Chave, "saldo": ValorJSON(c.Centavos)})
	}
	json.NewEncoder(w).Encode(lista)
}

func (a *apiFalsa) remover(w http.ResponseWriter, r *http.Request) {
	id, _ := strconv.ParseInt(r.PathValue("id"), 10, 64)
	a.mu.Lock()
	defer a.mu.Unlock()
	delete(a.contas, a.porID[id])
	a.excluir++
	w.WriteHeader(http.StatusNoContent)
}

func (a *apiFalsa) transferir(w http.ResponseWriter, r *http.Request) {
	var req struct {
		ChaveOrigem  string      `json:"chaveOrigem"`
		ChaveDestino string      `json:"chaveDestino"`
		Valor        json.Number `json:"valor"`
	}
	json.NewDecoder(r.Body).Decode(&req)
	valor, _ := ParseCentavos(req.Valor)

	a.mu.Lock()
	origem, destino := a.contas[req.ChaveOrigem], a.contas[req.ChaveDestino]
	if origem == nil || destino == nil {
		a.mu.Unlock()
		w.WriteHeader(http.StatusNotFound)
		return
	}
	saldoOrigem, saldoDestino := origem.Centavos, destino.Centavos // leitura
	if saldoOrigem < valor {
		a.mu.Unlock()
		w.WriteHeader(http.StatusUnprocessableEntity)
		return
	}
	if a.comBug {
		a.mu.Unlock()
		time.Sleep(2 * time.Millisecond) // janela em que outra requisição lê o mesmo saldo
		a.mu.Lock()
	}
	origem.Centavos = saldoOrigem - valor // escrita com base na leitura (talvez desatualizada)
	destino.Centavos = saldoDestino + valor
	a.mu.Unlock()
	w.WriteHeader(http.StatusOK)
	w.Write([]byte(`{}`))
}

func configTeste() Config {
	return Config{Contas: 4, SaldoInicial: 10000, Transferencias: 300, Concorrencia: 30, ValorMaximo: 500, Semente: 42}
}

func TestExecutar_apiCorreta_naoTemViolacoes(t *testing.T) {
	srv := novaAPIFalsa(false)
	defer srv.Close()

	res, err := Executar(context.Background(), NovoCliente(srv.URL, 30), configTeste(), "teste")
	if err != nil {
		t.Fatal(err)
	}

	if violacoes := Verificar(res.Iniciais, res.Confirmadas, res.Finais); len(violacoes) != 0 {
		t.Fatalf("API correta não deveria ter violações: %v", violacoes)
	}
	if res.PorStatus[200] == 0 {
		t.Fatalf("esperava transferências confirmadas, status: %v", res.PorStatus)
	}
	if total := res.PorStatus[200] + res.PorStatus[422] + res.ErrosDeRede; total != 300 {
		t.Fatalf("esperava 300 resultados, veio %d (%v)", total, res.PorStatus)
	}
	if len(res.Latencias) != 300 {
		t.Fatalf("esperava 300 latências, veio %d", len(res.Latencias))
	}
}

func TestExecutar_apiComCondicaoDeCorrida_eDetectada(t *testing.T) {
	srv := novaAPIFalsa(true)
	defer srv.Close()

	res, err := Executar(context.Background(), NovoCliente(srv.URL, 30), configTeste(), "teste")
	if err != nil {
		t.Fatal(err)
	}

	violacoes := Verificar(res.Iniciais, res.Confirmadas, res.Finais)
	if len(violacoes) == 0 {
		t.Fatal("a ferramenta deveria detectar o lost update da API com bug")
	}
	t.Logf("violações detectadas (esperado): %d, ex.: %v", len(violacoes), violacoes[0])
}

func TestExecutar_criaContasComPrefixoUnico_eLimpaAoFinal(t *testing.T) {
	srv := novaAPIFalsa(false)
	defer srv.Close()
	cli := NovoCliente(srv.URL, 5)
	cfg := configTeste()
	cfg.Transferencias = 10

	res, err := Executar(context.Background(), cli, cfg, "abc123")
	if err != nil {
		t.Fatal(err)
	}
	for chave := range res.Iniciais {
		if !strings.HasPrefix(chave, "carga-abc123-") {
			t.Errorf("chave sem o prefixo da execução: %s", chave)
		}
	}

	if err := Limpar(context.Background(), cli, res.IDs); err != nil {
		t.Fatal(err)
	}
	finais, _, err := cli.Saldos(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if len(finais) != 0 {
		t.Fatalf("contas de teste deveriam ter sido removidas, restaram %d", len(finais))
	}
}
