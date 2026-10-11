<script setup>
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { matchesPaymentTaskRunDetailSnapshot, paymentTaskCenterApi } from "../api/client";

const route = useRoute();
const router = useRouter();
const detail = ref(null);
const isLoading = ref(true);
const hasLoadedData = ref(false);
const loadErrorMessage = ref("");
let latestLoadRequestId = 0;
const currentTaskLogNo = computed(() => typeof route.params.taskLogNo === "string" ? route.params.taskLogNo : "");
const metricCards = computed(() => detail.value ? [
  { title: "任务状态", value: detail.value.taskStatus || "-", hint: detail.value.taskCode || "-" },
  { title: "处理总数", value: detail.value.processedCount ?? 0, hint: `成功 ${detail.value.successCount ?? 0} / 失败 ${detail.value.failCount ?? 0}` },
  { title: "告警数", value: detail.value.warningCount ?? 0, hint: detail.value.severityLevel || "-" },
  { title: "升级状态", value: detail.value.escalationStatus || "-", hint: detail.value.runMode || "-" }
] : []);
function formatValue(value) { return value == null || value === "" ? "-" : value; }
async function loadDetail(taskLogNo = currentTaskLogNo.value) {
  const requestId = ++latestLoadRequestId;
  const expectedSnapshot = taskLogNo === currentTaskLogNo.value && detail.value
    ? {
      taskLogNo: detail.value.taskLogNo,
      taskCode: detail.value.taskCode,
      bizDate: detail.value.bizDate,
      businessRefNo: detail.value.businessRefNo
    }
    : null;
  isLoading.value = true;
  loadErrorMessage.value = "";
  try {
    const result = await paymentTaskCenterApi.getTaskRunDetail(taskLogNo);
    if (requestId !== latestLoadRequestId || taskLogNo !== currentTaskLogNo.value) return;
    if (!result || result.taskLogNo !== taskLogNo) throw new Error("任务详情响应为空或与当前路由不一致");
    if (expectedSnapshot && !matchesPaymentTaskRunDetailSnapshot(expectedSnapshot, result)) {
      throw new Error("任务详情响应为空或与当前执行上下文快照不一致");
    }
    detail.value = result;
    hasLoadedData.value = true;
  } catch (error) {
    if (requestId !== latestLoadRequestId || taskLogNo !== currentTaskLogNo.value) return;
    loadErrorMessage.value = error.message;
  } finally {
    if (requestId === latestLoadRequestId) isLoading.value = false;
  }
}
watch(currentTaskLogNo, (taskLogNo) => {
  latestLoadRequestId += 1;
  detail.value = null;
  hasLoadedData.value = false;
  loadErrorMessage.value = "";
  loadDetail(taskLogNo);
}, { immediate: true });
</script>

<template>
  <div>
    <div class="topbar"><div><h2>支付任务执行详情</h2><p>按日志号精确核对任务运行状态、处理结果、告警和升级事实</p></div><div class="toolbar-actions"><button class="button secondary" @click="router.push('/payment-task-center')">返回任务中心</button><button class="button primary" :disabled="isLoading" @click="loadDetail()">{{ isLoading ? "刷新中..." : "刷新" }}</button></div></div>
    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">{{ hasLoadedData ? "任务详情刷新失败，当前展示上次成功快照：" : "任务详情加载失败：" }}{{ loadErrorMessage }}</div>
      <div v-if="isLoading" class="state-box">任务执行详情加载中...</div>
      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的任务详情快照</div>
      <template v-else-if="detail">
        <div class="detail-card-grid"><div v-for="card in metricCards" :key="card.title" class="detail-card"><div class="detail-label">{{ card.title }}</div><div class="detail-value">{{ card.value }}</div><div class="meta">{{ card.hint }}</div></div></div>
        <section class="panel mini detail-panel"><div class="section-title"><h3>任务运行事实</h3><span class="meta">日志号唯一定位</span></div><div class="detail-grid detail-grid-wide">
          <div><strong>日志号：</strong><span class="mono-text">{{ formatValue(detail.taskLogNo) }}</span></div><div><strong>任务编码：</strong>{{ formatValue(detail.taskCode) }}</div>
          <div><strong>任务名称：</strong>{{ formatValue(detail.taskName) }}</div><div><strong>运行方式：</strong>{{ formatValue(detail.runMode) }}</div>
          <div><strong>业务日期：</strong>{{ formatValue(detail.bizDate) }}</div><div><strong>业务关联号：</strong>{{ formatValue(detail.businessRefNo) }}</div>
          <div><strong>任务状态：</strong><span :class="['badge', detail.taskStatusType || 'info']">{{ formatValue(detail.taskStatus) }}</span></div><div><strong>严重等级：</strong>{{ formatValue(detail.severityLevel) }}</div>
          <div><strong>升级状态：</strong>{{ formatValue(detail.escalationStatus) }}</div><div><strong>触发人：</strong>{{ formatValue(detail.triggeredBy) }}</div>
          <div><strong>开始时间：</strong>{{ formatValue(detail.startedAt) }}</div><div><strong>完成时间：</strong>{{ formatValue(detail.completedAt) }}</div>
        </div></section>
        <div class="ops-card"><div class="ops-title">执行摘要</div><div class="timeline-item">{{ detail.summaryComment || "-" }}</div><div class="timeline-item">建议动作：{{ detail.suggestedAction || "-" }}</div></div>
        <div class="notice-banner">详情页只读展示任务执行事实；需要重新执行时返回任务中心，在刷新成功且满足当前候选条件后操作。</div>
      </template>
    </section>
  </div>
</template>
