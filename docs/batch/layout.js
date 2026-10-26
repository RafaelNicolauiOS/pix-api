// Leitor dos arquivos de largura fixa do batch COBOL.
// As posições abaixo espelham os copybooks cobol/copybooks/RESULTADO.cpy e MOVIMENTO.cpy:
// se o layout mudar lá, muda aqui. Valores monetários ficam em CENTAVOS (inteiros).

const TAMANHO_RESULTADO = 200;

// [início, fim] em posições "de mainframe": 1-based e inclusivas.
const CAMPOS_CONTA = {
  contaId: [2, 11], chave: [12, 41], titular: [42, 71],
  abertura: [72, 87], creditosDia: [88, 102], debitosDia: [103, 117], posterior: [118, 133],
  calculado: [134, 149], saldoApi: [150, 164], diferenca: [165, 180], situacao: [181, 192],
};
const CAMPOS_RESUMO = {
  dataReferencia: [2, 9], geradoEm: [10, 23], processadoEm: [24, 37],
  qtdContas: [38, 46], qtdLancamentos: [47, 55], qtdAvaliadas: [56, 64], qtdDivergentes: [65, 73],
  creditosDia: [74, 90], debitosDia: [91, 107], rc: [108, 109], situacao: [110, 119], motivo: [120, 179],
};
const SITUACOES_DIVERGENTES = new Set(["DIVERGENTE", "SEM RAZAO", "SEM CADASTRO"]);

const campo = (linha, [de, ate]) => linha.slice(de - 1, ate);
const texto = (linha, pos) => campo(linha, pos).trim();
const inteiro = (linha, pos) => parseInt(campo(linha, pos), 10) || 0;
// PIC S9(13)V99 SIGN LEADING SEPARATE: "+000000000002000" → 2000 centavos; "-..." → negativo.
const comSinal = (linha, pos) => {
  const bruto = campo(linha, pos);
  return (bruto[0] === "-" ? -1 : 1) * (parseInt(bruto.slice(1), 10) || 0);
};

// AAAAMMDD → AAAA-MM-DD | AAAAMMDDhhmmss → AAAA-MM-DDThh:mm:ss
const data = (d) => (/^\d{8}$/.test(d) && d !== "00000000" ? `${d.slice(0, 4)}-${d.slice(4, 6)}-${d.slice(6, 8)}` : null);
const dataHora = (t) => (/^\d{14}$/.test(t) && !/^0+$/.test(t)
  ? `${data(t.slice(0, 8))}T${t.slice(8, 10)}:${t.slice(10, 12)}:${t.slice(12, 14)}` : null);

export function lerResultado(conteudo) {
  const contas = [];
  let resumo = null;
  for (const bruta of conteudo.split(/\r?\n/)) {
    if (!bruta.trim()) continue;
    // O GnuCOBOL remove espaços no fim das linhas; completamos para o tamanho do registro.
    const linha = bruta.padEnd(TAMANHO_RESULTADO);
    if (linha[0] === "A") {
      const situacao = texto(linha, CAMPOS_CONTA.situacao);
      contas.push({
        contaId: inteiro(linha, CAMPOS_CONTA.contaId),
        chave: texto(linha, CAMPOS_CONTA.chave),
        titular: texto(linha, CAMPOS_CONTA.titular),
        abertura: comSinal(linha, CAMPOS_CONTA.abertura),
        creditosDia: inteiro(linha, CAMPOS_CONTA.creditosDia),
        debitosDia: inteiro(linha, CAMPOS_CONTA.debitosDia),
        posterior: comSinal(linha, CAMPOS_CONTA.posterior),
        calculado: comSinal(linha, CAMPOS_CONTA.calculado),
        saldoApi: inteiro(linha, CAMPOS_CONTA.saldoApi),
        diferenca: comSinal(linha, CAMPOS_CONTA.diferenca),
        situacao,
        divergente: SITUACOES_DIVERGENTES.has(situacao),
      });
    } else if (linha[0] === "R") {
      resumo = {
        dataReferencia: data(campo(linha, CAMPOS_RESUMO.dataReferencia)),
        geradoEm: dataHora(campo(linha, CAMPOS_RESUMO.geradoEm)),
        processadoEm: dataHora(campo(linha, CAMPOS_RESUMO.processadoEm)),
        qtdContas: inteiro(linha, CAMPOS_RESUMO.qtdContas),
        qtdLancamentos: inteiro(linha, CAMPOS_RESUMO.qtdLancamentos),
        qtdAvaliadas: inteiro(linha, CAMPOS_RESUMO.qtdAvaliadas),
        qtdDivergentes: inteiro(linha, CAMPOS_RESUMO.qtdDivergentes),
        creditosDia: inteiro(linha, CAMPOS_RESUMO.creditosDia),
        debitosDia: inteiro(linha, CAMPOS_RESUMO.debitosDia),
        rc: inteiro(linha, CAMPOS_RESUMO.rc),
        situacao: texto(linha, CAMPOS_RESUMO.situacao),
        motivo: texto(linha, CAMPOS_RESUMO.motivo),
      };
    }
  }
  return { contas, resumo };
}

// Quantos registros de cada tipo o MOVIMENTO tem (H, C, L, T).
export function lerMovimento(conteudo) {
  const r = { header: 0, contas: 0, lancamentos: 0, trailer: 0, total: 0 };
  for (const linha of conteudo.split(/\r?\n/)) {
    if (!linha) continue;
    r.total++;
    if (linha[0] === "H") r.header++;
    else if (linha[0] === "C") r.contas++;
    else if (linha[0] === "L") r.lancamentos++;
    else if (linha[0] === "T") r.trailer++;
  }
  return r;
}
