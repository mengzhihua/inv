#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"
PORT="${SERVER_PORT:-8089}"
echo "Starting INV 开票系统 on http://127.0.0.1:$PORT"
exec java ${JAVA_OPTS:-} -jar inv-backend-1.0.0.jar --server.port="$PORT" 
