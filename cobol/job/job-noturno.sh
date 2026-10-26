#!/usr/bin/env bash
# =====================================================================
# JOB NOTURNO - PIXBATCH  (o "JCL" do batch, em shell)
#
#   STEP010 EXTRAI   baixa o arquivo de movimento da pix-api
#   STEP020 CONCILIA executa o programa COBOL CONCILIA
#   STEP030 PUBLICA  grava a execução para o painel (execucao.json + indice.json)
#
# Uso:
#   cobol/job/job-noturno.sh --api https://pix-api-vfy6.onrender.com --saida /tmp/batch
#   cobol/job/job-noturno.sh --api http://localhost:8080 --data 2026-10-25 --saida docs/batch/demos/x --origem demo
#   cobol/job/job-noturno.sh --arquivo MOVIMENTO.DAT --saida ...   (pula a extração)
#
# Código de saída = maior RC dos steps (como o COND CODE de um job):
#   0 ok | 4 divergências | 8 falha na extração | 12 arquivo rejeitado | 16 erro de execução
# =====================================================================
set -u

RAIZ_COBOL="$(cd "$(dirname "$0")/.." && pwd)"
API=""; DATA=""; SAIDA=""; ARQUIVO=""; ORIGEM="manual"; TITULO=""; MAX_EXECUCOES=60

while [ $# -gt 0 ]; do
  case "$1" in
    --api) API="$2"; shift 2 ;;
    --data) DATA="$2"; shift 2 ;;
    --saida) SAIDA="$2"; shift 2 ;;
    --arquivo) ARQUIVO="$2"; shift 2 ;;
    --origem) ORIGEM="$2"; shift 2 ;;
    --titulo) TITULO="$2"; shift 2 ;;
    *) echo "parâmetro desconhecido: $1" >&2; exit 16 ;;
  esac
done
if [ -z "$SAIDA" ] || { [ -z "$API" ] && [ -z "$ARQUIVO" ]; }; then
  echo "uso: $0 (--api URL | --arquivo MOVIMENTO.DAT) --saida DIR [--data AAAA-MM-DD] [--origem noturno|manual|demo] [--titulo TEXTO]" >&2
  exit 16
fi

agora_ms() { python3 -c 'import time; print(int(time.time() * 1000))'; }
carimbo() { date -u +%Y-%m-%dT%H:%M:%SZ; }

ID="$(date -u +%Y%m%d-%H%M%S)"
DIR="$SAIDA/execucoes/$ID"
mkdir -p "$DIR"
LOG="$DIR/JOBLOG.TXT"
PASSOS="$DIR/.passos"; : > "$PASSOS"
INICIO_JOB="$(carimbo)"

log() { echo "$*" | tee -a "$LOG"; }
registrar_passo() { echo "$1|$2|$3|$4|$5" >> "$PASSOS"; }   # nome|descrição|início|duração ms|rc

log "PIXBATCH - JOB NOTURNO $ID - INICIO $(carimbo)"

# ---------------------------------------------------------- STEP010 EXTRAI
RC_EXTRAI=0; T0="$(agora_ms)"; INICIO_PASSO="$(carimbo)"
if [ -n "$ARQUIVO" ]; then
  cp "$ARQUIVO" "$DIR/MOVIMENTO.DAT"
  log "STEP010 EXTRAI   - arquivo informado: $(basename "$ARQUIVO")"
else
  URL="${API%/}/batch/movimento"; [ -n "$DATA" ] && URL="$URL?data=$DATA"
  log "STEP010 EXTRAI   - GET $URL"
  # O plano gratuito do Render "dorme": até 6 tentativas, 90 s cada, com pausa crescente.
  tentativa=1; HTTP=""
  while [ $tentativa -le 6 ]; do
    HTTP="$(curl -sS -m 90 -o "$DIR/MOVIMENTO.DAT" -w '%{http_code}' "$URL" 2>>"$LOG")"
    [ "$HTTP" = "200" ] && break
    log "STEP010 EXTRAI   - tentativa $tentativa: HTTP ${HTTP:-sem resposta}, aguardando..."
    sleep $((tentativa * 10)); tentativa=$((tentativa + 1))
  done
  if [ "$HTTP" != "200" ]; then RC_EXTRAI=8; log "STEP010 EXTRAI   - FALHA NA EXTRACAO"; fi
fi
if [ $RC_EXTRAI -eq 0 ]; then
  log "STEP010 EXTRAI   - $(wc -l < "$DIR/MOVIMENTO.DAT" | tr -d ' ') registros recebidos"
fi
registrar_passo STEP010 "Extração do movimento da API" "$INICIO_PASSO" $(( $(agora_ms) - T0 )) $RC_EXTRAI

# ---------------------------------------------------------- STEP020 CONCILIA
RC_CONCILIA=0
if [ $RC_EXTRAI -eq 0 ]; then
  T0="$(agora_ms)"; INICIO_PASSO="$(carimbo)"
  if [ ! -x "$RAIZ_COBOL/bin/CONCILIA" ]; then "$RAIZ_COBOL/compilar.sh" >> "$LOG" 2>&1 || exit 16; fi
  log "STEP020 CONCILIA - EXEC PGM=CONCILIA"
  # DD_*: os "cartões DD" que ligam os nomes lógicos do programa aos arquivos.
  DD_MOVIMENTO="$DIR/MOVIMENTO.DAT" DD_RELATORIO="$DIR/RELATORIO.TXT" DD_RESULTADO="$DIR/RESULTADO.DAT" \
    "$RAIZ_COBOL/bin/CONCILIA" 2>&1 | sed 's/^/          /' | tee -a "$LOG"
  RC_CONCILIA=${PIPESTATUS[0]}
  log "STEP020 CONCILIA - RC=$(printf '%02d' $RC_CONCILIA)"
  registrar_passo STEP020 "Validação, SORT e conciliação (COBOL)" "$INICIO_PASSO" $(( $(agora_ms) - T0 )) $RC_CONCILIA
fi

# ---------------------------------------------------------- STEP030 PUBLICA
T0="$(agora_ms)"; INICIO_PASSO="$(carimbo)"
RC_JOB=$RC_EXTRAI; [ $RC_CONCILIA -gt $RC_JOB ] && RC_JOB=$RC_CONCILIA
log "STEP030 PUBLICA  - gravando execução para o painel"
log "PIXBATCH - JOB NOTURNO $ID - FIM $(carimbo) - MAXCC=$(printf '%02d' $RC_JOB)"
registrar_passo STEP030 "Publicação para o painel" "$INICIO_PASSO" $(( $(agora_ms) - T0 )) 0

python3 - "$DIR" "$SAIDA" "$ID" "$INICIO_JOB" "$(carimbo)" "$RC_JOB" "$ORIGEM" "$TITULO" "${API:-arquivo local}" "$MAX_EXECUCOES" <<'PYEOF'
import json, os, sys, shutil
dir_, saida, id_, inicio, fim, rc, origem, titulo, api, maximo = sys.argv[1:]
rc, maximo = int(rc), int(maximo)

passos = []
for linha in open(os.path.join(dir_, ".passos")):
    nome, desc, ini, dur, prc = linha.rstrip("\n").split("|")
    passos.append({"nome": nome, "descricao": desc, "inicio": ini, "duracaoMs": int(dur), "rc": int(prc)})
os.remove(os.path.join(dir_, ".passos"))

# Data de referência e situação vêm do registro 'R' do RESULTADO (o COBOL é a fonte da verdade).
data_ref, situacao = None, {0: "OK", 4: "DIVERGENTE", 8: "FALHA NA EXTRACAO", 12: "REJEITADO"}.get(rc, "ERRO")
res = os.path.join(dir_, "RESULTADO.DAT")
if os.path.exists(res):
    for l in open(res):
        if l.startswith("R"):
            d = l[1:9]
            data_ref = f"{d[0:4]}-{d[4:6]}-{d[6:8]}" if d.strip("0") else None
            situacao = l[109:119].strip() or situacao

arquivos = [a for a in ("JOBLOG.TXT", "MOVIMENTO.DAT", "RELATORIO.TXT", "RESULTADO.DAT")
            if os.path.exists(os.path.join(dir_, a))]
execucao = {"id": id_, "titulo": titulo or None, "origem": origem, "api": api, "inicio": inicio, "fim": fim,
            "dataReferencia": data_ref, "rc": rc, "situacao": situacao, "passos": passos, "arquivos": arquivos}
json.dump(execucao, open(os.path.join(dir_, "execucao.json"), "w"), ensure_ascii=False, indent=2)

# Índice: execuções mais recentes primeiro; as antigas além do limite são apagadas.
indice_arq = os.path.join(saida, "indice.json")
indice = json.load(open(indice_arq)) if os.path.exists(indice_arq) else {"execucoes": []}
resumo = {k: execucao[k] for k in ("id", "titulo", "origem", "inicio", "dataReferencia", "rc", "situacao")}
indice["execucoes"] = [resumo] + [e for e in indice["execucoes"] if e["id"] != id_]
for antiga in indice["execucoes"][maximo:]:
    shutil.rmtree(os.path.join(saida, "execucoes", antiga["id"]), ignore_errors=True)
indice["execucoes"] = indice["execucoes"][:maximo]
indice["atualizadoEm"] = fim
json.dump(indice, open(indice_arq, "w"), ensure_ascii=False, indent=2)
PYEOF

exit $RC_JOB
