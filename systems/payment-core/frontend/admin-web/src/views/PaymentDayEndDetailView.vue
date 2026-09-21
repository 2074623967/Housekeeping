<script setup>
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentDayEndApi } from "../api/client";

const route = useRoute();
const router = useRouter();
const detail = ref(null);
const isLoading = ref(true);
const hasLoadedData = ref(false);
const loadErrorMessage = ref("");
let latestLoadRequestId = 0;

const currentBatchNo = computed(() => typeof route.params.batchNo === "string" ? route.params.batchNo : "");
const metricCards = computed(() => detail.value ? [
  { title: "批次状态", value: detail.value.batchStatus || "-", hint: detail.value.runMode || "-" },
  { title: "业务日期", value: detail.value.bizDate || "-", hint: detail.value.batchNo || "-" },
  { title: "支付成功差异", value: detail.value.paymentSuccessGapCount ?? 0, hint: detail.value.paymentSuccessGapAmount || "¥0.00" },
  { title: "对账准入", value: detail.value.reconciliationReadinessStatus || "UNKNOWN", hint: detail.value.reconciliationReadinessSummary || "-" }
] : []);

function formatValue(value) { return value == null || value === "" ? "-" : value; }

async function loadDetail(batchNo = currentBatchNo.value) {
  const requestId = ++latestLoadRequestId;
  isLoading.value = true;
  loadErrorMessage.value = "";
  try {
    const result = await paymentDayEndApi.getDetail(batchNo);
    if (requestId !== latestLoadRequestId || batchNo !== currentBatchNo.value) return;
    if (!result || result.batchNo !== batchNo) throw new Error("日终批次详情响应为空或与当前路由不一致");
    detail.value = result;
    hasLoadedData.value = true;
  } catch (error) {
    if (requestId !== latestLoadRequestId || batchNo !== currentBatchNo.value) return;
    loadErrorMessage.value = error.message;
  } finally {
    if (requestId === latestLoadRequestId) isLoading.value = false;
  }
}

watch(currentBatchNo, (batchNo) => {
  latestLoadRequestId += 1;
  detail.value = null;
  hasLoadedData.value = false;
  loadErrorMessage.value = "";
  loadDetail(batchNo);
}, { immediate: true });
</script>

<template>
  <div>
    <div class="topbar">
      <div><h2>支付日终批次详情</h2><p>按批次号精确核对业务日期、支付与退款事实、差异和对账准入结论</p></div>
      <div class="toolbar-actions">
        <button class="button secondary" @click="router.push('/payment-day-end')">返回日终处理</button>
        <button class="button primary" :disabled="isLoading" @click="loadDetail()">{{ isLoading ? "刷新中..." : "刷新" }}</button>
      </div>
    </div>
    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">{{ hasLoadedData ? "日终批次详情刷新失败，当前展示上次成功快照：" : "日终批次详情加载失败：" }}{{ loadErrorMessage }}</div>
      <div v-if="isLoading" class="state-box">日终批次详情加载中...</div>
      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的日终批次详情快照</div>
      <template v-else-if="detail">
        <div class="detail-card-grid"><div v-for="card in metricCards" :key="card.title" class="detail-card"><div class="detail-label">{{ card.title }}</div><div class="detail-value">{{ card.value }}</div><div class="meta">{{ card.hint }}</div></div></div>
        <section class="panel mini detail-panel"><div class="section-title"><h3>批次事实</h3><span class="meta">批次号唯一定位</span></div><div class="detail-grid detail-grid-wide">
          <div><strong>批次号：</strong><span class="mono-text">{{ formatValue(detail.batchNo) }}</span></div><div><strong>业务日期：</strong>{{ formatValue(detail.bizDate) }}</div>
          <div><strong>运行方式：</strong>{{ formatValue(detail.runMode) }}</div><div><strong>批次状态：</strong><span :class="['badge', detail.batchStatusType || 'info']">{{ formatValue(detail.batchStatus) }}</span></div>
          <div><strong>支付总单量：</strong>{{ formatValue(detail.paymentTotalCount) }}</div><div><strong>支付成功：</strong>{{ formatValue(detail.paymentSuccessCount) }} / {{ formatValue(detail.paymentSuccessAmount) }}</div>
          <div><strong>渠道成功：</strong>{{ formatValue(detail.channelSuccessCount) }} / {{ formatValue(detail.channelSuccessAmount) }}</div><div><strong>内部事件成功：</strong>{{ formatValue(detail.internalSuccessCount) }} / {{ formatValue(detail.internalSuccessAmount) }}</div>
          <div><strong>成功差异：</strong>{{ formatValue(detail.paymentSuccessGapCount) }} / {{ formatValue(detail.paymentSuccessGapAmount) }}</div><div><strong>待收口支付：</strong>{{ formatValue(detail.pendingPaymentCount) }} / {{ formatValue(detail.pendingPaymentAmount) }}</div>
          <div><strong>退款成功：</strong>{{ formatValue(detail.refundSuccessCount) }} / {{ formatValue(detail.refundSuccessAmount) }}</div><div><strong>退款失败：</strong>{{ formatValue(detail.refundFailCount) }} / {{ formatValue(detail.refundFailAmount) }}</div>
          <div><strong>待收口退款：</strong>{{ formatValue(detail.pendingRefundCount) }} / {{ formatValue(detail.pendingRefundAmount) }}</div><div><strong>渠道/内部异常：</strong>{{ formatValue(detail.channelAbnormalCount) }} / {{ formatValue(detail.internalAbnormalCount) }}</div>
          <div><strong>触发人：</strong>{{ formatValue(detail.triggeredBy) }}</div><div><strong>完成时间：</strong>{{ formatValue(detail.completedAt) }}</div>
        </div></section>
        <div class="ops-card"><div class="ops-title">对账准入判断</div><div class="timeline-item">{{ detail.reconciliationReadinessSummary || "暂无准入说明" }}</div><div class="timeline-item">执行备注：{{ detail.summaryComment || "-" }}</div></div>
        <div class="notice-banner">日终批次详情仅用于事实复核和对账前置判断，不代表渠道对账、内部账务核对或正式财务放行已经完成。</div>
      </template>
    </section>
  </div>
</template>
