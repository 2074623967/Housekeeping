# RabbitMQ test drill environment

This directory bootstraps the broker topology defined in `docs/2026-07-31-真实MQ接入与跨系统补偿演练方案.md`. It is a local or test-environment baseline, not production configuration.

## Start

```bash
docker compose -f infra/rabbitmq/docker-compose.yml up -d
```

The management UI is available at `http://127.0.0.1:15672` with `hsp / hsp-local-drill-20260801`. The AMQP endpoint is `amqp://127.0.0.1:5672`. These credentials are strictly for the checked-in localhost drill topology; do not reuse them outside this environment.

## Topology

- `payment.trade`: `payment.success.v1` for clearing and accounting.
- `clearing.trade`: `clearing.generated.v1` for settlement and accounting.
- Each consumer has a durable main queue, a five-minute retry queue and a DLQ.
- Main queues dead-letter to `*.dlq`; retry queues expire back to their main exchange.

Retry publishing must use the matching `*.retry` exchange and `*.retry.v1` routing key. This keeps a consumer failure isolated to its consumer queue instead of redelivering to all consumers of the original event.

## Drill rules

- Do not expose this localhost topology; production must inject credentials and use a dedicated vhost.
- Do not treat a broker start as a delivery gate pass. Record queue snapshots, business IDs, outbox state and downstream facts in the drill template before closing the gate.
