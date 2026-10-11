<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { matchesPaymentRecordDetailSnapshot, paymentApi, paymentRecordApi } from "../api/client";
import { normalizePaymentRecordType, paymentDetailRoute } from "../router/detailRoutes";
import { paymentLogsRoute, paymentRequestsRoute, refundsRoute } from "../router/listRoutes";
import { canQueryPaymentChannel, isChannelSubmissionUnaccepted, paymentRecordQuerySuggestion } from "../utils/paymentAction";

const route = useRoute();
const router = useRouter();
const detail = ref(null);
const isLoading = ref(true);
const hasLoadedData = ref(false);
const loadErrorMessage = ref("");
const actionRefreshErrorMessage = ref("");
const actionErrorMessage = ref("");
const actionResultMessage = ref("");
const activeAction = ref("");
const operationOperator = ref("");
let latestLoadRequestId = 0;

const isSnapshotStale = computed(() => Boolean(
  hasLoadedData.value && (loadErrorMessage.value || actionRefreshErrorMessage.value)
));
const currentPaymentOrderId = computed(() => typeof route.params.paymentOrderId === "string"
  ? route.params.paymentOrderId
  : "");
const canUseSnapshot = computed(() => Boolean(
  hasLoadedData.value
  && detail.value?.paymentOrderId === currentPaymentOrderId.value
  && !isLoading.value
  && !isSnapshotStale.value
  && !activeAction.value
));

const recordType = computed(() => normalizePaymentRecordType(route.query.recordType));
const recordTypeLabel = computed(() => {
  if (recordType.value === "WECHAT") {
    return "微信支付宝支付记录";
  }
  if (recordType.value === "BANK_CARD") {
    return "银行卡支付记录";
  }
  return "统一支付记录";
});

const canRunQuery = computed(() => canQueryPaymentChannel(
  detail.value?.paymentStatus,
  detail.value?.latestAttemptStatus,
  detail.value?.latestAttemptCompletedAt,
  detail.value?.externalTransactionNo,
  detail.value?.paymentChannel
) && !detail.value?.successFactIssue && !detail.value?.prepayBindingIssue);

async function loadDetail(afterAction = false, paymentOrderId = currentPaymentOrderId.value) {
  const requestId = ++latestLoadRequestId;
  const expectedSnapshot = paymentOrderId === currentPaymentOrderId.value && detail.value
    ? {
      paymentOrderId: detail.value.paymentOrderId,
      businessChainId: detail.value.businessChainId,
      businessOrderNo: detail.value.businessOrderNo,
      payScene: detail.value.payScene,
      paymentMethod: detail.value.paymentMethod,
      paymentAmount: detail.value.paymentAmount
    }
    : null;
  isLoading.value = true;
  if (afterAction) {
    actionRefreshErrorMessage.value = "";
  } else {
    loadErrorMessage.value = "";
  }
  try {
    const paymentRecordDetail = await paymentRecordApi.getDetail(paymentOrderId);
    if (requestId !== latestLoadRequestId || paymentOrderId !== currentPaymentOrderId.value) {
      return;
    }
    if (!paymentRecordDetail || paymentRecordDetail.paymentOrderId !== paymentOrderId) {
      throw new Error("支付记录详情响应为空或与当前路由不一致");
    }
    if (expectedSnapshot && !matchesPaymentRecordDetailSnapshot(expectedSnapshot, paymentRecordDetail)) {
      throw new Error("支付记录详情响应为空或与当前订单/金额快照不一致");
    }
    detail.value = paymentRecordDetail;
    hasLoadedData.value = true;
    loadErrorMessage.value = "";
    actionRefreshErrorMessage.value = "";
    if (!afterAction) {
      actionErrorMessage.value = "";
      actionResultMessage.value = "";
    }
  } catch (error) {
    if (requestId !== latestLoadRequestId || paymentOrderId !== currentPaymentOrderId.value) {
      return;
    }
    if (afterAction) {
      actionRefreshErrorMessage.value = error.message;
    } else {
      loadErrorMessage.value = error.message;
      actionRefreshErrorMessage.value = "";
    }
  } finally {
    if (requestId === latestLoadRequestId) {
      isLoading.value = false;
    }
  }
}

function goBackToList() {
  const pathMap = {
    ALL: "/payment-records",
    WECHAT: "/payment-records/wechat-alipay",
    BANK_CARD: "/payment-records/bank-card"
  };
  router.push(pathMap[recordType.value] || "/payment-records");
}

function openPaymentDetail() {
  if (!canUseSnapshot.value || detail.value?.successFactIssue || detail.value?.prepayBindingIssue) {
    return;
  }
  router.push(paymentDetailRoute(detail.value.paymentOrderId));
}

function openPaymentRequests() {
  if (!canUseSnapshot.value) {
    return;
  }
  router.push(paymentRequestsRoute({ paymentOrderId: detail.value.paymentOrderId }));
}

function openPaymentLogs() {
  if (!canUseSnapshot.value) {
    return;
  }
  router.push(paymentLogsRoute({ paymentOrderId: detail.value.paymentOrderId }));
}

function openUnverifiedRefunds() {
  if (!canUseSnapshot.value || !(detail.value?.unverifiedRefundCount > 0)) {
    return;
  }
  router.push(refundsRoute({
    paymentOrderId: detail.value.paymentOrderId,
    refundStatus: "UNVERIFIED_SUCCESS"
  }));
}

function formatValue(value) {
  return value === null || value === undefined || value === "" ? "—" : value;
}

function formatTextBlock(value) {
  return value || "暂无报文数据";
}

const metricCards = computed(() => {
  if (!detail.value) {
    return [];
  }
  return [
    {
      title: "支付金额",
      value: formatValue(detail.value.paymentAmount),
      hint: `已退 ${formatValue(detail.value.refundedAmount)} / 退款次数 ${formatValue(detail.value.refundCount)}`
    },
    {
      title: "路由轨迹数",
      value: `${(detail.value.routeLogs || []).length}`,
      hint: "用于判断路由选型与重试轨迹"
    },
    {
      title: "回调轨迹数",
      value: `${(detail.value.notifyLogs || []).length}`,
      hint: "用于判断回调是否已收口"
    },
    {
      title: "事件轨迹数",
      value: `${(detail.value.eventLogs || []).length}`,
      hint: "用于判断下游事件是否完整发布"
    }
  ];
});

const operationSuggestions = computed(() => {
  if (!detail.value) {
    return [];
  }
  const suggestions = [];
  if (detail.value.successFactIssue) {
    suggestions.push(`成功事实异常（${detail.value.successFactIssue}），保留支付记录快照并联查请求和日志；勿将当前状态视为可信成功。`);
  }
  if (detail.value.prepayBindingIssue) {
    suggestions.push(detail.value.prepayBindingIssue === "PREPAY_BINDING_MISSING"
      ? "当前支付单缺少精确预付归属，来源场景和失效时间不可判定；先人工审计，勿据此查单或重新提交。"
      : "同一支付单关联多张预付单，来源场景和失效时间不可判定；先人工审计，勿据此查单或重新提交。");
  }
  if (detail.value.unverifiedRefundCount > 0) {
    suggestions.push(`${detail.value.unverifiedRefundCount} 笔成功退款事实待核查，尚未计入已退金额；请下钻退款异常记录。`);
  }
  const querySuggestion = paymentRecordQuerySuggestion(detail.value);
  if ((detail.value.notifyLogs || []).length === 0
    && !isChannelSubmissionUnaccepted(detail.value.paymentStatus, detail.value.latestAttemptStatus, detail.value.externalTransactionNo)) {
    suggestions.push("当前无回调轨迹，优先核对渠道回调是否到达与通知地址是否配置正确。");
  }
  if ((detail.value.eventLogs || []).length === 0) {
    suggestions.push("当前无事件轨迹，建议联查支付处理日志和事件出站记录。");
  }
  if (querySuggestion) {
    suggestions.push(querySuggestion);
  }
  if (!suggestions.length) {
    suggestions.push("当前记录链路完整，可结合支付单详情继续做业务复盘。");
  }
  return suggestions;
});

async function handleQueryPayment() {
  if (!canUseSnapshot.value || !canRunQuery.value) {
    actionErrorMessage.value = "当前支付单没有可供渠道查单的提交事实。";
    actionResultMessage.value = "";
    return;
  }
  const operator = operationOperator.value.trim();
  if (!operator) {
    actionErrorMessage.value = "请先输入操作人。";
    actionResultMessage.value = "";
    return;
  }
  const paymentOrderId = currentPaymentOrderId.value;
  activeAction.value = "query";
  actionErrorMessage.value = "";
  actionResultMessage.value = "";
  try {
    const paymentDetail = await paymentApi.query(paymentOrderId, operator, {
      orderNo: detail.value?.businessOrderNo,
      amount: detail.value?.paymentAmount,
      payScene: detail.value?.payScene
    });
    if (paymentOrderId !== currentPaymentOrderId.value) {
      return;
    }
    loadErrorMessage.value = "";
    actionRefreshErrorMessage.value = "";
    actionResultMessage.value = `支付单 ${paymentOrderId} 已完成主动查单，当前状态为 ${paymentDetail.status}。`;
    await loadDetail(true, paymentOrderId);
  } catch (error) {
    if (paymentOrderId !== currentPaymentOrderId.value) {
      return;
    }
    actionErrorMessage.value = `主动查单失败：${error.message}`;
  } finally {
    activeAction.value = "";
  }
}

onMounted(loadDetail);

watch(currentPaymentOrderId, (paymentOrderId) => {
  detail.value = null;
  hasLoadedData.value = false;
  actionRefreshErrorMessage.value = "";
  actionErrorMessage.value = "";
  actionResultMessage.value = "";
  operationOperator.value = "";
  loadDetail(false, paymentOrderId);
});
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付记录详情</h2>
        <p>从收款记录反查支付尝试、路由、回调、事件和报文，补齐支付运营排查视角</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" @click="goBackToList">返回{{ recordTypeLabel }}</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !!detail?.successFactIssue || !!detail?.prepayBindingIssue" @click="openPaymentDetail">查看支付单详情</button>
        <button class="button secondary" :disabled="!canUseSnapshot" @click="openPaymentRequests">查看支付请求</button>
        <button class="button secondary" :disabled="!canUseSnapshot" @click="openPaymentLogs">查看处理日志</button>
        <button v-if="detail?.unverifiedRefundCount > 0" class="button secondary" :disabled="!canUseSnapshot" @click="openUnverifiedRefunds">查看待核查退款</button>
        <button class="button secondary" :disabled="isLoading || Boolean(activeAction)" @click="loadDetail()">刷新</button>
        <button class="button primary" :disabled="!canUseSnapshot || !canRunQuery" @click="handleQueryPayment">
          {{ activeAction === "query" ? "查单中..." : "主动查单" }}
        </button>
      </div>
    </div>

    <section class="panel">
      <div class="toolbar compact-toolbar">
        <div class="field">
          <label>操作人</label>
          <input v-model.trim="operationOperator" maxlength="64" placeholder="请输入操作人" :disabled="isLoading || Boolean(activeAction) || !hasLoadedData || isSnapshotStale" />
        </div>
      </div>
      <div v-if="loadErrorMessage" class="error-banner">
        {{ hasLoadedData ? "支付记录详情刷新失败，当前展示上次成功快照：" : "支付记录详情加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="actionRefreshErrorMessage" class="error-banner">
        主动查单结果已确认，但支付记录详情刷新失败，当前展示上次成功快照：{{ actionRefreshErrorMessage }}
      </div>
      <div v-if="actionErrorMessage" class="error-banner">{{ actionErrorMessage }}</div>
      <div v-if="actionResultMessage" class="state-banner">{{ actionResultMessage }}</div>
      <div v-if="isLoading" class="state-box">支付记录详情加载中...</div>
      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的支付记录详情快照</div>
      <template v-else-if="detail">
        <div v-if="detail.successFactIssue" class="error-banner">
          所属支付单成功事实异常（{{ detail.successFactIssue }}）；保留支付记录详情，支付单详情不可用。
        </div>
        <div v-if="detail.unverifiedRefundCount > 0" class="risk-banner">
          {{ detail.unverifiedRefundCount }} 笔成功退款事实待核查，当前已退金额仅包含可信成功退款。
        </div>
        <div class="detail-card-grid">
          <div class="detail-card">
            <div class="detail-label">支付单号</div>
            <div class="detail-value">{{ formatValue(detail.paymentOrderId) }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">业务链路</div>
            <div class="detail-value">{{ formatValue(detail.businessChainId) }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">支付状态</div>
            <div class="detail-value">
              <span :class="['badge', detail.statusType]">{{ formatValue(detail.paymentStatus) }}</span> <span v-if="isChannelSubmissionUnaccepted(detail.paymentStatus, detail.latestAttemptStatus, detail.externalTransactionNo)" class="badge warn">渠道受理未确认</span>
            </div>
          </div>
          <div class="detail-card">
            <div class="detail-label">支付方式</div>
            <div class="detail-value">{{ formatValue(detail.paymentMethod) }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">最近一次尝试状态</div>
            <div class="detail-value">
              <span :class="['badge', detail.latestAttemptStatusType || 'info']">
                {{ formatValue(detail.latestAttemptStatus) }}
              </span>
            </div>
          </div>
        </div>

        <div class="detail-card-grid detail-panel">
          <div v-for="card in metricCards" :key="card.title" class="detail-card">
            <div class="detail-label">{{ card.title }}</div>
            <div class="detail-value">{{ card.value }}</div>
            <div class="meta">{{ card.hint }}</div>
          </div>
        </div>

        <div class="split-panels detail-panel">
          <section class="panel mini">
            <div class="section-title">
              <h3>业务快照</h3>
              <span class="meta">收款、渠道和回调关键字段</span>
            </div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>业务订单号：</strong>{{ formatValue(detail.businessOrderNo) }}</div>
              <div><strong>支付请求号：</strong>{{ formatValue(detail.paymentRequestNo) }}</div>
              <div><strong>支付类型：</strong>{{ formatValue(detail.paymentType) }}</div>
              <div><strong>支付网关：</strong>{{ formatValue(detail.paymentGateway) }}</div>
              <div><strong>支付渠道：</strong>{{ formatValue(detail.paymentChannel) }}</div>
              <div><strong>外部交易流水：</strong>{{ formatValue(detail.externalTransactionNo) }}</div>
              <div><strong>用户ID：</strong>{{ formatValue(detail.userId) }}</div>
              <div><strong>用户支付渠道标识：</strong>{{ formatValue(detail.userPaymentChannelId) }}</div>
              <div><strong>商品名称：</strong>{{ formatValue(detail.productName) }}</div>
              <div><strong>渠道返回码：</strong>{{ formatValue(detail.channelReturnCode) }}</div>
              <div><strong>回调地址：</strong>{{ formatValue(detail.notifyUrl) }}</div>
              <div><strong>支付成功事件主题：</strong>{{ formatValue(detail.callbackMqTopic) }}</div>
            </div>
          </section>

          <section class="panel mini">
            <div class="section-title">
              <h3>排障建议</h3>
              <span class="meta">按当前记录自动整理</span>
            </div>
            <div class="ops-card">
              <div class="ops-row"><span>来源列表</span><span>{{ recordTypeLabel }}</span></div>
              <div class="ops-row"><span>支付状态</span><span>{{ formatValue(detail.paymentStatus) }}</span></div>
              <div class="ops-row"><span>最近尝试</span><span>{{ formatValue(detail.latestAttemptStatus) }}</span></div>
            </div>
            <div class="suggestion-list">
              <div v-for="item in operationSuggestions" :key="item" class="timeline-item">{{ item }}</div>
            </div>
          </section>
        </div>

        <div class="detail-grid detail-grid-wide">
          <div><strong>支付金额：</strong>{{ formatValue(detail.paymentAmount) }}</div>
          <div><strong>支付场景：</strong>{{ formatValue(detail.payScene) }}</div>
          <div v-if="detail.prepayBindingIssue"><strong>预付归属：</strong>{{ detail.prepayBindingIssue === "PREPAY_BINDING_MISSING" ? "预付归属缺失，待人工核查" : "多预付冲突，待人工核查" }}</div>
          <div><strong>已退金额：</strong>{{ formatValue(detail.refundedAmount) }}</div>
          <div><strong>退款次数：</strong>{{ formatValue(detail.refundCount) }}</div>
          <div><strong>银行名：</strong>{{ formatValue(detail.bankName) }}</div>
          <div><strong>卡号：</strong>{{ formatValue(detail.cardNo) }}</div>
          <div><strong>收款账号：</strong>{{ formatValue(detail.receivingAccount) }}</div>
          <div><strong>返参类型：</strong>{{ formatValue(detail.returnParameterType) }}</div>
          <div><strong>参数值：</strong>{{ formatValue(detail.parameterValue) }}</div>
          <div><strong>有效期：</strong>{{ formatValue(detail.validityPeriod) }}</div>
          <div><strong>失效时间：</strong>{{ formatValue(detail.expireTime) }}</div>
          <div><strong>创建时间：</strong>{{ formatValue(detail.createdAt) }}</div>
          <div><strong>更新时间：</strong>{{ formatValue(detail.updatedAt) }}</div>
          <div><strong>支付成功时间：</strong>{{ formatValue(detail.paidAt) }}</div>
          <div><strong>最近尝试完成时间：</strong>{{ formatValue(detail.latestAttemptCompletedAt) }}</div>
          <div><strong>最近终端：</strong>{{ formatValue(detail.latestTerminal) }}</div>
          <div><strong>最近客户端 IP：</strong>{{ formatValue(detail.latestClientIp) }}</div>
          <div><strong>最近幂等键：</strong>{{ formatValue(detail.latestIdempotencyKey) }}</div>
        </div>

        <div class="payload-grid">
          <div>
            <strong>最近一次支付请求报文</strong>
            <pre>{{ formatTextBlock(detail.latestRequestPayload) }}</pre>
          </div>
          <div>
            <strong>最近一次支付响应报文</strong>
            <pre>{{ formatTextBlock(detail.latestResponsePayload) }}</pre>
          </div>
        </div>

        <div class="split-panels detail-panel">
          <section class="panel mini">
            <h4>路由轨迹</h4>
            <div v-for="item in detail.routeLogs || []" :key="item" class="timeline-item">{{ item }}</div>
            <div v-if="!(detail.routeLogs || []).length" class="state-box">暂无路由轨迹</div>
          </section>
          <section class="panel mini">
            <h4>回调轨迹</h4>
            <div v-for="item in detail.notifyLogs || []" :key="item" class="timeline-item">{{ item }}</div>
            <div v-if="!(detail.notifyLogs || []).length" class="state-box">暂无回调轨迹</div>
          </section>
        </div>

        <section class="panel mini detail-panel">
          <h4>事件轨迹</h4>
          <div v-for="item in detail.eventLogs || []" :key="item" class="timeline-item">{{ item }}</div>
          <div v-if="!(detail.eventLogs || []).length" class="state-box">暂无事件轨迹</div>
        </section>
      </template>
    </section>
  </div>
</template>

<style scoped>
.suggestion-list {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}
</style>
