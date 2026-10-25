// Front-end sem framework: fetch() + manipulação do DOM.
// Em Swift seria o equivalente a URLSession + atualizar a UI na main thread.

const API_PRODUCAO = "https://pix-api-vfy6.onrender.com"; // URL gerada pelo Render
const API_LOCAL = "http://localhost:8080";
const rodandoLocal = ["localhost", "127.0.0.1", ""].includes(location.hostname);
const API_PADRAO = rodandoLocal ? API_LOCAL : API_PRODUCAO;

const brl = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const $ = (id) => document.getElementById(id);

// ---------- Preferências salvas no navegador (como UserDefaults no iOS) ----------
const prefs = {
  ler(chave) { try { return localStorage.getItem(chave); } catch { return null; } },
  gravar(chave, valor) { try { localStorage.setItem(chave, valor); } catch { /* modo privado */ } },
  apagar(chave) { try { localStorage.removeItem(chave); } catch { /* modo privado */ } },
};

// Ignora a URL antiga (pix-api.onrender.com), que pode ter ficado salva de versões anteriores da página.
const urlSalva = prefs.ler("pix-api-url");
let apiUrl = urlSalva && urlSalva !== "https://pix-api.onrender.com" ? urlSalva : API_PADRAO;
let contas = [];

// ---------- Utilidades de texto ----------
function iniciais(nome) {
  const partes = nome.trim().split(/\s+/);
  return ((partes[0]?.[0] ?? "") + (partes.length > 1 ? partes.at(-1)[0] : "")).toUpperCase();
}

// Cor estável por nome, para cada pessoa ter sempre o mesmo avatar.
function corDoAvatar(texto) {
  let h = 0;
  for (const c of texto) h = (h * 31 + c.charCodeAt(0)) % 360;
  return `hsl(${h} 55% 42%)`;
}

function criarAvatar(nome, extraClasse = "") {
  const span = document.createElement("span");
  span.className = `avatar ${extraClasse}`.trim();
  span.style.setProperty("--cor", corDoAvatar(nome));
  span.textContent = iniciais(nome);
  return span;
}

function mascararCpf(cpf) {
  return `***.${cpf.slice(3, 6)}.${cpf.slice(6, 9)}-**`;
}

function formatarCpfDigitado(valor) {
  const d = valor.replace(/\D/g, "").slice(0, 11);
  return d.replace(/(\d{3})(\d)/, "$1.$2").replace(/(\d{3})(\d)/, "$1.$2").replace(/(\d{3})(\d{1,2})$/, "$1-$2");
}

// Campo de dinheiro estilo app de banco: os dígitos entram pelos centavos (R$ 0,01 → R$ 0,12 → R$ 1,23).
function campoDinheiro(input) {
  const atualizar = (centavos) => {
    input.dataset.centavos = String(centavos);
    input.value = brl.format(centavos / 100);
  };
  atualizar(Math.round(parseFloat(input.value.replace(/[^\d,]/g, "").replace(",", ".") || "0") * 100));
  input.addEventListener("input", () => {
    const digitos = input.value.replace(/\D/g, "").slice(0, 13);
    atualizar(parseInt(digitos || "0", 10));
  });
  input.addEventListener("focus", () => requestAnimationFrame(() => input.setSelectionRange(input.value.length, input.value.length)));
  return {
    get valor() { return Number(input.dataset.centavos) / 100; },
    definir(reais) { atualizar(Math.round(reais * 100)); },
  };
}

// ---------- Toasts ----------
function toast(mensagem, tipo = "ok", duracao = 3500) {
  const el = document.createElement("div");
  el.className = `toast ${tipo}`;
  if (tipo === "carregando") {
    const s = document.createElement("span");
    s.className = "spinner";
    el.append(s);
  } else {
    el.append(icone(tipo === "erro" ? "i-alert" : "i-check"));
  }
  const texto = document.createElement("span");
  texto.textContent = mensagem;
  el.append(texto);
  $("toasts").append(el);
  if (duracao) setTimeout(() => el.remove(), duracao);
  return el;
}

function icone(id) {
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  const use = document.createElementNS("http://www.w3.org/2000/svg", "use");
  use.setAttribute("href", `#${id}`);
  svg.append(use);
  return svg;
}

// ---------- Status da conexão ----------
function definirStatus(estado) {
  const textos = { online: "Online", acordando: "Acordando…", offline: "Offline", carregando: "Conectando…" };
  $("status-conexao").dataset.estado = estado;
  $("status-conexao").querySelector(".status-texto").textContent = textos[estado];
}

// ---------- Cliente HTTP ----------
class ErroApi extends Error {
  constructor(problema) { super(problema.detail || problema.title); this.problema = problema; }
}

async function api(caminho, opcoes = {}) {
  // O plano free do Render "dorme" sem uso e leva ~1 min para acordar: avisamos o usuário.
  let avisoAcordando = null;
  const timer = setTimeout(() => {
    definirStatus("acordando");
    avisoAcordando = toast("Acordando o servidor… pode levar até 1 minuto.", "carregando", 0);
  }, 3000);
  try {
    const resposta = await fetch(apiUrl + caminho, {
      ...opcoes,
      headers: { "Content-Type": "application/json", ...opcoes.headers },
    });
    definirStatus("online");
    if (resposta.status === 204) return null;
    const corpo = await resposta.json().catch(() => ({}));
    if (!resposta.ok) throw new ErroApi({ status: resposta.status, ...corpo });
    return corpo;
  } catch (e) {
    if (e instanceof ErroApi) throw e;
    // fetch só rejeita em falha de rede ou bloqueio de CORS.
    definirStatus("offline");
    throw new ErroApi({
      title: "Sem conexão com o servidor",
      detail: `Não foi possível acessar ${apiUrl}. Verifique se a API está no ar ou ajuste a URL nas configurações.`,
    });
  } finally {
    clearTimeout(timer);
    avisoAcordando?.remove();
  }
}

// Textos amigáveis para cada tipo de erro (ProblemDetail, RFC 9457).
function traduzirProblema(p = {}) {
  const detalhes = p.erros ? Object.entries(p.erros).map(([campo, msg]) => `${rotuloCampo(campo)}: ${msg}`) : [];
  const titulos = {
    400: "Confira os dados",
    404: p.title === "Chave Pix não encontrada" ? "Chave Pix não encontrada" : "Não encontrado",
    409: "Essa chave já existe",
    422: p.title || "Operação não permitida",
  };
  return {
    titulo: titulos[p.status] || p.title || "Algo deu errado",
    detalhe: detalhes.length ? null : p.detail,
    detalhes,
    status: p.status,
  };
}

function rotuloCampo(campo) {
  return { titular: "Nome", cpf: "CPF", chavePix: "Chave Pix", saldo: "Saldo", valor: "Valor",
    chaveOrigem: "Conta de origem", chaveDestino: "Chave de destino" }[campo] || campo;
}

function mostrarCaixaErro(el, problema) {
  const { titulo, detalhe, detalhes } = traduzirProblema(problema);
  el.replaceChildren(icone("i-alert"));
  const corpo = document.createElement("div");
  const forte = document.createElement("strong");
  forte.textContent = titulo;
  corpo.append(forte);
  if (detalhe) {
    const p = document.createElement("div");
    p.textContent = detalhe;
    corpo.append(p);
  }
  if (detalhes.length) {
    const ul = document.createElement("ul");
    detalhes.forEach((d) => { const li = document.createElement("li"); li.textContent = d; ul.append(li); });
    corpo.append(ul);
  }
  el.append(corpo);
  el.hidden = false;
}

// ---------- Dialogs (sheets) ----------
function abrir(dialogo) {
  dialogo.showModal();
}
document.querySelectorAll("dialog").forEach((d) => {
  d.addEventListener("click", (ev) => { if (ev.target === d) d.close(); }); // clique fora fecha
  d.querySelectorAll("[data-fechar]").forEach((b) => b.addEventListener("click", () => d.close()));
});

// ---------- Saldo e lista de contas ----------
function renderizarSaldo() {
  const total = contas.reduce((soma, c) => soma + Number(c.saldo), 0);
  $("saldo-total").textContent = brl.format(total);
  $("saldo-sub").textContent = `${contas.length} ${contas.length === 1 ? "conta" : "contas"} · atualizado às ${new Date().toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}`;
}

function renderizarContas(destacarId) {
  const lista = $("lista-contas");
  const estado = $("estado-contas");
  lista.replaceChildren();
  $("contador-contas").textContent = contas.length ? String(contas.length) : "";
  lista.hidden = contas.length === 0;
  estado.hidden = contas.length > 0;
  if (!contas.length) {
    mostrarEstado("Nenhuma conta ainda", "Cadastre a primeira chave Pix para começar.", "Cadastrar chave", abrirCadastro);
    return;
  }
  for (const c of contas) {
    const li = document.createElement("li");
    li.className = "conta-item" + (c.id === destacarId ? " nova" : "");
    const botao = document.createElement("button");
    botao.type = "button";
    botao.setAttribute("aria-label", `${c.titular}, ${brl.format(c.saldo)}`);

    const info = document.createElement("span");
    info.className = "conta-info";
    const nome = document.createElement("div");
    nome.className = "conta-nome";
    nome.textContent = c.titular;
    const chave = document.createElement("div");
    chave.className = "conta-chave";
    chave.textContent = c.chavePix;
    info.append(nome, chave);

    const saldo = document.createElement("span");
    saldo.className = "conta-saldo";
    saldo.dataset.valor = "";
    saldo.textContent = brl.format(c.saldo);

    botao.append(criarAvatar(c.titular), info, saldo, icone("i-chevron"));
    botao.addEventListener("click", () => abrirConta(c));
    li.append(botao);
    lista.append(li);
  }
}

function mostrarEstado(titulo, texto, rotuloBotao, acao) {
  const estado = $("estado-contas");
  estado.replaceChildren();
  const forte = document.createElement("strong");
  forte.textContent = titulo;
  const p = document.createElement("p");
  p.textContent = texto;
  const botao = document.createElement("button");
  botao.type = "button";
  botao.className = "btn secundario";
  botao.textContent = rotuloBotao;
  botao.addEventListener("click", acao);
  estado.append(forte, p, botao);
  estado.hidden = false;
}

async function carregarContas(destacarId) {
  try {
    contas = await api("/contas");
    renderizarSaldo();
    renderizarContas(destacarId);
    return true;
  } catch (e) {
    $("lista-contas").hidden = true;
    $("saldo-total").textContent = "—";
    $("saldo-sub").textContent = "Não foi possível carregar";
    const { titulo, detalhe } = traduzirProblema(e.problema);
    mostrarEstado(titulo, detalhe, "Tentar de novo", () => carregarContas());
    return false;
  }
}

// ---------- Ocultar valores ----------
function aplicarOcultar(ocultar) {
  document.body.classList.toggle("ocultar", ocultar);
  const botao = $("alternar-saldo");
  botao.setAttribute("aria-pressed", String(ocultar));
  botao.setAttribute("aria-label", ocultar ? "Mostrar valores" : "Ocultar valores");
  botao.querySelector("use").setAttribute("href", ocultar ? "#i-eye-off" : "#i-eye");
}
$("alternar-saldo").addEventListener("click", () => {
  const ocultar = !document.body.classList.contains("ocultar");
  aplicarOcultar(ocultar);
  prefs.gravar("pix-ocultar", ocultar ? "1" : "0");
});
aplicarOcultar(prefs.ler("pix-ocultar") === "1");

// ---------- Fluxo de Pix: dados → confirmação → comprovante ----------
const valorPix = campoDinheiro($("pix-valor"));
let pixPendente = null;

function etapaPix(nome) {
  for (const etapa of ["dados", "confirmar", "resultado"]) $(`pix-etapa-${etapa}`).hidden = etapa !== nome;
  $("pix-voltar").hidden = nome !== "confirmar";
  $("pix-titulo").textContent = { dados: "Fazer Pix", confirmar: "Confirme o Pix", resultado: "Pix" }[nome];
}

function abrirPix(chaveOrigem) {
  if (!contas.length) {
    toast("Cadastre uma conta antes de fazer um Pix.", "erro");
    return;
  }
  const origem = $("pix-origem");
  origem.replaceChildren(...contas.map((c) => new Option(`${c.titular} · ${brl.format(c.saldo)}`, c.chavePix)));
  origem.value = chaveOrigem || contas[0].chavePix;
  $("pix-destino").value = "";
  valorPix.definir(0);
  $("pix-erro").textContent = "";
  atualizarOrigem();
  etapaPix("dados");
  abrir($("dlg-pix"));
}

function contaPorChave(chave) {
  return contas.find((c) => c.chavePix === chave);
}

function atualizarOrigem() {
  const origem = contaPorChave($("pix-origem").value);
  $("pix-saldo-disponivel").textContent = origem ? `Saldo disponível: ${brl.format(origem.saldo)}` : "";
  // Contatos = as outras contas, para preencher o destino com um toque.
  const contatos = $("pix-contatos");
  contatos.replaceChildren();
  for (const c of contas.filter((c) => c.chavePix !== origem?.chavePix)) {
    const b = document.createElement("button");
    b.type = "button";
    b.className = "contato";
    b.setAttribute("aria-pressed", String($("pix-destino").value === c.chavePix));
    const nome = document.createElement("span");
    nome.textContent = c.titular.split(" ")[0];
    b.append(criarAvatar(c.titular), nome);
    b.addEventListener("click", () => {
      $("pix-destino").value = c.chavePix;
      atualizarOrigem();
      $("pix-valor").focus();
    });
    contatos.append(b);
  }
}
$("pix-origem").addEventListener("change", atualizarOrigem);
$("pix-destino").addEventListener("input", () => {
  contatosMarcados();
});
function contatosMarcados() {
  const destino = $("pix-destino").value.trim();
  $("pix-contatos").querySelectorAll(".contato").forEach((b, i) => {
    const outras = contas.filter((c) => c.chavePix !== $("pix-origem").value);
    b.setAttribute("aria-pressed", String(outras[i]?.chavePix === destino));
  });
}

$("pix-etapa-dados").addEventListener("submit", (ev) => {
  ev.preventDefault();
  const chaveOrigem = $("pix-origem").value;
  const chaveDestino = $("pix-destino").value.trim();
  const valor = valorPix.valor;
  const erro = !chaveDestino ? "Informe a chave Pix de destino."
    : valor <= 0 ? "Digite um valor maior que zero."
    : null;
  $("pix-erro").textContent = erro || "";
  $("pix-destino").setAttribute("aria-invalid", String(!chaveDestino));
  if (erro) return;

  // Saldo insuficiente e chave inexistente são validados pela API (422 / 404),
  // para a página demonstrar as respostas de erro do back-end.
  pixPendente = { chaveOrigem, chaveDestino, valor };
  const destino = contaPorChave(chaveDestino);
  const origem = contaPorChave(chaveOrigem);
  $("conf-valor").textContent = brl.format(valor);
  $("conf-para").textContent = destino ? destino.titular : "Chave não cadastrada aqui";
  $("conf-chave").textContent = chaveDestino;
  $("conf-de").textContent = origem ? `${origem.titular}` : chaveOrigem;
  etapaPix("confirmar");
});

$("pix-voltar").addEventListener("click", () => etapaPix("dados"));

$("pix-confirmar").addEventListener("click", async () => {
  const botao = $("pix-confirmar");
  botao.disabled = true;
  try {
    const comprovante = await api("/transferencias", { method: "POST", body: JSON.stringify(pixPendente) });
    mostrarComprovante(comprovante);
    carregarContas();
  } catch (e) {
    mostrarFalhaPix(e.problema);
  } finally {
    botao.disabled = false;
  }
});

function linhaResumo(rotulo, valor) {
  const div = document.createElement("div");
  const dt = document.createElement("dt");
  dt.textContent = rotulo;
  const dd = document.createElement("dd");
  dd.textContent = valor;
  div.append(dt, dd);
  return div;
}

function mostrarComprovante(c) {
  const area = $("pix-etapa-resultado");
  area.replaceChildren();
  const selo = document.createElement("span");
  selo.className = "selo ok";
  selo.append(icone("i-check"));
  const titulo = document.createElement("h3");
  titulo.textContent = "Pix enviado!";
  const valor = document.createElement("p");
  valor.className = "valor-grande";
  valor.textContent = brl.format(c.valor);

  const resumo = document.createElement("dl");
  resumo.className = "resumo";
  resumo.append(
    linhaResumo("Para", contaPorChave(c.chaveDestino)?.titular ?? c.chaveDestino),
    linhaResumo("Chave", c.chaveDestino),
    linhaResumo("De", contaPorChave(c.chaveOrigem)?.titular ?? c.chaveOrigem),
    linhaResumo("Saldo restante", brl.format(c.saldoOrigem)),
    linhaResumo("Data", new Date(c.realizadaEm).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" })),
  );
  resumo.lastChild.previousSibling.querySelector("dd").dataset.valor = "";

  const concluir = document.createElement("button");
  concluir.type = "button";
  concluir.className = "btn primario largo";
  concluir.textContent = "Concluir";
  concluir.addEventListener("click", () => $("dlg-pix").close());

  area.append(selo, titulo, valor, resumo, concluir);
  etapaPix("resultado");
}

function mostrarFalhaPix(problema) {
  const { titulo, detalhe, detalhes, status } = traduzirProblema(problema);
  const area = $("pix-etapa-resultado");
  area.replaceChildren();
  const selo = document.createElement("span");
  selo.className = "selo erro";
  selo.append(icone("i-alert"));
  const h3 = document.createElement("h3");
  h3.textContent = titulo;
  const p = document.createElement("p");
  p.className = "detalhe";
  p.textContent = [detalhe, ...detalhes].filter(Boolean).join(" ");
  const codigo = document.createElement("p");
  codigo.className = "ajuda";
  codigo.textContent = status ? `Resposta da API: HTTP ${status}` : "";

  const tentar = document.createElement("button");
  tentar.type = "button";
  tentar.className = "btn primario largo";
  tentar.textContent = "Corrigir e tentar de novo";
  tentar.addEventListener("click", () => etapaPix("dados"));
  const fechar = document.createElement("button");
  fechar.type = "button";
  fechar.className = "btn texto largo";
  fechar.textContent = "Fechar";
  fechar.addEventListener("click", () => $("dlg-pix").close());

  area.append(selo, h3, p, codigo, tentar, fechar);
  etapaPix("resultado");
}

// ---------- Cadastro de chave ----------
const saldoInicial = campoDinheiro($("cad-saldo"));
$("cad-cpf").addEventListener("input", (ev) => { ev.target.value = formatarCpfDigitado(ev.target.value); });

function abrirCadastro() {
  $("form-cadastrar").reset();
  saldoInicial.definir(100);
  $("cad-erro").hidden = true;
  abrir($("dlg-cadastrar"));
}

$("form-cadastrar").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  const form = ev.currentTarget;
  const dados = {
    titular: form.titular.value.trim(),
    cpf: form.cpf.value.replace(/\D/g, ""),
    chavePix: form.chavePix.value.trim(),
    saldo: saldoInicial.valor,
  };
  const botao = form.querySelector("button[type=submit]");
  botao.disabled = true;
  try {
    const conta = await api("/contas", { method: "POST", body: JSON.stringify(dados) });
    $("dlg-cadastrar").close();
    toast(`Chave ${conta.chavePix} cadastrada para ${conta.titular}.`);
    await carregarContas(conta.id);
  } catch (e) {
    mostrarCaixaErro($("cad-erro"), e.problema);
  } finally {
    botao.disabled = false;
  }
});

// ---------- Detalhe da conta ----------
let contaAberta = null;

function abrirConta(c) {
  contaAberta = c;
  $("conta-avatar").replaceWith(Object.assign(criarAvatar(c.titular, "grande"), { id: "conta-avatar" }));
  $("conta-nome").textContent = c.titular;
  $("conta-saldo").textContent = brl.format(c.saldo);
  $("conta-chave").textContent = c.chavePix;
  $("conta-cpf").textContent = mascararCpf(c.cpf);
  $("conta-id").textContent = `#${c.id}`;
  resetarExcluir();
  abrir($("dlg-conta"));
}

$("copiar-chave").addEventListener("click", async () => {
  try {
    await navigator.clipboard.writeText(contaAberta.chavePix);
    toast("Chave Pix copiada.");
  } catch {
    toast("Não foi possível copiar.", "erro");
  }
});

$("conta-pix").addEventListener("click", () => {
  $("dlg-conta").close();
  abrirPix(contaAberta.chavePix);
});

// Exclusão em dois toques: o primeiro pede confirmação, o segundo exclui.
function resetarExcluir() {
  const b = $("conta-excluir");
  b.dataset.confirmar = "";
  b.className = "btn perigo-texto largo";
  b.lastChild.textContent = " Excluir conta";
}
$("conta-excluir").addEventListener("click", async () => {
  const b = $("conta-excluir");
  if (!b.dataset.confirmar) {
    b.dataset.confirmar = "1";
    b.className = "btn perigo largo";
    b.lastChild.textContent = " Toque de novo para confirmar";
    return;
  }
  b.disabled = true;
  try {
    await api(`/contas/${contaAberta.id}`, { method: "DELETE" });
    $("dlg-conta").close();
    toast(`Conta de ${contaAberta.titular} excluída.`);
    await carregarContas();
  } catch (e) {
    toast(traduzirProblema(e.problema).titulo, "erro");
  } finally {
    b.disabled = false;
  }
});

// ---------- Configurações da API ----------
function abrirConfig() {
  $("cfg-url").value = apiUrl;
  $("cfg-status").textContent = "";
  $("cfg-status").className = "status-teste";
  abrir($("dlg-config"));
}

async function testarConexao() {
  const status = $("cfg-status");
  const anterior = apiUrl;
  apiUrl = $("cfg-url").value.replace(/\/+$/, "");
  status.className = "status-teste";
  status.textContent = "Testando…";
  try {
    const lista = await api("/contas");
    status.className = "status-teste ok";
    status.textContent = `Conectado! ${lista.length} conta(s) encontradas.`;
  } catch (e) {
    status.className = "status-teste erro";
    status.textContent = traduzirProblema(e.problema).detalhe;
  } finally {
    apiUrl = anterior;
  }
}

$("cfg-testar").addEventListener("click", testarConexao);
$("cfg-padrao").addEventListener("click", () => { $("cfg-url").value = API_PADRAO; });
$("form-config").addEventListener("submit", (ev) => {
  ev.preventDefault();
  apiUrl = $("cfg-url").value.replace(/\/+$/, "");
  if (apiUrl === API_PADRAO) prefs.apagar("pix-api-url");
  else prefs.gravar("pix-api-url", apiUrl);
  $("dlg-config").close();
  toast("Configuração salva.");
  carregarContas();
});

// ---------- Atalhos ----------
$("abrir-config").addEventListener("click", abrirConfig);
$("atalho-pix").addEventListener("click", () => abrirPix());
$("atalho-cadastrar").addEventListener("click", abrirCadastro);
$("atalho-atualizar").addEventListener("click", async () => {
  if (await carregarContas()) toast("Saldos atualizados.");
});

carregarContas();
