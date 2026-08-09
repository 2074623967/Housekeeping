#!/usr/bin/env bash

set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
readonly PID_DIR="$ROOT_DIR/.runtime/freeze-candidate-frontends/pids"

if [[ ! -d "$PID_DIR" ]]; then
  echo "未找到 PID 目录: $PID_DIR"
  exit 0
fi

for pid_file in "$PID_DIR"/*.pid; do
  [[ -f "$pid_file" ]] || continue
  app_key="$(basename "$pid_file" .pid)"
  pid="$(cat "$pid_file")"
  if kill -0 "$pid" >/dev/null 2>&1; then
    kill "$pid" >/dev/null 2>&1 || true
    echo "[STOP] $app_key pid=$pid"
  else
    echo "[CLEAN] $app_key pid=$pid 已不存在"
  fi
  rm -f "$pid_file"
done
