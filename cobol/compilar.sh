#!/usr/bin/env bash
# Compila o CONCILIA com o GnuCOBOL (brew install gnucobol / apt-get install gnucobol).
# -x: gera executável | -free: formato livre | -Wall: todos os avisos | -I: pasta dos copybooks
set -euo pipefail
RAIZ="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$RAIZ/bin"
cobc -x -free -Wall -I "$RAIZ/copybooks" -o "$RAIZ/bin/CONCILIA" "$RAIZ/src/CONCILIA.cbl"
echo "compilado: $RAIZ/bin/CONCILIA"
