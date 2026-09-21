<script setup>
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentDeadLetterTaskApi } from "../api/client";

const route = useRoute();
const router = useRouter();
const detail = ref(null);
const isLoading = ref(true);
const hasLoadedData = ref(false);
const loadErrorMessage = ref("");
let latestLoadRequestId = 0;

const currentTaskNo = computed(() => typeof route.params.taskNo === "string" ? route.params.taskNo : "");
const isSnapshotStale = computed(() => hasLoadedData.value && Boolean(loadErrorMessage.value));
const statusLabel = (status) => ({
  PENDING_REVIEW: "待复核",
  READY_TO_REPLAY: "待重放",
  REPLAYING: "重放中",
  REPLAYED: "已重放",
  REPLAY_FAILED: "重放失败",
  MANUAL_RESOLVED: "人工结案"
}[status] || status || "-");
const metricCards = computed(() => detail.value ? [
  { title: "当前状态", value: statusLabel(detail.value.taskStatus), hint: detail.value.targetSystem || "目标系统未记录" },
  { title: "重放次数", value: detail.value.replayCount ?? 0, hint: detail.value.lastReplayAt || "尚未重放" },
  { title: "消息 ID", value: detail.value.messageId || "-", hint: detail.value.correlationId || "无关联 ID" },
  { title: "创建时间", value: detail.value.createdAt || "-", hint: detail.value.updatedAt || "无最近更新时间" }
] : []);

function formatValue(value) {
  return value == null || value === "" ? "-" : value;
}

async function loadDetail(taskNo = currentTaskNo.value) {
  const requestId = ++latestLoadRequestId;
  isLoading.value = true;
  loadErrorMessage.value = "";
  try {
    const result = await paymentDeadLetterTaskApi.getDetail(taskNo);
    if (requestId !== latestLoadRequestId || taskNo !== currentTaskNo.value) return;
    if (!result || result.taskNo !== taskNo) throw new Error("死信任务详情响应为空或与当前路由不一致");
    detail.value = result;
    hasLoadedData.value = true;
  } catch (error) {
    if (requestId !== latestLoadRequestId || taskNo !== currentTaskNo.value) return;
    loadErrorMessage.value = error.message;
  } finally {
    if (requestId === latestLoadRequestId) isLoading.value = false;
  }
}

watch(currentTaskNo, (taskNo) => {
  latestLoadRequestId += 1;
  detail.value = null;
  hasLoadedData.value = false;
  loadErrorMessage.value = "";
  loadDetail(taskNo);
}, { immediate: true });
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>死信补偿任务详情</h2>
        <p>按任务号精确核对死信消息、受控重放路由和人工处置事实</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" @click="router.push('/payment-dead-letter-tasks')">返回死信任务</button>
        <button class="button primary" :disabled="isLoading" @click="loadDetail()">{{ isLoading ? "刷新中..." : "刷新" }}</button>
      </div>
    </div>
    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">
        {{ hasLoadedData ? "死信任务详情刷新失败，当前展示上次成功快照：" : "死信任务详情加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="isLoading" class="state-box">死信任务详情加载中...</div>
      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的死信任务详情快照</div>
      <template v-else-if="detail">
        <div class="detail-card-grid">
          <div v-for="card in metricCards" :key="card.title" class="detail-card">
            <div class="detail-label">{{ card.title }}</div>
            <div class="detail-value">{{ card.value }}</div>
            <div class="meta">{{ card.hint }}</div>
          </div>
        </div>
        <div class="split-panels detail-panel">
          <section class="panel mini">
            <div class="section-title"><h3>任务事实</h3><span class="meta">任务号唯一定位</span></div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>任务号：</strong><span class="mono-text">{{ formatValue(detail.taskNo) }}</span></div>
              <div><strong>消息 ID：</strong><span class="mono-text">{{ formatValue(detail.messageId) }}</span></div>
              <div><strong>关联 ID：</strong>{{ formatValue(detail.correlationId) }}</div>
              <div><strong>目标系统：</strong>{{ formatValue(detail.targetSystem) }}</div>
              <div><strong>任务状态：</strong><span :class="['badge', detail.taskStatusType || 'info']">{{ statusLabel(detail.taskStatus) }}</span></div>
              <div><strong>重放次数：</strong>{{ formatValue(detail.replayCount) }}</div>
              <div><strong>死信路由：</strong><span class="mono-text">{{ formatValue(detail.deadLetterRoutingKey) }}</span></div>
              <div><strong>重放交换机：</strong><span class="mono-text">{{ formatValue(detail.replayExchange) }}</span></div>
              <div><strong>重放路由：</strong><span class="mono-text">{{ formatValue(detail.replayRoutingKey) }}</span></div>
              <div><strong>最后处置人：</strong>{{ formatValue(detail.operator) }}</div>
              <div><strong>处置时间：</strong>{{ formatValue(detail.resolvedAt) }}</div>
              <div><strong>最近重放：</strong>{{ formatValue(detail.lastReplayAt) }}</div>
            </div>
          </section>
          <section class="panel mini">
            <div class="section-title"><h3>处置留痕</h3><span class="meta">只读事实</span></div>
            <div class="ops-card"><div class="ops-title">处置说明</div><div class="timeline-item">{{ detail.resolutionNote || "尚未记录人工处置说明" }}</div></div>
            <div class="ops-card"><div class="ops-title">边界说明</div><div class="timeline-item">重放确认只代表 payment-core 已向原消费者路由重新发布消息，不代表目标系统已消费、入账或完成业务处理。</div></div>
          </section>
        </div>
        <div class="payload-grid snapshot-grid">
          <div><strong>消息体脱敏快照</strong><pre>{{ detail.payloadSnapshot || "-" }}</pre></div>
          <div><strong>消息头脱敏快照</strong><pre>{{ detail.headerSnapshot || "-" }}</pre></div>
        </div>
        <div v-if="isSnapshotStale" class="notice-banner">当前快照已过期，请刷新后再返回列表执行处置动作。</div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.snapshot-grid { grid-template-columns: 1fr 1fr; }
.snapshot-grid pre { max-height: 320px; }
@media (max-width: 900px) { .snapshot-grid { grid-template-columns: 1fr; } }
</style>
