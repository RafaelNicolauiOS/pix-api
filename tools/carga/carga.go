package main

import (
	"context"
	"fmt"
	"math/rand/v2"
	"sync"
	"time"
)

type Config struct {
	Contas         int   // poucas contas = muita disputa pelas mesmas linhas no banco
	SaldoInicial   int64 // centavos
	Transferencias int
	Concorrencia   int   // quantas requisições ao mesmo tempo
	ValorMaximo    int64 // centavos
	Semente        uint64
}

type Resultado struct {
	Iniciais    map[string]int64 // saldo inicial por chave
	Finais      map[string]int64 // saldo final das mesmas chaves
	IDs         map[string]int64 // para limpar as contas depois
	Confirmadas []Transferencia  // só as que receberam HTTP 200
	PorStatus   map[int]int
	ErrosDeRede int
	Latencias   []time.Duration
	Duracao     time.Duration
}

// Executar cria contas exclusivas desta execução, dispara as transferências em paralelo
// e lê os saldos finais. Quem decide se houve inconsistência é Verificar.
func Executar(ctx context.Context, cli *Cliente, cfg Config, prefixo string) (*Resultado, error) {
	res := &Resultado{
		Iniciais:  map[string]int64{},
		IDs:       map[string]int64{},
		PorStatus: map[int]int{},
	}

	chaves := make([]string, cfg.Contas)
	for i := range chaves {
		chaves[i] = fmt.Sprintf("carga-%s-%d@pix.com", prefixo, i+1)
		id, err := cli.CriarConta(ctx, fmt.Sprintf("Carga %d", i+1), fmt.Sprintf("%011d", i+1), chaves[i], cfg.SaldoInicial)
		if err != nil {
			return res, fmt.Errorf("preparando contas: %w", err) // res.IDs permite limpar as já criadas
		}
		res.Iniciais[chaves[i]] = cfg.SaldoInicial
		res.IDs[chaves[i]] = id
	}

	// Plano gerado antes, com semente fixa: a mesma semente repete exatamente a mesma carga.
	sorteio := rand.New(rand.NewPCG(cfg.Semente, 0))
	plano := make([]Transferencia, cfg.Transferencias)
	for i := range plano {
		o := sorteio.IntN(cfg.Contas)
		d := (o + 1 + sorteio.IntN(cfg.Contas-1)) % cfg.Contas // sempre diferente da origem
		plano[i] = Transferencia{chaves[o], chaves[d], 1 + sorteio.Int64N(cfg.ValorMaximo)}
	}

	// Worker pool: um canal (fila thread-safe) com as tarefas e N goroutines consumindo.
	// Goroutines são "threads" baratíssimas do Go; um canal é como um AsyncStream que várias
	// tarefas leem ao mesmo tempo, cada item entregue a uma só.
	fila := make(chan Transferencia)
	var (
		mu sync.Mutex     // protege o Resultado, escrito por várias goroutines
		wg sync.WaitGroup // espera todas terminarem (como um TaskGroup)
	)
	inicio := time.Now()
	for range cfg.Concorrencia {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for t := range fila {
				comeco := time.Now()
				status, err := cli.Transferir(ctx, t.Origem, t.Destino, t.Centavos)
				latencia := time.Since(comeco)

				mu.Lock()
				res.Latencias = append(res.Latencias, latencia)
				if err != nil {
					res.ErrosDeRede++
				} else {
					res.PorStatus[status]++
					if status == 200 {
						res.Confirmadas = append(res.Confirmadas, t)
					}
				}
				mu.Unlock()
			}
		}()
	}
	for _, t := range plano {
		fila <- t
	}
	close(fila) // avisa os workers que não há mais tarefas
	wg.Wait()
	res.Duracao = time.Since(inicio)

	todos, _, err := cli.Saldos(ctx)
	if err != nil {
		return res, fmt.Errorf("lendo saldos finais: %w", err)
	}
	res.Finais = map[string]int64{}
	for chave := range res.Iniciais {
		if saldo, ok := todos[chave]; ok {
			res.Finais[chave] = saldo
		}
	}
	return res, nil
}

// Limpar remove as contas criadas pela execução.
func Limpar(ctx context.Context, cli *Cliente, ids map[string]int64) error {
	for chave, id := range ids {
		if err := cli.RemoverConta(ctx, id); err != nil {
			return fmt.Errorf("removendo %s: %w", chave, err)
		}
	}
	return nil
}
