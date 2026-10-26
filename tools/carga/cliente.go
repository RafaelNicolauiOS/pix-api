package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

// Cliente fala com a pix-api. Em Go não há classes: um struct com métodos cumpre esse papel,
// e erros são valores retornados (nada de throw/try): cada chamada devolve (resultado, error).
type Cliente struct {
	base string
	http *http.Client
}

// NovoCliente reaproveita conexões TCP entre requisições (keep-alive), essencial sob carga.
func NovoCliente(base string, conexoes int) *Cliente {
	transporte := http.DefaultTransport.(*http.Transport).Clone()
	transporte.MaxIdleConns = conexoes
	transporte.MaxIdleConnsPerHost = conexoes
	return &Cliente{
		base: strings.TrimRight(base, "/"),
		// 90 s: o plano gratuito do Render leva ~1 min para "acordar" a API.
		http: &http.Client{Transport: transporte, Timeout: 90 * time.Second},
	}
}

type conta struct {
	ID       int64       `json:"id"`
	ChavePix string      `json:"chavePix"`
	Saldo    json.Number `json:"saldo"` // json.Number guarda o texto do número, sem virar float
}

// CriarConta devolve o id da conta criada.
func (c *Cliente) CriarConta(ctx context.Context, titular, cpf, chave string, saldoCentavos int64) (int64, error) {
	corpo := map[string]any{"titular": titular, "cpf": cpf, "chavePix": chave, "saldo": ValorJSON(saldoCentavos)}
	var criada conta
	status, err := c.enviar(ctx, http.MethodPost, "/contas", corpo, &criada)
	if err != nil {
		return 0, err
	}
	if status != http.StatusCreated {
		return 0, fmt.Errorf("criar conta %s: HTTP %d", chave, status)
	}
	return criada.ID, nil
}

// Transferir devolve só o status HTTP: 200, 422 e 404 são respostas válidas da API, não erros.
// error != nil significa falha de rede/timeout.
func (c *Cliente) Transferir(ctx context.Context, origem, destino string, centavos int64) (int, error) {
	corpo := map[string]any{"chaveOrigem": origem, "chaveDestino": destino, "valor": ValorJSON(centavos)}
	return c.enviar(ctx, http.MethodPost, "/transferencias", corpo, nil)
}

// Saldos lista todas as contas: saldos e ids por chave Pix.
func (c *Cliente) Saldos(ctx context.Context) (saldos map[string]int64, ids map[string]int64, err error) {
	var contas []conta
	status, err := c.enviar(ctx, http.MethodGet, "/contas", nil, &contas)
	if err != nil {
		return nil, nil, err
	}
	if status != http.StatusOK {
		return nil, nil, fmt.Errorf("listar contas: HTTP %d", status)
	}
	saldos, ids = make(map[string]int64, len(contas)), make(map[string]int64, len(contas))
	for _, ct := range contas {
		centavos, err := ParseCentavos(ct.Saldo)
		if err != nil {
			return nil, nil, err
		}
		saldos[ct.ChavePix], ids[ct.ChavePix] = centavos, ct.ID
	}
	return saldos, ids, nil
}

func (c *Cliente) RemoverConta(ctx context.Context, id int64) error {
	status, err := c.enviar(ctx, http.MethodDelete, fmt.Sprintf("/contas/%d", id), nil, nil)
	if err != nil {
		return err
	}
	if status != http.StatusNoContent && status != http.StatusNotFound {
		return fmt.Errorf("remover conta %d: HTTP %d", id, status)
	}
	return nil
}

func (c *Cliente) enviar(ctx context.Context, metodo, caminho string, corpo, resposta any) (int, error) {
	var leitor io.Reader
	if corpo != nil {
		dados, err := json.Marshal(corpo)
		if err != nil {
			return 0, err
		}
		leitor = bytes.NewReader(dados)
	}
	req, err := http.NewRequestWithContext(ctx, metodo, c.base+caminho, leitor)
	if err != nil {
		return 0, err
	}
	req.Header.Set("Content-Type", "application/json")

	res, err := c.http.Do(req)
	if err != nil {
		return 0, err
	}
	// defer: executa ao sair da função, como o "defer" do Swift. Fechar o corpo libera a conexão.
	defer res.Body.Close()

	if resposta != nil && res.StatusCode < 300 {
		if err := json.NewDecoder(res.Body).Decode(resposta); err != nil {
			return res.StatusCode, fmt.Errorf("resposta inválida de %s %s: %w", metodo, caminho, err)
		}
	} else {
		io.Copy(io.Discard, res.Body) // ler até o fim permite reutilizar a conexão
	}
	return res.StatusCode, nil
}
