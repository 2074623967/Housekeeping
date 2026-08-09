#!/usr/bin/env bash

set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
readonly RUNTIME_DIR="$ROOT_DIR/.runtime/freeze-candidate-frontends"
readonly LOG_DIR="$RUNTIME_DIR/logs"
readonly PID_DIR="$RUNTIME_DIR/pids"
readonly PAYMENT_CORE_API_TARGET="${HSP_PAYMENT_CORE_API_TARGET:-http://127.0.0.1:18081}"
readonly CLEARING_API_TARGET="${HSP_CLEARING_API_TARGET:-http://127.0.0.1:18122}"
readonly SETTLEMENT_API_TARGET="${HSP_SETTLEMENT_API_TARGET:-http://127.0.0.1:18132}"
readonly ACCOUNTING_API_TARGET="${HSP_ACCOUNTING_API_TARGET:-http://127.0.0.1:18110}"

mkdir -p "$LOG_DIR" "$PID_DIR"

start_frontend() {
  local app_key="$1"
  local workdir="$2"
  local port="$3"
  local target_var_name="${4:-}"
  local log_file="$LOG_DIR/${app_key}.log"
  local pid_file="$PID_DIR/${app_key}.pid"

  if curl -fsS "http://127.0.0.1:$port" >/dev/null 2>&1; then
    echo "[SKIP] $app_key 端口已可用, port=$port"
    return
  fi

  if [[ -f "$pid_file" ]]; then
    local existing_pid
    existing_pid="$(cat "$pid_file")"
    if kill -0 "$existing_pid" >/dev/null 2>&1; then
      echo "[SKIP] $app_key 已在运行, pid=$existing_pid"
      return
    fi
    rm -f "$pid_file"
  fi

  echo "[START] $app_key -> http://127.0.0.1:$port"
  (
    cd "$workdir"
    if [[ -n "$target_var_name" ]]; then
      export "$target_var_name"
      nohup npm run dev -- --host 127.0.0.1 --port "$port" >"$log_file" 2>&1 </dev/null &
    else
      nohup npm run dev -- --host 127.0.0.1 --port "$port" >"$log_file" 2>&1 </dev/null &
    fi
    echo $! >"$pid_file"
  )

  local pid
  pid="$(cat "$pid_file")"
  for _ in {1..20}; do
    if curl -fsS "http://127.0.0.1:$port" >/dev/null 2>&1; then
      echo "[OK] $app_key pid=$pid port=$port log=$log_file"
      return
    fi
    if ! kill -0 "$pid" >/dev/null 2>&1; then
      break
    fi
    sleep 0.25
  done

  echo "[FAIL] $app_key 未能监听端口 $port, pid=$pid log=$log_file" >&2
  tail -20 "$log_file" >&2 || true
  return 1
}

start_frontend \
  "payment-core-admin-web" \
  "$ROOT_DIR/systems/payment-core/frontend/admin-web" \
  "5174" \
  "HSP_PAYMENT_CORE_API_TARGET=$PAYMENT_CORE_API_TARGET"
start_frontend \
  "payment-core-app-web" \
  "$ROOT_DIR/systems/payment-core/frontend/app-web" \
  "5175" \
  "HSP_PAYMENT_CORE_API_TARGET=$PAYMENT_CORE_API_TARGET"
start_frontend \
  "payment-core-h5-web" \
  "$ROOT_DIR/systems/payment-core/frontend/h5-web" \
  "5176" \
  "HSP_PAYMENT_CORE_API_TARGET=$PAYMENT_CORE_API_TARGET"
start_frontend \
  "payment-core-pc-web" \
  "$ROOT_DIR/systems/payment-core/frontend/pc-web" \
  "5177" \
  "HSP_PAYMENT_CORE_API_TARGET=$PAYMENT_CORE_API_TARGET"
start_frontend \
  "clearing-system-admin-web" \
  "$ROOT_DIR/systems/clearing-system/frontend/admin-web" \
  "5273" \
  "HSP_CLEARING_API_TARGET=$CLEARING_API_TARGET"
start_frontend \
  "settlement-system-admin-web" \
  "$ROOT_DIR/systems/settlement-system/frontend/admin-web" \
  "5274" \
  "HSP_SETTLEMENT_API_TARGET=$SETTLEMENT_API_TARGET"
start_frontend \
  "accounting-system-admin-web" \
  "$ROOT_DIR/systems/accounting-system/frontend/admin-web" \
  "5275" \
  "HSP_ACCOUNTING_API_TARGET=$ACCOUNTING_API_TARGET"

cat <<EOF

冻结候选页面验收所需前端已启动：

1. payment-core/admin-web         http://127.0.0.1:5174
2. payment-core/app-web           http://127.0.0.1:5175
3. payment-core/h5-web            http://127.0.0.1:5176
4. payment-core/pc-web            http://127.0.0.1:5177
5. clearing-system/admin-web      http://127.0.0.1:5273
6. settlement-system/admin-web    http://127.0.0.1:5274
7. accounting-system/admin-web    http://127.0.0.1:5275

运行目录：
- PID: $PID_DIR
- LOG: $LOG_DIR

当前 API 代理目标：
- payment-core: $PAYMENT_CORE_API_TARGET
- clearing-system: $CLEARING_API_TARGET
- settlement-system: $SETTLEMENT_API_TARGET
- accounting-system: $ACCOUNTING_API_TARGET
EOF
