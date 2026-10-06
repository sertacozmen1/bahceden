#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────
# Bahçeden: Dokümantasyon Portalı (Local)
#
# Kullanım:
#   ./docs/.tools/serve.sh            → Kur + başlat (http://localhost:8888)
#   ./docs/.tools/serve.sh --port 9000
#   ./docs/.tools/serve.sh --build    → Yalnızca strict derleme (CI ile aynı kontrol)
#
# Not: Bu portal yalnızca dokümantasyon içindir ve uygulamanın bağımlılık
# bütçesine (ADR-002) dahil değildir; venv depo dışına sızmaz.
# ─────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV_DIR="$SCRIPT_DIR/.venv"
PORT="${PORT:-8888}"
MODE="serve"

while [[ $# -gt 0 ]]; do
    case $1 in
        --port) PORT="$2"; shift 2 ;;
        --build) MODE="build"; shift ;;
        --help|-h)
            echo "Kullanım: ./docs/.tools/serve.sh [--port PORT] [--build]"
            exit 0
            ;;
        *) echo "Bilinmeyen argüman: $1"; exit 1 ;;
    esac
done

# Windows (Git Bash) ve Unix arasında venv ikili dizini farklı.
if [ -d "$VENV_DIR/Scripts" ]; then
    BIN="$VENV_DIR/Scripts"
else
    BIN="$VENV_DIR/bin"
fi

if [ ! -d "$VENV_DIR" ]; then
    echo "Virtual environment oluşturuluyor..."
    python3 -m venv "$VENV_DIR" 2>/dev/null || python -m venv "$VENV_DIR"
    if [ -d "$VENV_DIR/Scripts" ]; then BIN="$VENV_DIR/Scripts"; else BIN="$VENV_DIR/bin"; fi
fi

echo "Bağımlılıklar kontrol ediliyor..."
"$BIN/python" -m pip install -q --upgrade pip
"$BIN/python" -m pip install -q -r "$SCRIPT_DIR/requirements.txt"

cd "$SCRIPT_DIR"

if [ "$MODE" = "build" ]; then
    echo "Statik site oluşturuluyor (strict)..."
    "$BIN/mkdocs" build --strict
    echo "Site oluşturuldu: $(cd "$SCRIPT_DIR/../.." && pwd)/site/"
else
    echo ""
    echo "Dokümantasyon portalı başlatılıyor: http://localhost:$PORT"
    echo ""
    "$BIN/mkdocs" serve -a "127.0.0.1:$PORT"
fi
