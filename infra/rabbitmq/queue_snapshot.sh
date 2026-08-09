#!/usr/bin/env bash

set -euo pipefail

RABBIT_CONTAINER="${RABBIT_CONTAINER:-hsp-rabbitmq}"
QUEUE_FILTER="${1:-}"

echo "# Queue Snapshot"
echo
echo "- generated_at: $(date '+%Y-%m-%d %H:%M:%S %z')"
echo "- container: ${RABBIT_CONTAINER}"
if [ -n "${QUEUE_FILTER}" ]; then
  echo "- filter: ${QUEUE_FILTER}"
fi
echo

list_cmd=(docker exec "${RABBIT_CONTAINER}" rabbitmqctl list_queues name messages_ready messages_unacknowledged consumers)
consumer_cmd=(docker exec "${RABBIT_CONTAINER}" rabbitmqctl list_consumers)

if [ -n "${QUEUE_FILTER}" ]; then
  echo "## Queues"
  echo
  "${list_cmd[@]}" | awk 'NR==1 || $1 ~ /'"${QUEUE_FILTER}"'/' || true
  echo
  echo "## Consumers"
  echo
  "${consumer_cmd[@]}" | awk 'NR==1 || $1 ~ /'"${QUEUE_FILTER}"'/' || true
else
  echo "## Queues"
  echo
  "${list_cmd[@]}"
  echo
  echo "## Consumers"
  echo
  "${consumer_cmd[@]}"
fi
