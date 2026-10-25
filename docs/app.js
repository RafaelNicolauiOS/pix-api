// Front-end sem framework: fetch() + manipulação do DOM.
// Em Swift seria o equivalente a URLSession + atualizar a UI na main thread.

const API_PRODUCAO = "https://pix-api-vfy6.onrender.com"; // URL gerada pelo Render
const API_LOCAL = "http://localhost:8080";
const CHAVE_STORAGE = "pix-api-url";

const rodandoLocal = ["localhost", "127.0.0.1", ""].includes(location.hostname);
const brl = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

const $ = (id) => document.getElementById(id);

// ---------- URL da API (salva no navegador) ----------
function lerUrlSalva() {
  try { return localStorage.getItem(CHAVE_STORAGE); } catch { return null; }
}
function salvarUrl(url) {
  try { localStorage.setItem(CHAVE_STORAGE, url); } catch { /* modo privado: ignora */ }
}

let apiUrl = lerUrlSalva() || (rodandoLocal ? API_LOCAL : API_PRODUCAO);
$("api-url").value = apiUrl;

// ---------- Mensagens ----------
// Sempre textContent (nunca innerHTML com dados da API) para evitar injeção de HTML (XSS).
function mostrar(alvo, tipo, titulo, detalhes = []) {
  const el = $(alvo);
  el.className = `mensagem ${tipo}`;
  el.replaceChildren();
  const forte = document.createElement("strong");
  forte.textContent = titulo;
  el.append(forte);
  if (detalhes.length) {
    const ul = document.createElement("ul");
    for (const d of detalhes) {
      const li = document.createElement("li");
      li.textContent = d;
      ul.append(li);
    }
    el.append(ul);
  }
}

// Converte a resposta de erro (ProblemDetail, RFC 9457) em texto legível.
function mostrarProblema(alvo, problema = { title: "Erro inesperado" }) {
  const detalhes = [];
  if (problema.detail) detalhes.push(problema.detail);
  if (problema.erros) {
    for (const [campo, msg] of Object.entries(problema.erros)) detalhes.push(`${campo}: ${msg}`);
  }
  const titulo = `Erro ${problema.status ?? ""} — ${problema.title ?? "Falha na requisição"}`;
  mostrar(alvo, "erro", titulo, detalhes);
}

// ---------- Cliente HTTP ----------
class ErroApi extends Error {
  constructor(problema) { super(problema.detail || problema.title); this.problema = problema; }
}

async function api(caminho, opcoes = {}) {
  // O plano free do Render "dorme" sem uso e leva ~1 min para acordar: avisamos o usuário.
  const aviso = setTimeout(
    () => mostrar("msg-api", "info", "Acordando o servidor… (plano gratuito do Render pode levar até 1 minuto)"),
    3000);
  try {
    const resposta = await fetch(apiUrl + caminho, {
      ...opcoes,
      headers: { "Content-Type": "application/json", ...opcoes.headers },
    });
    if (resposta.status === 204) return null;
    const corpo = await resposta.json().catch(() => ({}));
    if (!resposta.ok) throw new ErroApi({ status: resposta.status, ...corpo });
    return corpo;
  } catch (e) {
    if (e instanceof ErroApi) throw e;
    // fetch só rejeita em falha de rede ou bloqueio de CORS.
    throw new ErroApi({
      title: "Sem conexão com a API",
      detail: `Não foi possível acessar ${apiUrl}. Verifique se a API está no ar e se esta origem (${location.origin}) está liberada no CORS.`,
    });
  } finally {
    clearTimeout(aviso);
    if ($("msg-api").classList.contains("info")) $("msg-api").replaceChildren();
  }
}

async function enviando(form, acao) {
  const botao = form.querySelector("button[type=submit]");
  botao.disabled = true;
  try { await acao(); } finally { botao.disabled = false; }
}

// ---------- Contas ----------
async function carregarContas() {
  const corpo = $("contas");
  try {
    const contas = await api("/contas");
    corpo.replaceChildren();
    if (!contas.length) {
      corpo.innerHTML = '<tr><td colspan="5">Nenhuma conta cadastrada.</td></tr>';
    }
    for (const c of contas) {
      const tr = document.createElement("tr");
      for (const [valor, classe] of [[c.id], [c.titular], [c.chavePix], [brl.format(c.saldo), "num"]]) {
        const td = document.createElement("td");
        td.textContent = valor;
        if (classe) td.className = classe;
        tr.append(td);
      }
      const acoes = document.createElement("td");
      const excluir = document.createElement("button");
      excluir.type = "button";
      excluir.className = "link";
      excluir.textContent = "Excluir";
      excluir.addEventListener("click", () => excluirConta(c));
      acoes.append(excluir);
      tr.append(acoes);
      corpo.append(tr);
    }
    $("chaves").replaceChildren(...contas.map((c) => new Option(c.titular, c.chavePix)));
    $("msg-contas").replaceChildren();
  } catch (e) {
    corpo.innerHTML = '<tr><td colspan="5">—</td></tr>';
    mostrarProblema("msg-contas", e.problema);
  }
}

async function excluirConta(conta) {
  if (!confirm(`Excluir a conta de ${conta.titular} (${conta.chavePix})?`)) return;
  try {
    await api(`/contas/${conta.id}`, { method: "DELETE" });
    mostrar("msg-contas", "ok", `Conta de ${conta.titular} excluída.`);
    await carregarContas();
  } catch (e) {
    mostrarProblema("msg-contas", e.problema);
  }
}

// ---------- Formulários ----------
$("form-conta").addEventListener("submit", (ev) => {
  ev.preventDefault();
  const form = ev.currentTarget;
  const dados = Object.fromEntries(new FormData(form));
  dados.saldo = Number(dados.saldo);
  enviando(form, async () => {
    try {
      const conta = await api("/contas", { method: "POST", body: JSON.stringify(dados) });
      mostrar("msg-conta", "ok", `Chave ${conta.chavePix} cadastrada para ${conta.titular} (id ${conta.id}).`);
      form.reset();
      await carregarContas();
    } catch (e) {
      mostrarProblema("msg-conta", e.problema);
    }
  });
});

$("form-transferencia").addEventListener("submit", (ev) => {
  ev.preventDefault();
  const form = ev.currentTarget;
  const dados = Object.fromEntries(new FormData(form));
  dados.valor = Number(dados.valor);
  enviando(form, async () => {
    try {
      const c = await api("/transferencias", { method: "POST", body: JSON.stringify(dados) });
      mostrar("msg-transferencia", "ok", `Transferência de ${brl.format(c.valor)} realizada!`, [
        `${c.chaveOrigem} → ${c.chaveDestino}`,
        `Novo saldo da origem: ${brl.format(c.saldoOrigem)}`,
        `Em ${new Date(c.realizadaEm).toLocaleString("pt-BR")}`,
      ]);
      form.elements.valor.value = "";
      await carregarContas();
    } catch (e) {
      mostrarProblema("msg-transferencia", e.problema);
    }
  });
});

$("form-api").addEventListener("submit", (ev) => {
  ev.preventDefault();
  apiUrl = $("api-url").value.replace(/\/+$/, "");
  salvarUrl(apiUrl);
  mostrar("msg-api", "ok", `Usando a API em ${apiUrl}`);
  carregarContas();
});

$("testar-api").addEventListener("click", async () => {
  try {
    const contas = await api("/contas");
    mostrar("msg-api", "ok", `Conectado a ${apiUrl} — ${contas.length} conta(s).`);
  } catch (e) {
    mostrarProblema("msg-api", e.problema);
  }
});

$("atualizar").addEventListener("click", carregarContas);

carregarContas();
