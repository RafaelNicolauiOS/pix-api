#!/usr/bin/env bash
# Testes de "arquivo-ouro" (golden files) do CONCILIA.
#
# Cada pasta em testes/cenarios/ tem:
#   MOVIMENTO.DAT        entrada (o arquivo que a API exporta)
#   RESULTADO.esperado   saída de máquina esperada, calculada à mão ANTES do programa (TDD)
#   rc.esperado          RETURN-CODE esperado (0, 4 ou 12)
#   RELATORIO.esperado   relatório impresso aprovado após revisão ("approval testing")
#   JOBLOG.esperado      mensagens do job (DISPLAY) aprovadas após revisão
#
# Uso: ./executar-testes.sh            roda e compara tudo
#      ./executar-testes.sh --aprovar  grava RELATORIO/JOBLOG atuais como esperados (revise antes!)
set -u
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
APROVAR=false; [ "${1:-}" = "--aprovar" ] && APROVAR=true

"$RAIZ/compilar.sh" > /dev/null || { echo "FALHA NA COMPILACAO"; exit 2; }

total=0; falhas=0
for dir in "$RAIZ"/testes/cenarios/*/; do
  nome="$(basename "$dir")"; total=$((total + 1))
  saida="$(mktemp -d)"
  # As variáveis DD_* fazem o papel dos cartões DD da JCL: ligam o nome lógico ao arquivo físico.
  DD_MOVIMENTO="$dir/MOVIMENTO.DAT" DD_RELATORIO="$saida/RELATORIO.TXT" DD_RESULTADO="$saida/RESULTADO.DAT" \
    CONCILIA_AGORA=20261026030007 "$RAIZ/bin/CONCILIA" > "$saida/JOBLOG.TXT" 2>&1
  rc=$?

  erros=""
  [ "$rc" = "$(cat "$dir/rc.esperado")" ] || erros+="    RC $rc, esperado $(cat "$dir/rc.esperado")\n"
  diff -u "$dir/RESULTADO.esperado" "$saida/RESULTADO.DAT" > "$saida/diff-res" || erros+="$(sed 's/^/    /' "$saida/diff-res")\n"
  for arq in RELATORIO JOBLOG; do
    if $APROVAR; then
      cp "$saida/$arq.TXT" "$dir/$arq.esperado"
    elif [ -f "$dir/$arq.esperado" ]; then
      diff -u "$dir/$arq.esperado" "$saida/$arq.TXT" > "$saida/diff-$arq" || erros+="$(sed 's/^/    /' "$saida/diff-$arq")\n"
    else
      erros+="    sem $arq.esperado (revise a saída e rode com --aprovar)\n"
    fi
  done

  if [ -z "$erros" ]; then
    echo "ok    $nome (RC=$rc)"
  else
    falhas=$((falhas + 1)); echo "FALHA $nome"; printf "%b" "$erros"
  fi
  rm -rf "$saida"
done

echo; echo "$((total - falhas))/$total cenários passaram"
[ "$falhas" -eq 0 ]
