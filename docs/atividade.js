// Página de atividade: feed de eventos (GET /eventos) e logs do servidor (GET /logs, com token).

const API_PRODUCAO = "https://pix-api-vfy6.onrender.com";
const API_LOCAL = "http://localhost:8080";
const rodandoLocal = ["localhost", "127.0.0.1", ""].includes(location.hostname);
const INTERVALO_MS = 5000;

const $ = (id) => document.getElementById(id);
const brl = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

// Mesma URL configurada na página inicial (⚙️), se houver.
function lerArmazenamento(armazenamento, chave) {
  try { return armazenamento.getItem(chave); } catch { return null; }
}
function gravarArmazenamento(armazenamento, chave, valor) {
  try {
    if (valor === null) armazenamento.removeItem(chave);
    else armazenamento.setItem(chave, valor);
  } catch { /* modo privado */ }
}
const urlSalva = lerArmazenamento(localStorage, "pix-api-url");
const apiUrl = urlSalva && urlSalva !== "https://pix-api.onrender.com" ? urlSalva : (rodandoLocal ? API_LOCAL : API_PRODUCAO);

// O token fica no sessionStorage: vale só para esta aba e some quando ela é fechada.
let token = lerArmazenamento(sessionStorage, "pix-admin-token");

function definirStatus(estado) {
  const textos = { online: "Online", acordando: "Acordando…", offline: "Offline", carregando: "Conectando…" };
  $("status-conexao").dataset.estado = estado;
  $("status-conexao").querySelector(".status-texto").textContent = textos[estado];
}

async function api(caminho, headers = {}) {
  const lento = setTimeout(() => definirStatus("acordando"), 3000);
  try {
    const resposta = await fetch(apiUrl + caminho, { headers });
    definirStatus("online");
    const corpo = await resposta.json().catch(() => ({}));
    if (!resposta.ok) throw Object.assign(new Error(corpo.detail || corpo.title), { status: resposta.status, corpo });
    return corpo;
  } catch (e) {
    if (!e.status) definirStatus("offline");
    throw e;
  } finally {
    clearTimeout(lento);
  }
}

function icone(id) {
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  const use = document.createElementNS("http://www.w3.org/2000/svg", "use");
  use.setAttribute("href", `#${id}`);
  svg.append(use);
  return svg;
}

function el(tag, classe, texto) {
  const e = document.createElement(tag);
  if (classe) e.className = classe;
  if (texto !== undefined) e.textContent = texto; // sempre textContent: dados da API nunca viram HTML
  return e;
}

const tempoRelativo = new Intl.RelativeTimeFormat("pt-BR", { numeric: "auto" });
function haQuanto(instante) {
  const segundos = Math.round((new Date(instante) - Date.now()) / 1000);
  if (Math.abs(segundos) < 60) return tempoRelativo.format(segundos, "second");
  if (Math.abs(segundos) < 3600) return tempoRelativo.format(Math.round(segundos / 60), "minute");
  if (Math.abs(segundos) < 86400) return tempoRelativo.format(Math.round(segundos / 3600), "hour");
  return new Date(instante).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}

// ---------- Abas ----------
const abas = [$("aba-eventos"), $("aba-logs")];
function selecionarAba(aba) {
  for (const a of abas) {
    const ativa = a === aba;
    a.setAttribute("aria-selected", String(ativa));
    a.tabIndex = ativa ? 0 : -1;
    $(a.getAttribute("aria-controls")).hidden = !ativa;
  }
  history.replaceState(null, "", aba.id === "aba-logs" ? "#logs" : "#eventos");
  if (aba.id === "aba-logs") iniciarLogs();
}
abas.forEach((a, i) => {
  a.addEventListener("click", () => selecionarAba(a));
  a.addEventListener("keydown", (ev) => { // setas do teclado trocam de aba
    if (ev.key === "ArrowRight" || ev.key === "ArrowLeft") {
      const proxima = abas[(i + (ev.key === "ArrowRight" ? 1 : abas.length - 1)) % abas.length];
      proxima.focus();
      selecionarAba(proxima);
    }
  });
});

// ---------- Eventos ----------
const idsVistos = new Set();
let nomesPorChave = new Map();

async function carregarEventos() {
  try {
    const [eventos, contas] = await Promise.all([api("/eventos"), api("/contas").catch(() => [])]);
    nomesPorChave = new Map(contas.map((c) => [c.chavePix, c.titular]));
    renderizarEventos(eventos);
  } catch (e) {
    $("eventos-info").textContent = "Não foi possível carregar os eventos.";
    mostrarVazio(e.status ? (e.corpo.title || "Erro na API") : "Sem conexão com a API",
      e.status ? e.message : `Não foi possível acessar ${apiUrl}.`);
  }
}

function renderizarEventos(eventos) {
  const lista = $("feed-eventos");
  const primeiraCarga = idsVistos.size === 0;
  lista.replaceChildren();
  $("eventos-info").textContent = eventos.length
    ? `${eventos.length} ${eventos.length === 1 ? "transferência" : "transferências"} · atualizado às ${new Date().toLocaleTimeString("pt-BR")}`
    : `Atualizado às ${new Date().toLocaleTimeString("pt-BR")}`;
  if (!eventos.length) {
    mostrarVazio("Nenhuma transferência ainda", "Faça um Pix na página inicial e ele aparece aqui em segundos.");
    return;
  }
  $("eventos-vazio").hidden = true;

  for (const ev of eventos) {
    const novo = !primeiraCarga && !idsVistos.has(ev.id);
    idsVistos.add(ev.id);
    const li = el("li", "evento" + (novo ? " novo" : ""));

    const iconeEl = el("span", "evento-icone");
    iconeEl.append(icone("i-pix"));

    const info = el("div", "evento-info");
    const titulo = el("div", "evento-titulo");
    titulo.append(el("span", "", nomesPorChave.get(ev.chaveOrigem) ?? ev.chaveOrigem), icone("i-arrow"),
      el("span", "", nomesPorChave.get(ev.chaveDestino) ?? ev.chaveDestino));
    const sub = el("div", "evento-sub", `${ev.chaveOrigem} → ${ev.chaveDestino} · id ${ev.id.slice(0, 8)}`);
    sub.title = ev.id;
    info.append(titulo, sub);

    const lado = el("div", "evento-lado");
    const tempo = el("div", "evento-tempo", haQuanto(ev.realizadaEm));
    tempo.title = new Date(ev.realizadaEm).toLocaleString("pt-BR");
    lado.append(el("div", "evento-valor", brl.format(ev.valor)), tempo);

    li.append(iconeEl, info, lado);
    lista.append(li);
  }
}

function mostrarVazio(titulo, texto) {
  $("feed-eventos").replaceChildren();
  const vazio = $("eventos-vazio");
  vazio.replaceChildren(el("strong", "", titulo), el("p", "", texto));
  vazio.hidden = false;
}

$("eventos-atualizar").addEventListener("click", carregarEventos);

// ---------- Logs ----------
let linhasLog = [];
let filtroNivel = "";
let logsIniciados = false;

function iniciarLogs() {
  if (logsIniciados) return;
  logsIniciados = true;
  if (token) carregarLogs(); else mostrarFormularioToken();
}

function mostrarFormularioToken(erro) {
  $("area-logs").hidden = true;
  $("form-token").hidden = false;
  const caixa = $("token-erro");
  caixa.hidden = !erro;
  if (erro) {
    caixa.replaceChildren(icone("i-alert"), el("div", "", erro));
  }
  $("token").focus();
}

$("form-token").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  token = $("token").value.trim();
  const botao = ev.currentTarget.querySelector("button[type=submit]");
  botao.disabled = true;
  await carregarLogs();
  botao.disabled = false;
});

$("logs-sair").addEventListener("click", () => {
  token = null;
  gravarArmazenamento(sessionStorage, "pix-admin-token", null);
  $("token").value = "";
  mostrarFormularioToken();
});

async function carregarLogs() {
  try {
    linhasLog = await api("/logs?limite=500", { "X-Admin-Token": token });
    gravarArmazenamento(sessionStorage, "pix-admin-token", token);
    $("form-token").hidden = true;
    $("area-logs").hidden = false;
    renderizarLogs();
  } catch (e) {
    if (e.status === 401) {
      token = null;
      gravarArmazenamento(sessionStorage, "pix-admin-token", null);
      mostrarFormularioToken("Token inválido. Confira o valor de PIX_ADMIN_TOKEN no Render.");
    } else if (e.status === 404) {
      mostrarFormularioToken("Os logs estão desativados nesta API (PIX_ADMIN_TOKEN não configurado).");
    } else if (!$("area-logs").hidden) {
      $("logs-info").textContent = `Falha ao atualizar: ${e.message || "sem conexão"}. Tentando de novo…`;
    } else {
      mostrarFormularioToken(`Sem conexão com ${apiUrl}.`);
    }
  }
}

function renderizarLogs() {
  const consoleEl = $("console");
  const colado = consoleEl.scrollHeight - consoleEl.scrollTop - consoleEl.clientHeight < 40;
  const busca = $("logs-busca").value.trim().toLowerCase();
  const visiveis = linhasLog.filter((l) =>
    (!filtroNivel || l.nivel === filtroNivel) &&
    (!busca || `${l.logger} ${l.mensagem}`.toLowerCase().includes(busca)));

  consoleEl.replaceChildren();
  if (!visiveis.length) {
    consoleEl.append(el("div", "console-vazio", linhasLog.length ? "Nenhuma linha com esse filtro." : "Nenhum log ainda."));
  }
  for (const l of visiveis) {
    const linha = el("div", `linha ${l.nivel}`);
    linha.append(
      el("span", "linha-hora", new Date(l.instante).toLocaleTimeString("pt-BR")),
      el("span", `linha-nivel ${l.nivel}`, l.nivel),
      el("span", "linha-logger", l.logger),
      el("span", "linha-msg", l.mensagem),
    );
    linha.title = l.logger; // nome do logger ao passar o mouse (a coluna some em telas menores)
    consoleEl.append(linha);
  }
  // Como um "tail -f": acompanha o fim, a não ser que você tenha rolado para cima para ler.
  if (colado) consoleEl.scrollTop = consoleEl.scrollHeight;

  const erros = linhasLog.filter((l) => l.nivel === "ERROR").length;
  $("logs-info").textContent = `${visiveis.length} de ${linhasLog.length} linhas (as últimas 500 desde o início da API)` +
    (erros ? ` · ${erros} erro(s)` : "") + ` · atualizado às ${new Date().toLocaleTimeString("pt-BR")}`;
}

document.querySelectorAll(".chip").forEach((chip) => chip.addEventListener("click", () => {
  filtroNivel = chip.dataset.nivel;
  document.querySelectorAll(".chip").forEach((c) => c.setAttribute("aria-pressed", String(c === chip)));
  renderizarLogs();
}));
$("logs-busca").addEventListener("input", renderizarLogs);

// ---------- Atualização automática ----------
// Só busca quando a aba do navegador está visível, para não gastar a API à toa.
setInterval(() => {
  if (document.hidden) return;
  const naAbaLogs = !$("painel-logs").hidden;
  if (!naAbaLogs && $("eventos-auto").checked) carregarEventos();
  if (naAbaLogs && token && !$("area-logs").hidden && $("logs-auto").checked) carregarLogs();
}, INTERVALO_MS);

carregarEventos();
if (location.hash === "#logs") selecionarAba($("aba-logs"));
