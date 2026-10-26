// Painel do batch noturno: lê as saídas reais do programa COBOL e reproduz a execução.
import { lerResultado } from "./layout.js";

// Execuções reais: publicadas pelo GitHub Actions na branch batch-resultados.
// Demonstrações: execuções reais do mesmo job, rodadas localmente e versionadas em docs/batch/demos.
const FONTES = {
  reais: {
    base: "https://raw.githubusercontent.com/RafaelNicolauiOS/pix-api/batch-resultados/",
    descricao: "Rodadas pelo GitHub Actions todo dia às 03:00 contra a API em produção (Render).",
    vazio: "Ainda não há execuções noturnas publicadas. Veja as demonstrações enquanto isso.",
  },
  demos: {
    base: "batch/demos/",
    descricao: "Execuções reais do mesmo job, rodadas localmente: noite normal, lost update (API sem lock) e arquivo truncado.",
    vazio: "Nenhuma demonstração encontrada.",
  },
};

const $ = (id) => document.getElementById(id);
const brl = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const reais = (centavos) => brl.format(centavos / 100);
const semMovimento = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
const esperar = (ms) => new Promise((r) => setTimeout(r, semMovimento ? 0 : ms));

let fonteAtual = "reais";
let execucaoAtual = null;
let reproducao = 0; // incrementa para cancelar uma reprodução em andamento

// ---------------------------------------------------------------- utilidades
function el(tag, classe, texto) {
  const e = document.createElement(tag);
  if (classe) e.className = classe;
  if (texto !== undefined && texto !== null) e.textContent = texto; // dados nunca viram HTML
  return e;
}
function icone(id) {
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  const use = document.createElementNS("http://www.w3.org/2000/svg", "use");
  use.setAttribute("href", `#${id}`);
  svg.append(use);
  return svg;
}
async function buscar(url, tipo = "text") {
  const r = await fetch(url, { cache: "no-store" });
  if (!r.ok) throw new Error(`HTTP ${r.status} em ${url}`);
  return tipo === "json" ? r.json() : r.text();
}
const formatarData = (iso) => (iso ? new Date(`${iso}T12:00:00`).toLocaleDateString("pt-BR") : "—");
const formatarDataHora = (iso) => (iso ? new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "medium" }) : "—");
const ROTULO_RC = { 0: "OK", 4: "Divergências", 8: "Falha na extração", 12: "Rejeitado", 16: "Erro" };
const classeRc = (rc) => (rc === 0 ? "ok" : rc === 4 ? "aviso" : "erro");
const iconeRc = (rc) => (rc === 0 ? "i-check" : rc === 4 ? "i-alert" : "i-x");

function seloRc(rc, grande = false) {
  const s = el("span", `rc-selo ${classeRc(rc)}${grande ? " grande" : ""}`);
  s.append(icone(iconeRc(rc)), el("span", "", `RC ${String(rc).padStart(2, "0")}`));
  s.title = ROTULO_RC[rc] ?? "";
  return s;
}

// ---------------------------------------------------------------- fontes e lista
document.querySelectorAll(".abas [role=tab]").forEach((aba) => {
  aba.addEventListener("click", () => selecionarFonte(aba.dataset.fonte));
});

async function selecionarFonte(fonte, idDesejado) {
  fonteAtual = fonte;
  document.querySelectorAll(".abas [role=tab]").forEach((a) => {
    const ativa = a.dataset.fonte === fonte;
    a.setAttribute("aria-selected", String(ativa));
    a.tabIndex = ativa ? 0 : -1;
  });
  $("fonte-descricao").textContent = FONTES[fonte].descricao;
  const lista = $("lista-execucoes");
  lista.replaceChildren(el("li", "carregando", "Carregando execuções…"));
  $("lista-vazia").hidden = true;

  let execucoes = [];
  try {
    execucoes = (await buscar(`${FONTES[fonte].base}indice.json?t=${Date.now()}`, "json")).execucoes ?? [];
  } catch {
    execucoes = [];
  }
  lista.replaceChildren();
  if (!execucoes.length) {
    $("execucao").hidden = true;
    const vazio = $("lista-vazia");
    vazio.replaceChildren(el("strong", "", "Nada por aqui ainda"), el("p", "", FONTES[fonte].vazio));
    if (fonte === "reais") {
      const b = el("button", "btn secundario", "Ver demonstrações");
      b.type = "button";
      b.addEventListener("click", () => selecionarFonte("demos"));
      vazio.append(b);
    }
    vazio.hidden = false;
    return false;
  }
  for (const ex of execucoes) {
    const li = el("li");
    const b = el("button", "item-execucao");
    b.type = "button";
    b.dataset.id = ex.id;
    const topo = el("span", "item-topo");
    topo.append(el("strong", "", ex.titulo || `Movimento de ${formatarData(ex.dataReferencia)}`), seloRc(ex.rc));
    const meta = el("span", "item-meta",
      `${ex.origem === "noturno" ? "Noturno" : ex.origem === "demo" ? "Demonstração" : "Manual"} · ${formatarDataHora(ex.inicio)}`);
    b.append(topo, meta);
    b.addEventListener("click", () => abrirExecucao(ex.id, true));
    li.append(b);
    lista.append(li);
  }
  await abrirExecucao(idDesejado && execucoes.some((e) => e.id === idDesejado) ? idDesejado : execucoes[0].id, true);
  return true;
}

// ---------------------------------------------------------------- execução
async function abrirExecucao(id, reproduzir) {
  document.querySelectorAll(".item-execucao").forEach((b) => b.setAttribute("aria-current", String(b.dataset.id === id)));
  history.replaceState(null, "", `#${fonteAtual}/${id}`);
  const base = `${FONTES[fonteAtual].base}execucoes/${id}/`;
  try {
    const ex = await buscar(`${base}execucao.json`, "json");
    const [resultado, relatorio, joblog] = await Promise.all([
      ex.arquivos.includes("RESULTADO.DAT") ? buscar(`${base}RESULTADO.DAT`) : "",
      ex.arquivos.includes("RELATORIO.TXT") ? buscar(`${base}RELATORIO.TXT`) : "",
      ex.arquivos.includes("JOBLOG.TXT") ? buscar(`${base}JOBLOG.TXT`) : "",
    ]);
    execucaoAtual = { ex, base, ...lerResultado(resultado), relatorio, joblog: joblog.split("\n").filter(Boolean) };
    $("execucao").hidden = false;
    montarCabecalho();
    if (reproduzir && !semMovimento) await reproduzirExecucao();
    else mostrarEstadoFinal();
  } catch (e) {
    $("execucao").hidden = true;
    const vazio = $("lista-vazia");
    vazio.replaceChildren(el("strong", "", "Não foi possível carregar a execução"), el("p", "", e.message));
    vazio.hidden = false;
  }
}

function montarCabecalho() {
  const { ex, resumo } = execucaoAtual;
  $("rc-selo").replaceWith(Object.assign(seloRc(ex.rc, true), { id: "rc-selo" }));
  $("exec-titulo").textContent = ex.titulo || `Movimento de ${formatarData(ex.dataReferencia)}`;
  const duracao = ex.passos.reduce((s, p) => s + p.duracaoMs, 0);
  $("exec-meta").textContent = [
    `Data de referência ${formatarData(resumo?.dataReferencia ?? ex.dataReferencia)}`,
    `iniciado ${formatarDataHora(ex.inicio)}`,
    `duração ${(duracao / 1000).toLocaleString("pt-BR", { maximumFractionDigits: 1 })} s`,
  ].join(" · ");

  const downloads = $("downloads");
  downloads.replaceChildren();
  for (const arq of ex.arquivos) {
    const a = el("a", "btn texto", arq);
    a.href = `${execucaoAtual.base}${arq}`;
    a.download = arq;
    a.prepend(icone("i-download"));
    downloads.append(a);
  }
}

// Estado de cada etapa a partir do resultado final.
function estadoDasEtapas() {
  const { ex, resumo } = execucaoAtual;
  const passo = (n) => ex.passos.find((p) => p.nome === n);
  const extraiu = passo("STEP010")?.rc === 0;
  const rejeitado = resumo?.rc === 12;
  return {
    extrai: extraiu ? "ok" : "erro",
    valida: !extraiu ? "pulada" : rejeitado ? "erro" : "ok",
    sort: !extraiu || rejeitado ? "pulada" : "ok",
    concilia: !extraiu || rejeitado ? "pulada" : resumo?.qtdDivergentes ? "aviso" : "ok",
    publica: "ok",
  };
}
const TEXTO_STATUS = { ok: "concluída", aviso: "com divergências", erro: "falhou", pulada: "não executada", ativa: "em andamento…", pendente: "" };

function marcarEtapa(etapa, estado) {
  const li = $("pipeline").querySelector(`[data-etapa="${etapa}"]`);
  li.dataset.estado = estado;
  li.querySelector(".etapa-status").textContent = TEXTO_STATUS[estado];
}

function mostrarEstadoFinal() {
  const estados = estadoDasEtapas();
  for (const [etapa, estado] of Object.entries(estados)) marcarEtapa(etapa, estado);
  const log = $("joblog");
  log.textContent = execucaoAtual.joblog.join("\n");
  $("joblog-info").textContent = `${execucaoAtual.joblog.length} linhas`;
  montarKpis(false);
  montarGrafico();
  montarTabela();
  $("relatorio").textContent = execucaoAtual.relatorio || "(sem relatório)";
}

// Reproduz o job log linha a linha, acendendo as etapas conforme as mensagens do COBOL.
async function reproduzirExecucao() {
  const minha = ++reproducao;
  const estados = estadoDasEtapas();
  ["extrai", "valida", "sort", "concilia", "publica"].forEach((e) => marcarEtapa(e, "pendente"));
  const log = $("joblog");
  log.textContent = "";
  $("joblog-info").textContent = "executando…";
  montarKpis(true);
  $("grafico").replaceChildren();
  $("tabela-contas").replaceChildren();
  $("relatorio").textContent = "";
  $("reproduzir").disabled = true;

  for (const linha of execucaoAtual.joblog) {
    if (minha !== reproducao) return;
    if (/STEP010/.test(linha)) marcarEtapa("extrai", "ativa");
    if (/EXEC PGM=CONCILIA/.test(linha)) { marcarEtapa("extrai", estados.extrai); marcarEtapa("valida", "ativa"); }
    if (/ARQUIVO VALIDADO|ARQUIVO REJEITADO/.test(linha)) {
      marcarEtapa("valida", estados.valida);
      if (estados.sort !== "pulada") marcarEtapa("sort", "ativa");
    }
    if (/LANCAMENTOS NO RAZAO/.test(linha)) { await esperar(350); marcarEtapa("sort", estados.sort); marcarEtapa("concilia", "ativa"); }
    if (/STEP020 CONCILIA - RC=/.test(linha)) {
      ["sort", "concilia"].forEach((e) => marcarEtapa(e, estados[e]));
    }
    if (/STEP030/.test(linha)) marcarEtapa("publica", "ativa");
    if (/FALHA NA EXTRACAO/.test(linha)) marcarEtapa("extrai", "erro");

    log.textContent += (log.textContent ? "\n" : "") + linha;
    log.scrollTop = log.scrollHeight;
    await esperar(/DIVERGENTE|SEM RAZAO|SEM CADASTRO|REJEITADO/.test(linha) ? 420 : 160);
  }
  if (minha !== reproducao) return;
  for (const [etapa, estado] of Object.entries(estados)) marcarEtapa(etapa, estado);
  $("joblog-info").textContent = `${execucaoAtual.joblog.length} linhas`;
  montarKpis(false, true);
  montarGrafico();
  montarTabela();
  $("relatorio").textContent = execucaoAtual.relatorio || "(sem relatório)";
  $("reproduzir").disabled = false;
}
$("reproduzir").addEventListener("click", reproduzirExecucao);

// ---------------------------------------------------------------- indicadores
function montarKpis(vazio, animar = false) {
  const r = execucaoAtual.resumo;
  const kpis = [
    { rotulo: "Lançamentos no razão", valor: r?.qtdLancamentos ?? 0, fmt: (v) => v.toLocaleString("pt-BR") },
    { rotulo: "Contas avaliadas", valor: r?.qtdAvaliadas ?? 0, fmt: (v) => v.toLocaleString("pt-BR") },
    { rotulo: "Divergências", valor: r?.qtdDivergentes ?? 0, fmt: (v) => v.toLocaleString("pt-BR"),
      destaque: (r?.qtdDivergentes ?? 0) > 0 ? "aviso" : "ok" },
    { rotulo: "Créditos do dia", valor: r?.creditosDia ?? 0, fmt: reais },
    { rotulo: "Débitos do dia", valor: r?.debitosDia ?? 0, fmt: reais },
    { rotulo: "Contas no cadastro", valor: r?.qtdContas ?? 0, fmt: (v) => v.toLocaleString("pt-BR") },
  ];
  const caixa = $("kpis");
  caixa.replaceChildren();
  if (r?.rc === 12 && !vazio) {
    const aviso = el("div", "kpi-rejeitado");
    aviso.append(icone("i-x"), el("strong", "", "Arquivo rejeitado"), el("span", "", r.motivo));
    caixa.append(aviso);
  }
  for (const k of kpis) {
    const tile = el("div", `kpi${k.destaque && !vazio ? ` ${k.destaque}` : ""}`);
    const valor = el("strong", "kpi-valor", vazio ? "—" : k.fmt(k.valor));
    tile.append(el("span", "kpi-rotulo", k.rotulo), valor);
    caixa.append(tile);
    if (animar && !vazio && !semMovimento) contarAte(valor, k.valor, k.fmt);
  }
}
function contarAte(alvo, final, fmt) {
  const inicio = performance.now();
  const passo = (agora) => {
    const t = Math.min(1, (agora - inicio) / 700);
    alvo.textContent = fmt(Math.round(final * (1 - (1 - t) ** 3)));
    if (t < 1) requestAnimationFrame(passo);
  };
  requestAnimationFrame(passo);
}

// ---------------------------------------------------------------- gráfico (barras divergentes)
function montarGrafico() {
  const caixa = $("grafico");
  caixa.replaceChildren();
  const { contas, resumo } = execucaoAtual;
  const comDiferenca = contas.filter((c) => c.diferenca !== 0)
    .sort((a, b) => Math.abs(b.diferenca) - Math.abs(a.diferenca)).slice(0, 12);
  $("legenda-grafico").hidden = !comDiferenca.length;

  if (resumo?.rc === 12) {
    caixa.append(el("p", "grafico-vazio", "O arquivo foi rejeitado antes da conciliação: nenhuma conta foi comparada."));
    return;
  }
  if (!comDiferenca.length) {
    const ok = el("p", "grafico-vazio ok");
    ok.append(icone("i-check"), el("span", "", `Todas as ${contas.length} contas fecham no centavo: razão e saldos conferem.`));
    caixa.append(ok);
    return;
  }

  const ns = "http://www.w3.org/2000/svg";
  // Desenha na largura real do card: o texto do SVG fica no tamanho certo, sem encolher.
  const largura = Math.max(340, caixa.clientWidth || 720);
  const estreito = largura < 560;
  const rotuloL = estreito ? 96 : 180, valorL = estreito ? 92 : 140, alturaLinha = 34, barra = 20;
  const altura = comDiferenca.length * alturaLinha + 8;
  const svg = document.createElementNS(ns, "svg");
  svg.setAttribute("viewBox", `0 0 ${largura} ${altura}`);
  svg.setAttribute("role", "img");
  svg.setAttribute("aria-label", "Diferença entre o saldo da API e o saldo calculado pelo razão, por conta");
  const maximo = Math.max(...comDiferenca.map((c) => Math.abs(c.diferenca)));
  // Espaço para o rótulo de valor dos DOIS lados do zero, para nenhum texto invadir o nome da conta.
  const areaX0 = rotuloL + valorL, areaX1 = largura - valorL;
  const zero = (areaX0 + areaX1) / 2;
  const escala = (areaX1 - zero) / maximo;

  const eixo = document.createElementNS(ns, "line");
  Object.entries({ x1: zero, x2: zero, y1: 0, y2: altura, class: "eixo-zero" }).forEach(([k, v]) => eixo.setAttribute(k, v));
  svg.append(eixo);

  comDiferenca.forEach((c, i) => {
    const y = i * alturaLinha + (alturaLinha - barra) / 2 + 4;
    const comprimento = Math.max(2, Math.abs(c.diferenca) * escala);
    const positivo = c.diferenca > 0;
    const x = positivo ? zero : zero - comprimento;

    const nome = document.createElementNS(ns, "text");
    Object.entries({ x: rotuloL - 10, y: y + barra / 2, class: "rotulo-conta", "text-anchor": "end", "dominant-baseline": "middle" })
      .forEach(([k, v]) => nome.setAttribute(k, v));
    nome.textContent = estreito ? `#${c.contaId}` : `#${c.contaId} ${c.chave ? c.chave.slice(0, 18) : "(encerrada)"}`;
    svg.append(nome);

    // Retângulo com 4px de canto só na ponta do dado; quadrado na base (linha do zero).
    const r = 4, w = comprimento, h = barra;
    const caminho = positivo
      ? `M${x},${y} H${x + w - r} Q${x + w},${y} ${x + w},${y + r} V${y + h - r} Q${x + w},${y + h} ${x + w - r},${y + h} H${x} Z`
      : `M${x + w},${y} H${x + r} Q${x},${y} ${x},${y + r} V${y + h - r} Q${x},${y + h} ${x + r},${y + h} H${x + w} Z`;
    const forma = document.createElementNS(ns, "path");
    forma.setAttribute("d", caminho);
    forma.setAttribute("class", `barra ${positivo ? "pos" : "neg"}`);
    svg.append(forma);

    const valor = document.createElementNS(ns, "text");
    const xValor = positivo ? x + w + 8 : x - 8;
    Object.entries({ x: xValor, y: y + barra / 2, class: "valor-barra", "text-anchor": positivo ? "start" : "end", "dominant-baseline": "middle" })
      .forEach(([k, v]) => valor.setAttribute(k, v));
    valor.textContent = `${positivo ? "+" : "−"}${reais(Math.abs(c.diferenca))}${estreito ? "" : positivo ? " sobra" : " falta"}`;
    svg.append(valor);

    // Alvo de hover maior que a barra (a linha inteira).
    const alvo = document.createElementNS(ns, "rect");
    Object.entries({ x: 0, y: i * alturaLinha + 4, width: largura, height: alturaLinha, class: "alvo-hover" })
      .forEach(([k, v]) => alvo.setAttribute(k, v));
    alvo.addEventListener("mousemove", (ev) => mostrarTooltip(ev, c));
    alvo.addEventListener("mouseleave", esconderTooltip);
    svg.append(alvo);
  });

  caixa.append(svg);
  const outras = contas.filter((c) => c.diferenca !== 0).length - comDiferenca.length;
  if (outras > 0) caixa.append(el("p", "nota", `+ ${outras} conta(s) com diferença menor — veja a tabela.`));
}

let redesenho;
window.addEventListener("resize", () => {
  clearTimeout(redesenho);
  redesenho = setTimeout(() => { if (execucaoAtual && $("grafico").firstChild) montarGrafico(); }, 150);
});

function mostrarTooltip(ev, c) {
  const t = $("tooltip");
  t.replaceChildren(
    el("strong", "", `Conta #${c.contaId} · ${c.situacao}`),
    el("span", "", c.chave || "(conta encerrada)"),
    el("span", "", `Saldo pelo razão: ${reais(c.calculado)}`),
    el("span", "", `Saldo na API: ${reais(c.saldoApi)}`),
    el("span", "", `Diferença: ${c.diferenca > 0 ? "+" : ""}${reais(c.diferenca)}`),
  );
  t.hidden = false;
  const margem = 14;
  const x = Math.min(ev.clientX + margem, window.innerWidth - t.offsetWidth - 8);
  const y = Math.min(ev.clientY + margem, window.innerHeight - t.offsetHeight - 8);
  t.style.transform = `translate(${x}px, ${y}px)`;
}
function esconderTooltip() { $("tooltip").hidden = true; }

// ---------------------------------------------------------------- tabela
function montarTabela() {
  const corpo = $("tabela-contas");
  corpo.replaceChildren();
  const so = $("so-divergentes").checked;
  const contas = execucaoAtual.contas.filter((c) => !so || c.divergente);
  if (!contas.length) {
    const tr = el("tr");
    const td = el("td", "vazio", execucaoAtual.resumo?.rc === 12
      ? "Arquivo rejeitado: nenhuma conta foi conciliada." : "Nenhuma conta divergente.");
    td.colSpan = 10;
    tr.append(td);
    corpo.append(tr);
    return;
  }
  for (const c of contas) {
    const tr = el("tr", c.divergente ? "linha-divergente" : "");
    const situacao = el("td");
    const selo = el("span", `selo-situacao ${c.divergente ? "erro" : c.situacao === "ENCERRADA" ? "neutro" : "ok"}`);
    selo.append(icone(c.divergente ? "i-alert" : "i-check"), el("span", "", c.situacao));
    situacao.append(selo);
    tr.append(
      el("td", "num", `#${c.contaId}`),
      el("td", "chave", c.chave || "(encerrada)"),
      el("td", "num", reais(c.abertura)),
      el("td", "num", reais(c.creditosDia)),
      el("td", "num", reais(c.debitosDia)),
      el("td", "num", reais(c.posterior)),
      el("td", "num forte", reais(c.calculado)),
      el("td", "num forte", reais(c.saldoApi)),
      el("td", `num ${c.diferenca ? "dif" : ""}`, `${c.diferenca > 0 ? "+" : ""}${reais(c.diferenca)}`),
      situacao,
    );
    corpo.append(tr);
  }
}
$("so-divergentes").addEventListener("change", montarTabela);

// ---------------------------------------------------------------- início
// #reais/<id> ou #demos/<id> abre direto uma execução (link compartilhável).
const [fonteHash, idHash] = location.hash.slice(1).split("/");
if (fonteHash === "demos") {
  selecionarFonte("demos", idHash);
} else {
  const temReais = await selecionarFonte("reais", idHash);
  if (!temReais && fonteHash !== "reais") selecionarFonte("demos");
}
