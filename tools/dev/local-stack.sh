#!/usr/bin/env bash

set -u

readonly ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
readonly RUNTIME_DIR="${HSP_LOCAL_RUNTIME_DIR:-$ROOT_DIR/.runtime/local-stack}"
readonly PID_DIR="$RUNTIME_DIR/pids"
readonly LOG_DIR="$RUNTIME_DIR/logs"
readonly LOCAL_JAVA_OPTS="${HSP_LOCAL_JAVA_OPTS:--Xms64m -Xmx256m}"
readonly START_TIMEOUT_SECONDS="${HSP_LOCAL_START_TIMEOUT_SECONDS:-90}"
STACK_SCOPE=""
QUERY_ACCESS_KEY=""
PAYMENT_EVENT_SECRET=""
REFUND_EVENT_SECRET=""
CLEARING_EVENT_SECRET=""

resolve_maven_bin() {
  if [[ -n "${HSP_MAVEN_BIN:-}" ]]; then
    printf '%s' "$HSP_MAVEN_BIN"
    return 0
  fi
  if command -v mvn >/dev/null 2>&1; then
    local path_maven_bin
    path_maven_bin="$(command -v mvn)"
    if [[ -x "$path_maven_bin" ]]; then
      printf '%s' "$path_maven_bin"
      return 0
    fi
  fi
  for candidate in \
    "$ROOT_DIR/../apache-maven-3.9.16/bin/mvn" \
    "/Users/abc123/apache-maven-3.9.16/bin/mvn" \
    "$ROOT_DIR/../apache-maven-3.9.12/bin/mvn" \
    "/Users/abc123/workspace/apache-maven-3.9.12/bin/mvn"; do
    if [[ -x "$candidate" ]]; then
      printf '%s' "$candidate"
      return 0
    fi
  done
  printf '%s' "mvn"
}

resolve_java_home() {
  if [[ -n "${HSP_JAVA_HOME:-}" && -x "${HSP_JAVA_HOME}/bin/java" && -x "${HSP_JAVA_HOME}/bin/javac" ]]; then
    printf '%s' "$HSP_JAVA_HOME"
    return 0
  fi
  if [[ "$(uname -s)" == "Darwin" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
    local mac_java_home
    mac_java_home="$(/usr/libexec/java_home 2>/dev/null || true)"
    if [[ -n "$mac_java_home" && -x "$mac_java_home/bin/java" && -x "$mac_java_home/bin/javac" ]]; then
      printf '%s' "$mac_java_home"
      return 0
    fi
  fi
  for candidate in \
    "/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home" \
    "/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home"; do
    if [[ -x "$candidate/bin/java" && -x "$candidate/bin/javac" ]]; then
      printf '%s' "$candidate"
      return 0
    fi
  done
  if command -v java >/dev/null 2>&1; then
    local java_bin
    java_bin="$(command -v java)"
    if [[ "$java_bin" == */bin/java && -x "${java_bin%/bin/java}/bin/java" && -x "${java_bin%/bin/java}/bin/javac" ]]; then
      printf '%s' "${java_bin%/bin/java}"
      return 0
    fi
  fi
  printf '%s' ""
}

resolve_maven_repo() {
  if [[ -n "${HSP_MAVEN_REPO:-}" ]]; then
    printf '%s' "$HSP_MAVEN_REPO"
    return 0
  fi
  local readable_repo=""
  for candidate in \
    "$ROOT_DIR/../apache-maven-3.9.16/repository" \
    "/Users/abc123/apache-maven-3.9.16/repository" \
    "$ROOT_DIR/../apache-maven-3.9.12/repository" \
    "/Users/abc123/workspace/apache-maven-3.9.12/repository"; do
    if [[ -d "$candidate" && -w "$candidate" ]]; then
      printf '%s' "$candidate"
      return 0
    fi
    if [[ -z "$readable_repo" && -d "$candidate" && -r "$candidate" ]]; then
      readable_repo="$candidate"
    fi
  done
  if [[ -n "$readable_repo" ]]; then
    printf '%s' "$readable_repo"
    return 0
  fi
  if [[ -n "${HOME:-}" && -d "$HOME/.m2/repository" && -w "$HOME/.m2/repository" ]]; then
    printf '%s' "$HOME/.m2/repository"
    return 0
  fi
  local runtime_repo="$ROOT_DIR/.runtime/maven-repository"
  if mkdir -p "$runtime_repo" 2>/dev/null && [[ -w "$runtime_repo" ]]; then
    printf '%s' "$runtime_repo"
    return 0
  fi
  if [[ -n "${HOME:-}" ]]; then
    printf '%s' "$HOME/.m2/repository"
    return 0
  fi
  printf '%s' "$runtime_repo"
}

readonly MAVEN_BIN="$(resolve_maven_bin)"
readonly JAVA_HOME_PATH="$(resolve_java_home)"
readonly MAVEN_REPO="$(resolve_maven_repo)"

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
payment-admin|payment-core|systems/payment-core/frontend/admin-web|5174|HSP_PAYMENT_CORE_API_TARGET|http://localhost:18081|/api/dashboard/summary
payment-app|payment-core|systems/payment-core/frontend/app-web|5175|HSP_PAYMENT_CORE_API_TARGET|http://localhost:18081|/api/dashboard/summary
payment-h5|payment-core|systems/payment-core/frontend/h5-web|5176|HSP_PAYMENT_CORE_API_TARGET|http://localhost:18081|/api/dashboard/summary
payment-pc|payment-core|systems/payment-core/frontend/pc-web|5177|HSP_PAYMENT_CORE_API_TARGET|http://localhost:18081|/api/dashboard/summary
wallet-account-admin|wallet-account|systems/wallet-account/frontend/admin-web|5178|HSP_WALLET_ACCOUNT_API_TARGET|http://localhost:8095|/api/wallet/accounts?pageNo=1&pageSize=1
refund-admin|refund-center|systems/refund-center/frontend/admin-web|5179|REFUND_CENTER_API_TARGET|http://localhost:18140|/api/refunds/overview
reconciliation-admin|reconciliation-system|systems/reconciliation-system/frontend/admin-web|5180|RECONCILIATION_API_TARGET|http://localhost:18150|/api/reconciliation/overview
deposit-admin|deposit-system|systems/deposit-system/frontend/admin-web|5181|DEPOSIT_API_TARGET|http://localhost:18160|/api/deposits/overview
ops-config-admin|ops-config-system|systems/ops-config-system/frontend/admin-web|5182|OPS_CONFIG_API_TARGET|http://localhost:18170|/api/ops-config/summary
risk-control-admin|risk-control-system|systems/risk-control-system/frontend/admin-web|5183|RISK_CONTROL_API_TARGET|http://localhost:18180|/api/risk-control/summary
gateway-access-admin|gateway-access|systems/gateway-access/frontend/admin-web|5184|HSP_GATEWAY_ACCESS_API_TARGET|http://localhost:8090|/api/gateway-access/summary
wallet-admin|wallet-system|systems/wallet-system/frontend/admin-web|5185|HSP_WALLET_API_TARGET|http://localhost:8094|/api/wallet/accounts
clearing-admin|clearing-system|systems/clearing-system/frontend/admin-web|5273|HSP_CLEARING_API_TARGET|http://localhost:18120|/api/clearing/batches?pageNo=1&pageSize=1
settlement-admin|settlement-system|systems/settlement-system/frontend/admin-web|5274|HSP_SETTLEMENT_API_TARGET|http://localhost:18130|/api/settlements/dashboard
accounting-admin|accounting-system|systems/accounting-system/frontend/admin-web|5275|HSP_ACCOUNTING_API_TARGET|http://localhost:18110|/api/accounting/subjects?pageNo=1&pageSize=1
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
  happy-path 隔离启动并验证支付链路（verify payment-chain 别名）
  verify   在同一会话启动全栈、冒烟、执行 happy-path 后统一停止

scope:
  all        全部后端和前端（默认）
  backends   全部后端
  frontends  全部前端
  <system>   指定系统，例如 payment-core、refund-center
  payment-chain  启动支付 happy-path 所需的后端依赖
  payment-query-chain  隔离运行网关主动查单到九服务对账的变体
  risk-review    仅启动支付核心与风控中心，验证本地人工复核回环
EOF
}

is_payment_chain_system() {
  case "$1" in
    ops-config-system|risk-control-system|gateway-access|accounting-system|clearing-system|settlement-system|refund-center|reconciliation-system|payment-core)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

is_selected() {
  local scope="$1"
  local component="$2"
  local system_name="$3"
  if [[ "$scope" == "payment-chain" || "$scope" == "payment-query-chain" ]]; then
    [[ "$component" == "backends" ]] && is_payment_chain_system "$system_name"
    return $?
  fi
  if [[ "$scope" == "risk-review" ]]; then
    [[ "$component" == "backends" && ( "$system_name" == "risk-control-system" || "$system_name" == "payment-core" ) ]]
    return $?
  fi
  [[ "$scope" == "all" ]] \
    || [[ "$scope" == "$component" ]] \
    || [[ "$scope" == "$system_name" ]]
}

pid_is_running() {
  local pid="$1"
  local process_state=""
  [[ "$pid" =~ ^[0-9]+$ ]] || return 1
  kill -0 "$pid" >/dev/null 2>&1 || return 1
  process_state="$(ps -o stat= -p "$pid" 2>/dev/null | tr -d '[:space:]')"
  [[ -n "$process_state" && "$process_state" != Z* ]]
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
  local scope="$5"
  local verification_mode="${6:-}"
  local workdir="$ROOT_DIR/$relative_dir"
  local pid_file="$PID_DIR/backend.$name.pid"
  local log_file="$LOG_DIR/backend.$name.log"
  local health_url="http://localhost:$port$health_path"
  local pid=""
  local -a backend_env=(JAVA_HOME="$JAVA_HOME_PATH" MAVEN_OPTS="$LOCAL_JAVA_OPTS")
  local -a payment_query_env=()
  local -a event_signature_env=()
  local payment_profile="demo"

  if [[ "$name" == "risk-control-system" && "$scope" == "risk-review" ]]; then
    backend_env+=(SPRING_PROFILES_ACTIVE=local-risk-review)
  elif [[ "$verification_mode" == "payment-chain" || "$verification_mode" == "payment-query-chain" ]] \
    && is_payment_chain_system "$name"; then
    if [[ "$name" == "payment-core" ]]; then
      payment_profile="demo,local-payment-chain"
    else
      backend_env+=(SPRING_PROFILES_ACTIVE=local-payment-chain)
    fi
  fi
  if [[ "$verification_mode" == "payment-query-chain" ]]; then
    if [[ "$name" == "gateway-access" ]]; then
      backend_env+=(HSP_GATEWAY_PAYMENT_ACCESS_KEY="$QUERY_ACCESS_KEY"
        HSP_GATEWAY_WX_PAYMENT_QUERY_URL="http://127.0.0.1:18190/query"
        SPRING_SQL_INIT_DATA_LOCATIONS="classpath:data.sql,file:$ROOT_DIR/tools/dev/payment-query-chain-gateway.sql")
    elif [[ "$name" == "payment-core" ]]; then
      payment_query_env+=(HSP_PAYMENT_CHANNEL_QUERY_GATEWAY_ENABLED=true
        HSP_PAYMENT_CHANNEL_QUERY_SUPPORTED_CHANNELS=wx_h5
        HSP_PAYMENT_CHANNEL_QUERY_GATEWAY_ACCESS_KEY="$QUERY_ACCESS_KEY"
        HSP_PAYMENT_CHANNEL_QUERY_GATEWAY_URL="http://127.0.0.1:8090/api/gateway/payment-orders/query")
    fi
  fi
  if [[ -n "$PAYMENT_EVENT_SECRET" ]] \
    && [[ "$name" == "payment-core" || "$name" == "clearing-system" || "$name" == "accounting-system" ]]; then
    event_signature_env+=(HSP_PAYMENT_SUCCESS_HTTP_SECRET="$PAYMENT_EVENT_SECRET")
  fi
  if [[ -n "$REFUND_EVENT_SECRET" ]] \
    && [[ "$name" == "refund-center" || "$name" == "accounting-system" ]]; then
    event_signature_env+=(HSP_REFUND_SUCCESS_HTTP_SECRET="$REFUND_EVENT_SECRET")
  fi
  if [[ -n "$CLEARING_EVENT_SECRET" ]] \
    && [[ "$name" == "clearing-system" || "$name" == "settlement-system" || "$name" == "accounting-system" ]]; then
    event_signature_env+=(HSP_CLEARING_GENERATED_HTTP_SECRET="$CLEARING_EVENT_SECRET")
  fi

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

  echo "[START] backend/$name -> localhost:$port"
  (
    cd "$workdir" || exit 1
    if [[ "$name" == "payment-core" ]]; then
      nohup env \
        JAVA_HOME="$JAVA_HOME_PATH" \
        MAVEN_OPTS="$LOCAL_JAVA_OPTS" \
        ${payment_query_env[@]+"${payment_query_env[@]}"} \
        ${event_signature_env[@]+"${event_signature_env[@]}"} \
        HSP_PAYMENT_DOWNSTREAM_RECONCILIATION_FACT_URL="http://localhost:18150/api/reconciliation/cross-system/facts" \
        HSP_CLEARING_DOWNSTREAM_RECONCILIATION_FACT_URL="http://localhost:18150/api/reconciliation/cross-system/facts" \
        HSP_SETTLEMENT_DOWNSTREAM_RECONCILIATION_FACT_URL="http://localhost:18150/api/reconciliation/cross-system/facts" \
        HSP_ACCOUNTING_DOWNSTREAM_RECONCILIATION_FACT_URL="http://localhost:18150/api/reconciliation/cross-system/facts" \
        REFUND_CENTER_RECONCILIATION_FACT_URL="http://localhost:18150/api/reconciliation/cross-system/facts" \
        SPRING_PROFILES_ACTIVE="$payment_profile" \
        "$MAVEN_BIN" -Dmaven.repo.local="$MAVEN_REPO" -Pdemo \
          -Dspring-boot.run.fork=false -q spring-boot:run \
        >"$log_file" 2>&1 &
    else
      nohup env \
        "${backend_env[@]}" \
        ${event_signature_env[@]+"${event_signature_env[@]}"} \
        HSP_PAYMENT_DOWNSTREAM_RECONCILIATION_FACT_URL="http://localhost:18150/api/reconciliation/cross-system/facts" \
        HSP_CLEARING_DOWNSTREAM_RECONCILIATION_FACT_URL="http://localhost:18150/api/reconciliation/cross-system/facts" \
        HSP_SETTLEMENT_DOWNSTREAM_RECONCILIATION_FACT_URL="http://localhost:18150/api/reconciliation/cross-system/facts" \
        HSP_ACCOUNTING_DOWNSTREAM_RECONCILIATION_FACT_URL="http://localhost:18150/api/reconciliation/cross-system/facts" \
        REFUND_CENTER_RECONCILIATION_FACT_URL="http://localhost:18150/api/reconciliation/cross-system/facts" \
        "$MAVEN_BIN" -Dmaven.repo.local="$MAVEN_REPO" \
          -Dspring-boot.run.fork=false -q spring-boot:run \
        >"$log_file" 2>&1 &
    fi
    echo $! >"$pid_file"
  )
  pid="$(cat "$pid_file")"
  if ! wait_until_ready "backend/$name" "$pid" "$health_url" "$log_file"; then
    stop_process "backend/$name" "$pid_file" || true
    return 1
  fi
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
  local page_url="http://localhost:$port/"
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

  echo "[START] frontend/$name -> localhost:$port (api: $api_target)"
  (
    cd "$workdir" || exit 1
    nohup env "$api_env_name=$api_target" \
      node "$workdir/node_modules/vite/bin/vite.js" --host localhost --port "$port" --strictPort \
      >"$log_file" 2>&1 &
    echo $! >"$pid_file"
  )
  pid="$(cat "$pid_file")"
  if ! wait_until_ready "frontend/$name" "$pid" "$page_url" "$log_file"; then
    stop_process "frontend/$name" "$pid_file" || true
    return 1
  fi
}

stop_child_processes() {
  local parent_pid="$1"
  local child_pid
  while read -r child_pid; do
    [[ -n "$child_pid" ]] || continue
    stop_child_processes "$child_pid"
    kill "$child_pid" >/dev/null 2>&1 || true
  done < <(pgrep -P "$parent_pid" 2>/dev/null || true)
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

  stop_child_processes "$pid"
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
    for executable in "$MAVEN_BIN" "$JAVA_HOME_PATH/bin/java" "$JAVA_HOME_PATH/bin/javac"; do
      if [[ -x "$executable" ]]; then
        echo "[OK] $executable"
      else
        echo "[FAIL] 不可执行: $executable" >&2
        failures=1
      fi
    done
    if [[ -d "$MAVEN_REPO" && -w "$MAVEN_REPO" ]]; then
      echo "[OK] Maven repository writable -> $MAVEN_REPO"
    elif [[ -d "$MAVEN_REPO" && -r "$MAVEN_REPO" ]]; then
      echo "[OK] Maven repository readable cache -> $MAVEN_REPO"
    else
      echo "[FAIL] Maven repository missing or not writable -> $MAVEN_REPO" >&2
      failures=1
    fi
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
  local verification_mode="${2:-}"
  local failures=0
  mkdir -p "$PID_DIR" "$LOG_DIR"

  if [[ "$scope" == "payment-query-chain" ]]; then
    echo "[START] local/payment-query-upstream -> 127.0.0.1:18190"
    nohup node "$ROOT_DIR/tools/dev/local-payment-query-upstream.mjs" \
      >"$LOG_DIR/payment-query-upstream.log" 2>&1 &
    echo $! >"$PID_DIR/payment-query-upstream.pid"
    wait_until_ready "local/payment-query-upstream" "$(cat "$PID_DIR/payment-query-upstream.pid")" \
      "http://127.0.0.1:18190/health" "$LOG_DIR/payment-query-upstream.log" || return 1
  fi

  if [[ "$scope" != "frontends" ]]; then
    while IFS='|' read -r name relative_dir port health_path; do
      if is_selected "$scope" "backends" "$name"; then
        start_backend "$name" "$relative_dir" "$port" "$health_path" "$scope" "$verification_mode" || failures=1
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
  if [[ "$scope" == "payment-query-chain" ]]; then
    stop_process "local/payment-query-upstream" "$PID_DIR/payment-query-upstream.pid" || failures=1
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
    if [[ "$pid_state" != pid=* ]]; then
      pid_state="external/ready"
    fi
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
        report_endpoint "backend/$name" "http://localhost:$port$health_path" "$PID_DIR/backend.$name.pid" || failures=1
      fi
    done < <(backend_manifest)
  fi
  if [[ "$scope" != "backends" ]]; then
    while IFS='|' read -r name system_name relative_dir port api_env_name api_target proxy_path; do
      if is_selected "$scope" "frontends" "$system_name"; then
        report_endpoint "frontend/$name" "http://localhost:$port/" "$PID_DIR/frontend.$name.pid" || failures=1
        if [[ "$mode" == "smoke" ]]; then
          report_endpoint "proxy/$name" "http://localhost:$port$proxy_path" "$PID_DIR/frontend.$name.pid" || failures=1
        fi
      fi
    done < <(frontend_manifest)
  fi

  if [[ "$mode" == "smoke" && "$failures" -eq 0 ]]; then
    echo "[PASS] 所选本地系统 HTTP 冒烟通过"
  fi
  return "$failures"
}

guard_local_payment_chain_verify() {
  local scope="$1"
  local variable_name name system_name relative_dir port health_path api_env_name api_target proxy_path
  local executable

  for executable in lsof nc; do
    if ! command -v "$executable" >/dev/null 2>&1; then
      echo "[FAIL] payment-chain 写前隔离检查缺少 $executable" >&2
      return 2
    fi
  done
  while read -r variable_name; do
    case "$variable_name" in
      SPRING_*|DATABASE_URL|HSP_DB_*|HSP_DEMO_DB_*|HSP_RABBITMQ_*|HSP_*_BASE_URL|HSP_GATEWAY_*_URL|HSP_PAYMENT_*_URL|\
      HSP_PAYMENT_CORE_PORT|HSP_PAYMENT_SUBMIT_GATEWAY_ENABLED|HSP_PAYMENT_EVENT_DISPATCH_MODE|\
      HSP_PAYMENT_SUCCESS_HTTP_SECRET|\
      HSP_PAYMENT_SUCCESS_AMQP_SECRET|\
      HSP_REFUND_SUCCESS_HTTP_SECRET|\
      HSP_CLEARING_GENERATED_HTTP_SECRET|\
      HSP_CLEARING_GENERATED_AMQP_SECRET|\
      HSP_SETTLEMENT_COMPENSATION_HTTP_SECRET|\
      HSP_LOCAL_PAYMENT_CHAIN_SCOPED|HSP_LOCAL_PAYMENT_QUERY_CHAIN_SCOPED|HSP_LOCAL_JAVA_OPTS|\
      JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|MAVEN_OPTS|\
      OPS_CONFIG_*|RISK_CONTROL_*|REFUND_CENTER_*|RECONCILIATION_*)
        if [[ -n "${!variable_name:-}" ]]; then
          echo "[FAIL] payment-chain 拒绝环境覆盖: $variable_name" >&2
          return 2
        fi
        ;;
    esac
  done < <(compgen -e)
  while IFS='|' read -r name relative_dir port health_path; do
    if is_selected "$scope" "backends" "$name"; then
      if [[ -f "$PID_DIR/backend.$name.pid" ]]; then
        echo "[FAIL] payment-chain 发现现存 PID 文件: backend.$name.pid" >&2
        return 2
      fi
      if lsof -nP -iTCP:"$port" -sTCP:LISTEN -t >/dev/null 2>&1 \
        || nc -z -w 1 127.0.0.1 "$port" >/dev/null 2>&1; then
        echo "[FAIL] payment-chain 目标端口 $port 已有服务，拒绝演示写入" >&2
        return 2
      fi
    fi
  done < <(backend_manifest)
  if [[ "$scope" == "all" ]]; then
    while IFS='|' read -r name system_name relative_dir port api_env_name api_target proxy_path; do
      if [[ -f "$PID_DIR/frontend.$name.pid" ]]; then
        echo "[FAIL] verify all 发现现存前端 PID 文件: frontend.$name.pid" >&2
        return 2
      fi
      if lsof -nP -iTCP:"$port" -sTCP:LISTEN -t >/dev/null 2>&1 \
        || nc -z -w 1 127.0.0.1 "$port" >/dev/null 2>&1; then
        echo "[FAIL] verify all 前端端口 $port 已有服务，拒绝复用" >&2
        return 2
      fi
    done < <(frontend_manifest)
  fi
  if [[ "$scope" == "payment-query-chain" ]]; then
    if [[ -f "$PID_DIR/payment-query-upstream.pid" ]] \
      || lsof -nP -iTCP:18190 -sTCP:LISTEN -t >/dev/null 2>&1 \
      || nc -z -w 1 127.0.0.1 18190 >/dev/null 2>&1; then
      echo "[FAIL] payment-query-chain 上游 mock 端口/PID 已被占用，拒绝演示写入" >&2
      return 2
    fi
  fi
}

guard_started_local_payment_chain() {
  local scope="$1"
  local name system_name relative_dir port health_path api_env_name api_target proxy_path pid_file

  while IFS='|' read -r name relative_dir port health_path; do
    if is_selected "$scope" "backends" "$name"; then
      pid_file="$PID_DIR/backend.$name.pid"
      if [[ ! -f "$pid_file" ]] || ! pid_is_running "$(cat "$pid_file")"; then
        echo "[FAIL] payment-chain 未确认本轮启动的 backend/$name，拒绝演示写入" >&2
        return 2
      fi
    fi
  done < <(backend_manifest)
  if [[ "$scope" == "all" ]]; then
    while IFS='|' read -r name system_name relative_dir port api_env_name api_target proxy_path; do
      pid_file="$PID_DIR/frontend.$name.pid"
      if [[ ! -f "$pid_file" ]] || ! pid_is_running "$(cat "$pid_file")"; then
        echo "[FAIL] verify all 未确认本轮启动的 frontend/$name，拒绝复用" >&2
        return 2
      fi
    done < <(frontend_manifest)
  fi
  if [[ "$scope" == "payment-query-chain" ]] \
    && { [[ ! -f "$PID_DIR/payment-query-upstream.pid" ]] \
      || ! pid_is_running "$(cat "$PID_DIR/payment-query-upstream.pid")"; }; then
    echo "[FAIL] payment-query-chain 未确认本轮本机上游 mock" >&2
    return 2
  fi
}

run_verify() {
  local scope="$1"
  local result=0
  local variable_name
  local verification_mode=""

  if [[ "$scope" != "all" && "$scope" != "payment-chain" \
    && "$scope" != "payment-query-chain" && "$scope" != "risk-review" ]]; then
    echo "[FAIL] verify 只支持 all、payment-chain、payment-query-chain 或 risk-review" >&2
    return 2
  fi
  if [[ "$scope" != "risk-review" ]]; then
    guard_local_payment_chain_verify "$scope" || return $?
    verification_mode="payment-chain"
    PAYMENT_EVENT_SECRET="$(node -e "process.stdout.write(require('node:crypto').randomBytes(32).toString('hex'))")"
    REFUND_EVENT_SECRET="$(node -e "process.stdout.write(require('node:crypto').randomBytes(32).toString('hex'))")"
    CLEARING_EVENT_SECRET="$(node -e "process.stdout.write(require('node:crypto').randomBytes(32).toString('hex'))")"
    if [[ "$scope" == "payment-query-chain" ]]; then
      verification_mode="payment-query-chain"
      QUERY_ACCESS_KEY="local-query-$RANDOM-$RANDOM-$RANDOM"
    fi
  fi
  if [[ "$scope" == "risk-review" ]]; then
    for variable_name in HSP_DB_URL HSP_DEMO_DB_URL HSP_DB_USERNAME HSP_DB_PASSWORD \
      RISK_CONTROL_DATASOURCE_URL RISK_CONTROL_DATASOURCE_DRIVER RISK_CONTROL_DATASOURCE_USERNAME \
      RISK_CONTROL_DATASOURCE_PASSWORD RISK_CONTROL_SQL_INIT_MODE RISK_CONTROL_PORT \
      HSP_PAYMENT_CORE_PORT HSP_PAYMENT_RISK_CONTROL_EVALUATE_URL \
      HSP_PAYMENT_SUBMIT_GATEWAY_ENABLED HSP_PAYMENT_EVENT_DISPATCH_MODE \
      HSP_PAYMENT_OPS_CONFIG_ENABLED RISK_CONTROL_OPS_CONFIG_ENABLED \
      SPRING_DATASOURCE_URL SPRING_PROFILES_ACTIVE DATABASE_URL; do
      if [[ -n "${!variable_name:-}" ]]; then
        echo "[FAIL] risk-review 拒绝环境覆盖: $variable_name" >&2
        return 2
      fi
    done
    for variable_name in payment-core risk-control-system; do
      if [[ -f "$PID_DIR/backend.$variable_name.pid" ]]; then
        echo "[FAIL] risk-review 发现现存 PID 文件: backend.$variable_name.pid" >&2
        return 2
      fi
    done
    if http_is_ready "http://localhost:18081/api/dashboard/summary" \
      || http_is_ready "http://localhost:18180/api/risk-control/summary" \
      || http_is_ready "http://localhost:8090/api/gateway-access/summary"; then
      echo "[FAIL] risk-review 目标端口已有外部服务，拒绝演示写入" >&2
      return 2
    fi
    export HSP_PAYMENT_RISK_CONTROL_ENABLED=true
  fi

  # 保持启动、验证和清理在同一受控会话，避免后台进程被运行器回收后留下 stale PID。
  trap 'run_stop "$scope"; exit 130' INT TERM
  trap 'run_stop "$scope"' EXIT
  run_doctor "$scope" || return $?
  run_start "$scope" "$verification_mode" || result=$?
  if [[ "$result" -eq 0 && "$scope" != "risk-review" ]]; then
    guard_started_local_payment_chain "$scope" || result=$?
  fi
  if [[ "$result" -eq 0 ]]; then
    run_report "$scope" "smoke" || result=$?
  fi
  if [[ "$result" -eq 0 ]]; then
    if [[ "$scope" == "risk-review" ]]; then
      HSP_LOCAL_RISK_REVIEW_SCOPED=true node "$ROOT_DIR/tools/dev/cross-system-risk-review.mjs" || result=$?
    elif [[ "$scope" == "payment-query-chain" ]]; then
      HSP_LOCAL_PAYMENT_CHAIN_SCOPED=true HSP_LOCAL_PAYMENT_QUERY_CHAIN_SCOPED=true \
        node "$ROOT_DIR/tools/dev/cross-system-happy-path.mjs" || result=$?
    else
      HSP_LOCAL_PAYMENT_CHAIN_SCOPED=true node "$ROOT_DIR/tools/dev/cross-system-happy-path.mjs" || result=$?
    fi
  fi
  run_stop "$scope" || [[ "$result" -ne 0 ]] || result=$?
  trap - INT TERM EXIT
  return "$result"
}

main() {
  local command_name="${1:-}"
  local scope="${2:-all}"

  case "$command_name" in
    doctor)
      run_doctor "$scope"
      ;;
    up)
      STACK_SCOPE="$scope"
      trap 'exit 130' INT TERM
      trap 'run_stop "${STACK_SCOPE:-all}"' EXIT
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
      run_verify payment-chain
      ;;
    verify)
      run_verify "$scope"
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
