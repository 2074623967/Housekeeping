#!/usr/bin/env bash

set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
readonly PAYMENT_CORE_FRONTEND_DIR="$ROOT_DIR/systems/payment-core/frontend"
readonly RUN_DIR="$ROOT_DIR/.runtime/payment-core-frontends"
readonly LOG_DIR="$RUN_DIR/logs"
readonly PID_DIR="$RUN_DIR/pids"
readonly API_TARGET="${HSP_PAYMENT_CORE_API_TARGET:-http://127.0.0.1:18081}"

mkdir -p "$LOG_DIR" "$PID_DIR"

start_frontend() {
  local app_name="$1"
  local workdir="$PAYMENT_CORE_FRONTEND_DIR/$app_name"
  local log_file="$LOG_DIR/${app_name}.log"
  local pid_file="$PID_DIR/${app_name}.pid"

  if [[ -f "$pid_file" ]]; then
    local existing_pid
    existing_pid="$(cat "$pid_file")"
    if kill -0 "$existing_pid" >/dev/null 2>&1; then
      echo "[SKIP] $app_name 已在运行, pid=$existing_pid"
      return
    fi
    rm -f "$pid_file"
  fi

  echo "[START] $app_name -> api target: $API_TARGET"
  (
    cd "$workdir"
    nohup env HSP_PAYMENT_CORE_API_TARGET="$API_TARGET" npm run dev -- --host 127.0.0.1 >"$log_file" 2>&1 &
    echo $! >"$pid_file"
  )

  local pid
  pid="$(cat "$pid_file")"
  echo "[OK] $app_name pid=$pid log=$log_file"
}

start_frontend "admin-web"
start_frontend "app-web"
start_frontend "h5-web"
start_frontend "pc-web"

cat <<EOF

支付核心四端启动命令已下发：

1. admin-web  默认端口 5174
2. app-web    默认端口 5175
3. h5-web     默认端口 5176
4. pc-web     默认端口 5177

运行目录：
- PID: $PID_DIR
- LOG: $LOG_DIR

当前 API 代理目标：
- $API_TARGET
EOF
