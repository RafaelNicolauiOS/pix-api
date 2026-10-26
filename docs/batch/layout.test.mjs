// Teste do leitor do RESULTADO contra o arquivo-ouro do COBOL.
// Rode: node --test docs/batch/layout.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { lerResultado, lerMovimento } from "./layout.js";

const raiz = new URL("../../cobol/testes/cenarios/", import.meta.url);
const ler = (cenario, arquivo) => readFileSync(new URL(`${cenario}/${arquivo}`, raiz), "latin1");

test("lê contas e resumo do cenário com divergências", () => {
  const { contas, resumo } = lerResultado(ler("02-divergencias", "RESULTADO.esperado"));

  assert.equal(contas.length, 5);
  const bruno = contas.find((c) => c.contaId === 2);
  assert.equal(bruno.chave, "bruno@pix.com");
  assert.equal(bruno.titular, "Bruno Lima");
  assert.equal(bruno.abertura, 25000);   // centavos
  assert.equal(bruno.creditosDia, 12050);
  assert.equal(bruno.debitosDia, 1000);
  assert.equal(bruno.posterior, 2000);
  assert.equal(bruno.calculado, 38050);
  assert.equal(bruno.saldoApi, 36050);
  assert.equal(bruno.diferenca, -2000);  // sinal separado à esquerda
  assert.equal(bruno.situacao, "DIVERGENTE");
  assert.equal(bruno.divergente, true);

  const encerrada = contas.find((c) => c.contaId === 3);
  assert.equal(encerrada.situacao, "ENCERRADA");
  assert.equal(encerrada.divergente, false);

  assert.equal(resumo.dataReferencia, "2026-10-25");
  assert.equal(resumo.processadoEm, "2026-10-26T03:00:07");
  assert.equal(resumo.qtdContas, 4);
  assert.equal(resumo.qtdLancamentos, 11);
  assert.equal(resumo.qtdAvaliadas, 5);
  assert.equal(resumo.qtdDivergentes, 2);
  assert.equal(resumo.creditosDia, 13050);
  assert.equal(resumo.debitosDia, 13050);
  assert.equal(resumo.rc, 4);
  assert.equal(resumo.situacao, "DIVERGENTE");
});

test("lê o motivo de um arquivo rejeitado", () => {
  const { contas, resumo } = lerResultado(ler("03-arquivo-truncado", "RESULTADO.esperado"));

  assert.equal(contas.length, 0);
  assert.equal(resumo.rc, 12);
  assert.equal(resumo.situacao, "REJEITADO");
  assert.equal(resumo.motivo, "TRAILER AUSENTE - ARQUIVO TRUNCADO");
});

test("conta registros do arquivo de movimento por tipo", () => {
  const mov = lerMovimento(ler("01-dia-normal", "MOVIMENTO.DAT"));

  assert.deepEqual(mov, { header: 1, contas: 3, lancamentos: 11, trailer: 1, total: 16 });
});
