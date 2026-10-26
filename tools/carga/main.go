// Comando carga: teste de carga e de consistência da pix-api.
//
// Cria contas exclusivas, dispara transferências concorrentes entre elas (incluindo A→B e B→A
// ao mesmo tempo) e verifica se nenhum centavo foi criado, perdido ou gasto duas vezes.
//
//	go run . -transferencias 2000 -concorrencia 100
package main

import (
	"context"
	"flag"
	"fmt"
	"net/url"
	"os"
	"os/signal"
	"sort"
	"time"
)

func main() {
	// flag: o "ArgumentParser" da biblioteca padrão do Go.
	api := flag.String("api", "http://localhost:8080", "URL base da pix-api")
	contas := flag.Int("contas", 5, "quantidade de contas (menos contas = mais disputa)")
	saldo := flag.Int64("saldo", 1000, "saldo inicial de cada conta, em reais")
	transferencias := flag.Int("transferencias", 1000, "total de transferências")
	concorrencia := flag.Int("concorrencia", 50, "requisições simultâneas")
	valorMax := flag.Int64("valor-max", 50, "valor máximo de cada transferência, em reais")
	semente := flag.Uint64("semente", uint64(time.Now().UnixNano()), "semente do sorteio (repita para reproduzir)")
	manter := flag.Bool("manter", false, "não remover as contas de teste ao final")
	permitirRemoto := flag.Bool("permitir-remoto", false, "permite rodar contra uma API que não é local")
	flag.Parse()

	if *contas < 2 || *transferencias < 1 || *concorrencia < 1 || *saldo < 0 || *valorMax < 1 {
		sair("parâmetros inválidos: são necessárias 2+ contas, 1+ transferência, concorrência 1+ e valor máximo 1+")
	}
	if !ehLocal(*api) && !*permitirRemoto {
		sair(fmt.Sprintf("%s não é local. Use -permitir-remoto para confirmar: a carga cria contas e "+
			"transferências reais nessa API (e é removida ao final, salvo com -manter).", *api))
	}

	cfg := Config{
		Contas:         *contas,
		SaldoInicial:   *saldo * 100,
		Transferencias: *transferencias,
		Concorrencia:   *concorrencia,
		ValorMaximo:    *valorMax * 100,
		Semente:        *semente,
	}

	// Ctrl-C cancela o contexto: as requisições em andamento são interrompidas com elegância.
	ctx, cancelar := signal.NotifyContext(context.Background(), os.Interrupt)
	defer cancelar()

	cli := NovoCliente(*api, cfg.Concorrencia)
	prefixo := fmt.Sprintf("%x", time.Now().UnixNano())[6:]

	fmt.Println("pix-api · teste de carga e consistência")
	fmt.Printf("API %s · %d contas × %s · %d transferências (até %s) · %d simultâneas · semente %d\n\n",
		*api, cfg.Contas, Reais(cfg.SaldoInicial), cfg.Transferencias, Reais(cfg.ValorMaximo), cfg.Concorrencia, cfg.Semente)

	res, err := Executar(ctx, cli, cfg, prefixo)
	if err != nil {
		limparSeNecessario(cli, res, *manter)
		sair(err.Error())
	}

	falhou := relatorio(res)
	limparSeNecessario(cli, res, *manter)

	if falhou {
		os.Exit(1) // código de saída ≠ 0 faz um pipeline de CI falhar
	}
}

// relatorio imprime os resultados e devolve true se algo deu errado.
func relatorio(res *Resultado) bool {
	falhou := false
	vazao := float64(len(res.Latencias)) / res.Duracao.Seconds()

	fmt.Printf("Resultados (%.2f s · %.0f transferências/s)\n", res.Duracao.Seconds(), vazao)
	fmt.Printf("  200 confirmadas ............ %d\n", res.PorStatus[200])
	fmt.Printf("  422 saldo insuficiente ..... %d\n", res.PorStatus[422])

	inesperados := []int{}
	for status := range res.PorStatus {
		if status != 200 && status != 422 {
			inesperados = append(inesperados, status)
		}
	}
	sort.Ints(inesperados)
	for _, status := range inesperados {
		fmt.Printf("  ❌ HTTP %d inesperado ...... %d\n", status, res.PorStatus[status])
		falhou = true
	}
	if res.ErrosDeRede > 0 {
		fmt.Printf("  ❌ erros de rede/timeout ... %d\n", res.ErrosDeRede)
		falhou = true
	}

	var movimentado int64
	for _, t := range res.Confirmadas {
		movimentado += t.Centavos
	}
	fmt.Printf("  valor movimentado .......... %s\n", Reais(movimentado))
	fmt.Printf("  latência p50 %v · p95 %v · p99 %v · máx %v\n\n",
		arredondar(Percentil(res.Latencias, 50)), arredondar(Percentil(res.Latencias, 95)),
		arredondar(Percentil(res.Latencias, 99)), arredondar(Percentil(res.Latencias, 100)))

	fmt.Println("Verificação de consistência")
	violacoes := Verificar(res.Iniciais, res.Confirmadas, res.Finais)
	if len(violacoes) == 0 {
		var total int64
		for _, s := range res.Finais {
			total += s
		}
		fmt.Printf("  ✅ %d contas: saldo final = inicial − enviado + recebido\n", len(res.Finais))
		fmt.Println("  ✅ nenhum saldo negativo")
		fmt.Printf("  ✅ soma dos saldos inalterada: %s\n", Reais(total))
	} else {
		for _, v := range violacoes {
			fmt.Printf("  ❌ %s\n", v)
		}
		falhou = true
	}
	if !falhou {
		fmt.Println("\nResultado: APROVADO — nenhum centavo criado, perdido ou gasto duas vezes.")
	} else {
		fmt.Println("\nResultado: REPROVADO")
	}
	return falhou
}

func limparSeNecessario(cli *Cliente, res *Resultado, manter bool) {
	if res == nil || len(res.IDs) == 0 {
		return
	}
	if manter {
		fmt.Printf("\nContas mantidas (prefixo %s).\n", primeiraChave(res.IDs))
		return
	}
	// Contexto novo: a limpeza roda mesmo se a carga foi interrompida com Ctrl-C.
	ctx, cancelar := context.WithTimeout(context.Background(), 2*time.Minute)
	defer cancelar()
	if err := Limpar(ctx, cli, res.IDs); err != nil {
		fmt.Fprintf(os.Stderr, "\naviso: limpeza incompleta: %v\n", err)
		return
	}
	fmt.Printf("\nLimpeza: %d contas de teste removidas.\n", len(res.IDs))
}

func primeiraChave(ids map[string]int64) string {
	for chave := range ids {
		return chave
	}
	return ""
}

func ehLocal(api string) bool {
	u, err := url.Parse(api)
	if err != nil {
		return false
	}
	host := u.Hostname()
	return host == "localhost" || host == "127.0.0.1" || host == "::1"
}

func arredondar(d time.Duration) time.Duration {
	return d.Round(100 * time.Microsecond)
}

func sair(mensagem string) {
	fmt.Fprintln(os.Stderr, "erro:", mensagem)
	os.Exit(2)
}
