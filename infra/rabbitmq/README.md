# RabbitMQ test drill environment

This directory bootstraps the broker topology defined in `docs/2026-07-31-真实MQ接入与跨系统补偿演练方案.md`. It is a local or test-environment baseline, not production configuration.

## Start

```bash
docker compose -f infra/rabbitmq/docker-compose.yml up -d
```

The management UI is available at `http://127.0.0.1:15672` with `hsp / hsp-local-drill-20260801`. The AMQP endpoint is `amqp://127.0.0.1:5672`. These credentials are strictly for the checked-in localhost drill topology; do not reuse them outside this environment.

## Isolated drill topology

When the default queues already contain historical retry or DLQ messages, declare an isolated queue set before running a new evidence round:

```bash
bash infra/rabbitmq/declare_isolated_topology.sh 20260803-a
```

For a fast retry drill, override the retry TTL before declaring the isolated topology:

```bash
RETRY_TTL_MS=2000 bash infra/rabbitmq/declare_isolated_topology.sh 20260803-fast
```

The script creates a suffix-scoped queue / retry queue / DLQ set for:

- `clearing.payment-success`
- `accounting.payment-success`
- `settlement.clearing-generated`
- `accounting.clearing-generated`

It also prints the environment variables that the four local services should use for the isolated drill.
`RETRY_TTL_MS` defaults to `300000` and can be lowered for rapid replay / retry verification.
The suffix must start with a letter or digit and contain only letters, digits or hyphens (at most 41 characters). The script rejects invalid suffixes and retry TTL values before issuing any Management API writes. Its printed payment-core routing variable and payment/clearing AMQP dispatch switches match the current application configuration; apply those settings to the isolated services before a drill.

## Queue snapshot

Capture the current queue and consumer state before and after each drill:

```bash
bash infra/rabbitmq/queue_snapshot.sh
bash infra/rabbitmq/queue_snapshot.sh '20260803-a|payment.compensation'
```

## 当前批次 DLQ/replay 取证

RabbitMQ 与 payment-core、下游消费者均启动后，可使用仓库内脚本生成新的同批次补偿证据：

执行前在脚本进程中配置与运行中的 `payment-core` 一致的 `HSP_PAYMENT_SUCCESS_AMQP_SECRET`（支付成功模式）或 `HSP_CLEARING_GENERATED_AMQP_SECRET`（清分生成模式）。不要在命令行、日志或仓库文档中填写真实密钥。

```bash
HSP_DLQ_SUFFIX=20260923-a \
HSP_DLQ_TARGET_SYSTEM=accounting \
node tools/dev/dlq-compensation-smoke.mjs
```

脚本默认覆盖 `payment.success.* -> accounting`，也支持：

```bash
HSP_DLQ_SUFFIX=20260923-a \
HSP_DLQ_EVENT_KIND=clearing-generated \
HSP_DLQ_TARGET_SYSTEM=settlement \
node tools/dev/dlq-compensation-smoke.mjs
```

增加 `HSP_DLQ_INCLUDE_MANUAL=true` 可在同一运行中额外注入一条非法报文并核验 `MANUAL_RESOLVED`。脚本要求隔离 suffix，不接受历史任务号；会通过 Management API 发布新消息，轮询 `PENDING_REVIEW` 任务，执行人工处置和定向 replay，并查询对应下游事实。RabbitMQ 不可用时会直接失败，不会退回历史证据。

脚本对将要发布的原始 UTF-8 JSON 字节签名；缺密钥时不发布，入站任务 `sourceSignatureVerified` 不为 `true` 时不发起复核或重放。可选非法报文样本故意不签名，只允许人工结案。脚本通过 Management API **直接向隔离 DLQ 注入合成消息**，只检查签名 intake、复核/重放及下游查询的局部接线；即便运行成功，也不能证明消息经过真实主队列消费者、重试 TTL、死信转发、生产身份审批或资金处理。真实链路须另行从主队列注入、采集队列快照和下游事实。

可用 `HSP_DLQ_TIMEOUT_MS` 和 `HSP_DLQ_POLL_MS` 调整轮询超时与间隔，两者必须是正数；可用 `HSP_DLQ_PAYLOAD_JSON` 提供当前批次自定义 JSON 报文，必须是对象、事件类型必须匹配所选模式，且必须包含对应业务单号。参数或 JSON 非法时，脚本会在发布消息前失败，不产生伪造的运行态结果。

The snapshot output is designed to paste directly into `docs/2026-07-31-MQ补偿演练记录模板.md`.

## Topology

- `payment.trade`: `payment.success.v1` for clearing and accounting.
- `clearing.trade`: `clearing.generated.v1` for settlement and accounting.
- Each consumer has a durable main queue, a five-minute retry queue and a DLQ.
- Main queues dead-letter to `*.dlq`; retry queues expire back to their main exchange.

Retry publishing must use the matching `*.retry` exchange and `*.retry.v1` routing key. This keeps a consumer failure isolated to its consumer queue instead of redelivering to all consumers of the original event.

## Drill rules

- Do not expose this localhost topology; production must inject credentials and use a dedicated vhost.
- Do not treat a broker start as a delivery gate pass. Record queue snapshots, business IDs, outbox state and downstream facts in the drill template before closing the gate.
