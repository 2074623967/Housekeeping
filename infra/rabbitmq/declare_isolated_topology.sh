#!/usr/bin/env bash

set -euo pipefail

if [ "${1:-}" = "" ]; then
  echo "Usage: $0 <suffix>" >&2
  echo "Example: $0 20260803-a" >&2
  exit 1
fi

SUFFIX="$1"
RABBIT_HOST="${RABBIT_HOST:-127.0.0.1}"
RABBIT_PORT="${RABBIT_PORT:-15672}"
RABBIT_USER="${RABBIT_USER:-hsp}"
RABBIT_PASSWORD="${RABBIT_PASSWORD:-hsp-local-drill-20260801}"
RABBIT_VHOST="${RABBIT_VHOST:-/}"
RETRY_TTL_MS="${RETRY_TTL_MS:-300000}"

urlencode() {
  python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"
}

API_BASE="http://${RABBIT_HOST}:${RABBIT_PORT}/api"
VHOST_ENCODED="$(urlencode "${RABBIT_VHOST}")"

api_put() {
  local path="$1"
  local payload="$2"
  curl -fsS -u "${RABBIT_USER}:${RABBIT_PASSWORD}" \
    -H 'content-type: application/json' \
    -X PUT "${API_BASE}${path}" \
    -d "${payload}" >/dev/null
}

api_post() {
  local path="$1"
  local payload="$2"
  curl -fsS -u "${RABBIT_USER}:${RABBIT_PASSWORD}" \
    -H 'content-type: application/json' \
    -X POST "${API_BASE}${path}" \
    -d "${payload}" >/dev/null
}

declare_queue() {
  local queue_name="$1"
  local arguments="$2"
  api_put "/queues/${VHOST_ENCODED}/$(urlencode "${queue_name}")" \
    "{\"auto_delete\":false,\"durable\":true,\"arguments\":${arguments}}"
}

declare_binding() {
  local exchange_name="$1"
  local queue_name="$2"
  local routing_key="$3"
  api_post "/bindings/${VHOST_ENCODED}/e/$(urlencode "${exchange_name}")/q/$(urlencode "${queue_name}")" \
    "{\"routing_key\":\"${routing_key}\",\"arguments\":{}}"
}

create_payment_topology() {
  local consumer_name="$1"
  local main_queue="${consumer_name}.payment-success.${SUFFIX}"
  local retry_queue="${main_queue}.retry"
  local dlq_queue="${main_queue}.dlq"
  local retry_key="payment.success.${consumer_name}.retry.${SUFFIX}.v1"
  local replay_key="payment.success.${consumer_name}.replay.${SUFFIX}.v1"
  local dlq_key="payment.success.${consumer_name}.dlq.${SUFFIX}.v1"

  declare_queue "${main_queue}" "{\"x-dead-letter-exchange\":\"payment.trade.dlq\",\"x-dead-letter-routing-key\":\"${dlq_key}\"}"
  declare_queue "${retry_queue}" "{\"x-message-ttl\":${RETRY_TTL_MS},\"x-dead-letter-exchange\":\"payment.trade.replay\",\"x-dead-letter-routing-key\":\"${replay_key}\"}"
  declare_queue "${dlq_queue}" "{}"

  declare_binding "payment.trade" "${main_queue}" "payment.success.${SUFFIX}.v1"
  declare_binding "payment.trade.retry" "${retry_queue}" "${retry_key}"
  declare_binding "payment.trade.dlq" "${dlq_queue}" "${dlq_key}"
  declare_binding "payment.trade.dlq" "payment.compensation.dlq-intake" "${dlq_key}"
  declare_binding "payment.trade.replay" "${main_queue}" "${replay_key}"
}

create_clearing_topology() {
  local consumer_name="$1"
  local main_queue="${consumer_name}.clearing-generated.${SUFFIX}"
  local retry_queue="${main_queue}.retry"
  local dlq_queue="${main_queue}.dlq"
  local retry_key="clearing.generated.${consumer_name}.retry.${SUFFIX}.v1"
  local replay_key="clearing.generated.${consumer_name}.replay.${SUFFIX}.v1"
  local dlq_key="clearing.generated.${consumer_name}.dlq.${SUFFIX}.v1"

  declare_queue "${main_queue}" "{\"x-dead-letter-exchange\":\"clearing.trade.dlq\",\"x-dead-letter-routing-key\":\"${dlq_key}\"}"
  declare_queue "${retry_queue}" "{\"x-message-ttl\":${RETRY_TTL_MS},\"x-dead-letter-exchange\":\"clearing.trade.replay\",\"x-dead-letter-routing-key\":\"${replay_key}\"}"
  declare_queue "${dlq_queue}" "{}"

  declare_binding "clearing.trade" "${main_queue}" "clearing.generated.${SUFFIX}.v1"
  declare_binding "clearing.trade.retry" "${retry_queue}" "${retry_key}"
  declare_binding "clearing.trade.dlq" "${dlq_queue}" "${dlq_key}"
  declare_binding "clearing.trade.dlq" "payment.compensation.dlq-intake" "${dlq_key}"
  declare_binding "clearing.trade.replay" "${main_queue}" "${replay_key}"
}

create_payment_topology "clearing"
create_payment_topology "accounting"
create_clearing_topology "settlement"
create_clearing_topology "accounting"

cat <<EOF
Isolated RabbitMQ topology declared for suffix: ${SUFFIX}
Retry TTL (ms): ${RETRY_TTL_MS}

Recommended environment variables:

payment-core:
  HSP_PAYMENT_EVENT_ROUTING_KEY=payment.success.${SUFFIX}.v1

clearing-system:
  HSP_CLEARING_AMQP_ENABLED=true
  HSP_CLEARING_AMQP_PAYMENT_SUCCESS_QUEUE=clearing.payment-success.${SUFFIX}
  HSP_CLEARING_AMQP_RETRY_ROUTING_KEY=payment.success.clearing.retry.${SUFFIX}.v1
  HSP_CLEARING_AMQP_DEAD_LETTER_ROUTING_KEY=payment.success.clearing.dlq.${SUFFIX}.v1
  HSP_CLEARING_AMQP_CLEARING_GENERATED_ROUTING_KEY=clearing.generated.${SUFFIX}.v1

settlement-system:
  HSP_SETTLEMENT_AMQP_ENABLED=true
  HSP_SETTLEMENT_AMQP_CLEARING_GENERATED_QUEUE=settlement.clearing-generated.${SUFFIX}
  HSP_SETTLEMENT_AMQP_RETRY_ROUTING_KEY=clearing.generated.settlement.retry.${SUFFIX}.v1
  HSP_SETTLEMENT_AMQP_DEAD_LETTER_ROUTING_KEY=clearing.generated.settlement.dlq.${SUFFIX}.v1

accounting-system:
  HSP_ACCOUNTING_AMQP_ENABLED=true
  HSP_ACCOUNTING_AMQP_PAYMENT_SUCCESS_QUEUE=accounting.payment-success.${SUFFIX}
  HSP_ACCOUNTING_AMQP_RETRY_ROUTING_KEY=payment.success.accounting.retry.${SUFFIX}.v1
  HSP_ACCOUNTING_AMQP_DEAD_LETTER_ROUTING_KEY=payment.success.accounting.dlq.${SUFFIX}.v1
  HSP_ACCOUNTING_AMQP_CLEARING_GENERATED_QUEUE=accounting.clearing-generated.${SUFFIX}
  HSP_ACCOUNTING_AMQP_CLEARING_GENERATED_RETRY_ROUTING_KEY=clearing.generated.accounting.retry.${SUFFIX}.v1
  HSP_ACCOUNTING_AMQP_CLEARING_GENERATED_DEAD_LETTER_ROUTING_KEY=clearing.generated.accounting.dlq.${SUFFIX}.v1
EOF
