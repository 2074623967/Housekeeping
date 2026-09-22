#!/usr/bin/env bash

set -u

readonly ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
readonly RUNTIME_DIR="$ROOT_DIR/.runtime/local-stack"
readonly PID_DIR="$RUNTIME_DIR/pids"
readonly LOG_DIR="$RUNTIME_DIR/logs"
readonly MAVEN_BIN="${HSP_MAVEN_BIN:-/Users/abc123/apache-maven-3.9.16/bin/mvn}"
readonly MAVEN_REPO="${HSP_MAVEN_REPO:-/Users/abc123/apache-maven-3.9.16/repository}"
readonly JAVA_HOME_PATH="${HSP_JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home}"
readonly LOCAL_JAVA_OPTS="${HSP_LOCAL_JAVA_OPTS:--Xms64m -Xmx256m}"
readonly START_TIMEOUT_SECONDS="${HSP_LOCAL_START_TIMEOUT_SECONDS:-90}"

backend_manifest() {
  cat <<'EOF'
ops-config-system|systems/ops-config-system/backend|18170|/api/ops-config/summary
risk-control-system|systems/risk-control-system/backend|18180|/api/risk-control/summary
gateway-access|systems/gateway-access/backend|8090|/api/gateway-access/summary
accounting-system|systems/accounting-system/backend|18110|/api/accounting/subjects?pageNo=1&pageSize=1
clearing-system|systems/clearing-system/backend|18120|/api/clearing/batches?pageNo=1&pageSize=1
settlement-system|systems/settlement-system/backend|18130|/api/settlements/dashboard
refund-center|systems/refund-center/backend|18140|/api/refunds/overview
reconciliation-system|systems/reconciliation-system/backend|18150|/api/reconciliation/overview
deposit-system|systems/deposit-system/backend|18160|/api/deposits/overview
wallet-system|systems/wallet-system/backend|8094|/api/wallet/accounts
wallet-account|systems/wallet-account/backend|8095|/api/wallet/accounts?pageNo=1&pageSize=1
payment-core|systems/payment-core/backend|18081|/api/dashboard/summary
EOF
}

frontend_manifest() {
  cat <<'EOF'
payment-admin|payment-core|systems/payment-core/frontend/admin-web|5174|HSP_PAYMENT_CORE_API_TARGET|http://127.0.0.1:18081|/api/dashboard/summary
payment-app|payment-core|systems/payment-core/frontend/app-web|5175|HSP_PAYMENT_CORE_API_TARGET|http://127.0.0.1:18081|/api/dashboard/summary
payment-h5|payment-core|systems/payment-core/frontend/h5-web|5176|HSP_PAYMENT_CORE_API_TARGET|http://127.0.0.1:18081|/api/dashboard/summary
payment-pc|payment-core|systems/payment-core/frontend/pc-web|5177|HSP_PAYMENT_CORE_API_TARGET|http://127.0.0.1:18081|/api/dashboard/summary
wallet-account-admin|wallet-account|systems/wallet-account/frontend/admin-web|5178|HSP_WALLET_ACCOUNT_API_TARGET|http://127.0.0.1:8095|/api/wallet/accounts?pageNo=1&pageSize=1
refund-admin|refund-center|systems/refund-center/frontend/admin-web|5179|REFUND_CENTER_API_TARGET|http://127.0.0.1:18140|/api/refunds/overview
reconciliation-admin|reconciliation-system|systems/reconciliation-system/frontend/admin-web|5180|RECONCILIATION_API_TARGET|http://127.0.0.1:18150|/api/reconciliation/overview
deposit-admin|deposit-system|systems/deposit-system/frontend/admin-web|5181|DEPOSIT_API_TARGET|http://127.0.0.1:18160|/api/deposits/overview
ops-config-admin|ops-config-system|systems/ops-config-system/frontend/admin-web|5182|OPS_CONFIG_API_TARGET|http://127.0.0.1:18170|/api/ops-config/summary
risk-control-admin|risk-control-system|systems/risk-control-system/frontend/admin-web|5183|RISK_CONTROL_API_TARGET|http://127.0.0.1:18180|/api/risk-control/summary
gateway-access-admin|gateway-access|systems/gateway-access/frontend/admin-web|5184|HSP_GATEWAY_ACCESS_API_TARGET|http://127.0.0.1:8090|/api/gateway-access/summary
wallet-admin|wallet-system|systems/wallet-system/frontend/admin-web|5185|HSP_WALLET_API_TARGET|http://127.0.0.1:8094|/api/wallet/accounts
clearing-admin|clearing-system|systems/clearing-system/frontend/admin-web|5273|HSP_CLEARING_API_TARGET|http://127.0.0.1:18120|/api/clearing/batches?pageNo=1&pageSize=1
settlement-admin|settlement-system|systems/settlement-system/frontend/admin-web|5274|HSP_SETTLEMENT_API_TARGET|http://127.0.0.1:18130|/api/settlements/dashboard
accounting-admin|accounting-system|systems/accounting-system/frontend/admin-web|5275|HSP_ACCOUNTING_API_TARGET|http://127.0.0.1:18110|/api/accounting/subjects?pageNo=1&pageSize=1
EOF
}

usage() {
  cat <<'EOF'
用法: tools/dev/local-stack.sh <command> [scope]

command:
  doctor   检查本地运行依赖
  up       前台托管整套服务，适合在当前 Codex 终端持续运行
  start    启动服务并等待可访问
  stop     停止由本脚本启动的服务
  status   查看进程和 HTTP 可用状态
  smoke    对已启动页面和核心查询接口做 HTTP 冒烟
  happy-path 生成本地隔离支付单并验证支付、清分、结算、账务链路

scope:
  all        全部后端和前端（默认）
  backends   全部后端
  frontends  全部前端
  <system>   指定系统，例如 payment-core、refund-center
EOF
}

is_selected() {
  local scope="$1"
  local component="$2"
  local system_name="$3"
  [[ "$scope" == "all" ]] \
    || [[ "$scope" == "$component" ]] \
    || [[ "$scope" == "$system_name" ]]
}

pid_is_running() {
  local pid="$1"
  [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" >/dev/null 2>&1
}

http_is_ready() {
  local url="$1"
  local code
  code="$(curl --silent --show-error --max-time 2 --output /dev/null --write-out '%{http_code}' "$url" 2>/dev/null || true)"
  [[ "$code" =~ ^[23][0-9][0-9]$ ]]
}

wait_until_ready() {
  local name="$1"
  local pid="$2"
  local url="$3"
  local log_file="$4"
  local elapsed=0

  while (( elapsed < START_TIMEOUT_SECONDS )); do
    if http_is_ready "$url"; then
      echo "[READY] $name -> $url"
      return 0
    fi
    if ! pid_is_running "$pid"; then
      echo "[FAIL] $name 进程已退出，日志: $log_file" >&2
      tail -n 20 "$log_file" >&2 || true
      return 1
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done

  echo "[FAIL] $name 在 ${START_TIMEOUT_SECONDS}s 内未就绪，日志: $log_file" >&2
  tail -n 20 "$log_file" >&2 || true
  return 1
}

start_backend() {
  local name="$1"
  local relative_dir="$2"
  local port="$3"
  local health_path="$4"
  local workdir="$ROOT_DIR/$relative_dir"
  local pid_file="$PID_DIR/backend.$name.pid"
  local log_file="$LOG_DIR/backend.$name.log"
  local health_url="http://127.0.0.1:$port$health_path"
  local pid=""

  if [[ -f "$pid_file" ]]; then
    pid="$(cat "$pid_file")"
    if pid_is_running "$pid"; then
      if http_is_ready "$health_url"; then
        echo "[SKIP] backend/$name 已运行, pid=$pid"
        return 0
      fi
      echo "[FAIL] backend/$name 进程存在但接口未就绪, pid=$pid, log=$log_file" >&2
      return 1
    fi
    rm -f "$pid_file"
  fi

  if http_is_ready "$health_url"; then
    echo "[USE] backend/$name 已由外部进程提供 -> $health_url"
    return 0
  fi

  echo "[START] backend/$name -> 127.0.0.1:$port"
  (
    cd "$workdir" || exit 1
    if [[ "$name" == "payment-core" ]]; then
      nohup env \
        JAVA_HOME="$JAVA_HOME_PATH" \
        MAVEN_OPTS="$LOCAL_JAVA_OPTS" \
        HSP_PAYMENT_DOWNSTREAM_RECONCILIATION_FACT_URL="http://127.0.0.1:18150/api/reconciliation/cross-system/facts" \
        HSP_CLEARING_DOWNSTREAM_RECONCILIATION_FACT_URL="http://127.0.0.1:18150/api/reconciliation/cross-system/facts" \
        HSP_SETTLEMENT_DOWNSTREAM_RECONCILIATION_FACT_URL="http://127.0.0.1:18150/api/reconciliation/cross-system/facts" \
        SPRING_PROFILES_ACTIVE=demo \
        "$MAVEN_BIN" -Dmaven.repo.local="$MAVEN_REPO" -Pdemo \
          -Dspring-boot.run.fork=false -q spring-boot:run \
        >"$log_file" 2>&1 &
    else
      nohup env \
        JAVA_HOME="$JAVA_HOME_PATH" \
        MAVEN_OPTS="$LOCAL_JAVA_OPTS" \
        HSP_PAYMENT_DOWNSTREAM_RECONCILIATION_FACT_URL="http://127.0.0.1:18150/api/reconciliation/cross-system/facts" \
        HSP_CLEARING_DOWNSTREAM_RECONCILIATION_FACT_URL="http://127.0.0.1:18150/api/reconciliation/cross-system/facts" \
        HSP_SETTLEMENT_DOWNSTREAM_RECONCILIATION_FACT_URL="http://127.0.0.1:18150/api/reconciliation/cross-system/facts" \
        "$MAVEN_BIN" -Dmaven.repo.local="$MAVEN_REPO" \
          -Dspring-boot.run.fork=false -q spring-boot:run \
        >"$log_file" 2>&1 &
    fi
    echo $! >"$pid_file"
  )
  pid="$(cat "$pid_file")"
  wait_until_ready "backend/$name" "$pid" "$health_url" "$log_file"
}

start_frontend() {
  local name="$1"
  local system_name="$2"
  local relative_dir="$3"
  local port="$4"
  local api_env_name="$5"
  local api_target="$6"
  local workdir="$ROOT_DIR/$relative_dir"
  local pid_file="$PID_DIR/frontend.$name.pid"
  local log_file="$LOG_DIR/frontend.$name.log"
  local page_url="http://127.0.0.1:$port/"
  local pid=""

  if [[ -f "$pid_file" ]]; then
    pid="$(cat "$pid_file")"
    if pid_is_running "$pid"; then
      if http_is_ready "$page_url"; then
        echo "[SKIP] frontend/$name 已运行, pid=$pid"
        return 0
      fi
      echo "[FAIL] frontend/$name 进程存在但页面未就绪, pid=$pid, log=$log_file" >&2
      return 1
    fi
    rm -f "$pid_file"
  fi

  if http_is_ready "$page_url"; then
    echo "[USE] frontend/$name 已由外部进程提供 -> $page_url"
    return 0
  fi
  if [[ ! -d "$workdir/node_modules" ]]; then
    echo "[FAIL] frontend/$name 缺少 node_modules，请先在 $workdir 执行 npm install" >&2
    return 1
  fi

  echo "[START] frontend/$name -> 127.0.0.1:$port (api: $api_target)"
  (
    cd "$workdir" || exit 1
    nohup env "$api_env_name=$api_target" \
      node "$workdir/node_modules/vite/bin/vite.js" --host 127.0.0.1 --port "$port" --strictPort \
      >"$log_file" 2>&1 &
    echo $! >"$pid_file"
  )
  pid="$(cat "$pid_file")"
  wait_until_ready "frontend/$name" "$pid" "$page_url" "$log_file"
}

stop_process() {
  local label="$1"
  local pid_file="$2"
  local pid=""
  local retries=20

  [[ -f "$pid_file" ]] || return 0
  pid="$(cat "$pid_file")"
  if ! pid_is_running "$pid"; then
    echo "[CLEAN] $label pid=$pid 已不存在"
    rm -f "$pid_file"
    return 0
  fi

  kill "$pid" >/dev/null 2>&1 || true
  while (( retries > 0 )) && pid_is_running "$pid"; do
    sleep 0.1
    retries=$((retries - 1))
  done
  if pid_is_running "$pid"; then
    echo "[WARN] $label pid=$pid 未响应 TERM，保留 PID 文件供人工检查" >&2
    return 1
  fi
  rm -f "$pid_file"
  echo "[STOP] $label pid=$pid"
}

run_doctor() {
  local scope="${1:-all}"
  local failures=0
  local command_name
  for command_name in curl; do
    if command -v "$command_name" >/dev/null 2>&1; then
      echo "[OK] $command_name -> $(command -v "$command_name")"
    else
      echo "[FAIL] 未找到命令: $command_name" >&2
      failures=1
    fi
  done
  if [[ "$scope" != "frontends" ]]; then
    for executable in "$MAVEN_BIN" "$JAVA_HOME_PATH/bin/java"; do
      if [[ -x "$executable" ]]; then
        echo "[OK] $executable"
      else
        echo "[FAIL] 不可执行: $executable" >&2
        failures=1
      fi
    done
  fi
  if [[ "$scope" != "backends" ]]; then
    for command_name in node npm; do
      if command -v "$command_name" >/dev/null 2>&1; then
        echo "[OK] $command_name -> $(command -v "$command_name")"
      else
        echo "[FAIL] 未找到命令: $command_name" >&2
        failures=1
      fi
    done
    while IFS='|' read -r name system_name relative_dir port api_env_name api_target proxy_path; do
      if is_selected "$scope" "frontends" "$system_name"; then
        if [[ -d "$ROOT_DIR/$relative_dir/node_modules" ]]; then
          echo "[OK] frontend/$name 依赖已安装"
        else
          echo "[FAIL] frontend/$name 缺少 node_modules" >&2
          failures=1
        fi
      fi
    done < <(frontend_manifest)
  fi
  return "$failures"
}

run_start() {
  local scope="$1"
  local failures=0
  mkdir -p "$PID_DIR" "$LOG_DIR"

  if [[ "$scope" != "frontends" ]]; then
    while IFS='|' read -r name relative_dir port health_path; do
      if is_selected "$scope" "backends" "$name"; then
        start_backend "$name" "$relative_dir" "$port" "$health_path" || failures=1
      fi
    done < <(backend_manifest)
  fi

  if [[ "$scope" != "backends" ]]; then
    while IFS='|' read -r name system_name relative_dir port api_env_name api_target proxy_path; do
      if is_selected "$scope" "frontends" "$system_name"; then
        start_frontend "$name" "$system_name" "$relative_dir" "$port" "$api_env_name" "$api_target" || failures=1
      fi
    done < <(frontend_manifest)
  fi

  return "$failures"
}

run_stop() {
  local scope="$1"
  local failures=0

  if [[ "$scope" != "backends" ]]; then
    while IFS='|' read -r name system_name relative_dir port api_env_name api_target proxy_path; do
      if is_selected "$scope" "frontends" "$system_name"; then
        stop_process "frontend/$name" "$PID_DIR/frontend.$name.pid" || failures=1
      fi
    done < <(frontend_manifest)
  fi

  if [[ "$scope" != "frontends" ]]; then
    while IFS='|' read -r name relative_dir port health_path; do
      if is_selected "$scope" "backends" "$name"; then
        stop_process "backend/$name" "$PID_DIR/backend.$name.pid" || failures=1
      fi
    done < <(backend_manifest)
  fi

  return "$failures"
}

report_endpoint() {
  local label="$1"
  local url="$2"
  local pid_file="$3"
  local pid_state="external/stopped"
  local http_state="DOWN"
  local pid=""

  if [[ -f "$pid_file" ]]; then
    pid="$(cat "$pid_file")"
    if pid_is_running "$pid"; then
      pid_state="pid=$pid"
    else
      pid_state="stale-pid=$pid"
    fi
  fi
  if http_is_ready "$url"; then
    http_state="READY"
  fi
  printf '%-38s %-10s %-18s %s\n' "$label" "$http_state" "$pid_state" "$url"
  [[ "$http_state" == "READY" ]]
}

run_report() {
  local scope="$1"
  local mode="$2"
  local failures=0

  if [[ "$scope" != "frontends" ]]; then
    while IFS='|' read -r name relative_dir port health_path; do
      if is_selected "$scope" "backends" "$name"; then
        report_endpoint "backend/$name" "http://127.0.0.1:$port$health_path" "$PID_DIR/backend.$name.pid" || failures=1
      fi
    done < <(backend_manifest)
  fi
  if [[ "$scope" != "backends" ]]; then
    while IFS='|' read -r name system_name relative_dir port api_env_name api_target proxy_path; do
      if is_selected "$scope" "frontends" "$system_name"; then
        report_endpoint "frontend/$name" "http://127.0.0.1:$port/" "$PID_DIR/frontend.$name.pid" || failures=1
        if [[ "$mode" == "smoke" ]]; then
          report_endpoint "proxy/$name" "http://127.0.0.1:$port$proxy_path" "$PID_DIR/frontend.$name.pid" || failures=1
        fi
      fi
    done < <(frontend_manifest)
  fi

  if [[ "$mode" == "smoke" && "$failures" -eq 0 ]]; then
    echo "[PASS] 所选本地系统 HTTP 冒烟通过"
  fi
  return "$failures"
}

main() {
  local command_name="${1:-}"
  local scope="${2:-all}"

  case "$command_name" in
    doctor)
      run_doctor "$scope"
      ;;
    up)
      trap 'exit 130' INT TERM
      trap 'run_stop "$scope"' EXIT
      run_doctor "$scope" && run_start "$scope" && run_report "$scope" "smoke" || return $?
      echo "[RUNNING] 本地系统持续运行中；按 Ctrl-C 停止由本脚本启动的进程。"
      while true; do
        sleep 5
      done
      ;;
    start)
      run_doctor "$scope" && run_start "$scope" && run_report "$scope" "status"
      ;;
    stop)
      run_stop "$scope"
      ;;
    status)
      run_report "$scope" "status"
      ;;
    smoke)
      run_report "$scope" "smoke"
      ;;
    happy-path)
      node "$ROOT_DIR/tools/dev/cross-system-happy-path.mjs"
      ;;
    help|-h|--help)
      usage
      ;;
    *)
      usage >&2
      return 2
      ;;
  esac
}

main "$@"
