<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentApi } from "../api/client";
import { paymentRecordDetailRoute as buildPaymentRecordDetailRoute } from "../router/detailRoutes";
import { buildAppWebCashierUrl } from "../router/externalUrls";
import { billsRoute, ordersRoute, paymentLogsRoute, paymentRequestsRoute } from "../router/listRoutes";
import { canClosePaymentLocally, canQueryPaymentChannel } from "../utils/paymentAction";

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

const paymentRecordDetailRoute = computed(() => {
  if (!detail.value?.paymentOrderId) {
    return "";
  }
  return buildPaymentRecordDetailRoute(detail.value.paymentOrderId);
});

const orderRoute = computed(() => {
  if (!detail.value?.orderNo) {
    return "";
  }
  return ordersRoute({ orderNo: detail.value.orderNo });
});

const billRoute = computed(() => {
  if (!detail.value?.billNo && !detail.value?.orderNo) {
    return "";
  }
  const billNo = detail.value?.billNo || "";
  const orderNo = detail.value?.orderNo || "";
  return billsRoute({ billNo, orderNo });
});

const cashierUrl = computed(() => {
  if (!detail.value?.prepayOrderNo) {
    return "";
  }
  return buildAppWebCashierUrl(detail.value.prepayOrderNo);
});

const metricCards = computed(() => {
  if (!detail.value) {
    return [];
  }
  return [
    {
      title: "支付金额",
      value: detail.value.amount || "-",
      hint: `支付方式 ${detail.value.paymentMethod || "-"}`
    },
    {
      title: "路由轨迹数",
      value: `${(detail.value.routeLogs || []).length}`,
      hint: "用于复盘路由选型和渠道命中"
    },
    {
      title: "回调轨迹数",
      value: `${(detail.value.notifyLogs || []).length}`,
      hint: "用于确认渠道通知是否已经收口"
    },
    {
      title: "事件轨迹数",
      value: `${(detail.value.eventLogs || []).length}`,
      hint: "用于确认下游事件是否完整发布"
    }
  ];
});

const operationSuggestions = computed(() => {
  if (!detail.value) {
    return [];
  }
  const suggestions = [];
  if (detail.value.status === "WAIT_CALLBACK") {
    suggestions.push("当前支付单等待回调，建议先主动查单；确认渠道已成功但未回调时，再进入异常中心排查。");
  }
  if (detail.value.status === "PAYING") {
    suggestions.push("当前支付单处理中，建议优先查看最近一次支付尝试和渠道响应报文，避免重复提交。");
  }
  if (detail.value.status === "CLOSED") {
    suggestions.push("当前支付单已关闭，如业务仍需付款，应从订单侧重新发起预付单，禁止直接恢复旧单。");
  }
  if (detail.value.status === "SUCCESS") {
    suggestions.push("当前支付单已成功，建议联查订单、账单、支付记录和下游事件是否全部收口。");
  }
  if (!(detail.value.notifyLogs || []).length) {
    suggestions.push("当前没有回调轨迹，需关注渠道回调地址、验签和消息落库情况。");
  }
  if (!suggestions.length) {
    suggestions.push("当前状态可结合路由、回调和事件轨迹继续完成支付链路复盘。");
  }
  return suggestions;
});

async function loadDetail(afterAction = false, paymentOrderId = currentPaymentOrderId.value) {
  const requestId = ++latestLoadRequestId;
  isLoading.value = true;
  if (afterAction) {
    actionRefreshErrorMessage.value = "";
  } else {
    loadErrorMessage.value = "";
  }
  try {
    const paymentDetail = await paymentApi.getDetail(paymentOrderId);
    if (requestId !== latestLoadRequestId || paymentOrderId !== currentPaymentOrderId.value) {
      return;
    }
    if (!paymentDetail || paymentDetail.paymentOrderId !== paymentOrderId) {
      throw new Error("支付单详情响应为空或与当前路由不一致");
    }
    detail.value = paymentDetail;
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

function isActionRunning(actionName) {
  return activeAction.value === actionName;
}

function canRunClose() {
  return canClosePaymentLocally(
    detail.value?.status,
    detail.value?.channelTransactionNo,
    detail.value?.latestAttemptStatus
  );
}

function canRunQuery() {
  return canQueryPaymentChannel(detail.value?.status, detail.value?.latestAttemptStatus);
}

function openRelatedRoute(targetRoute) {
  if (!canUseSnapshot.value || !targetRoute) {
    return;
  }
  router.push(targetRoute);
}

function openCashier() {
  if (!canUseSnapshot.value || !cashierUrl.value) {
    return;
  }
  window.open(cashierUrl.value, "_blank", "noopener,noreferrer");
}

async function handleQuery() {
  if (!canUseSnapshot.value || !canRunQuery()) {
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
    const paymentDetail = await paymentApi.query(paymentOrderId, operator);
    if (paymentOrderId !== currentPaymentOrderId.value) {
      return;
    }
    detail.value = paymentDetail;
    hasLoadedData.value = true;
    loadErrorMessage.value = "";
    actionRefreshErrorMessage.value = "";
    actionResultMessage.value = `支付单 ${paymentOrderId} 当前状态为 ${paymentDetail.status}。`;
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

async function handleClose() {
  if (!canUseSnapshot.value || !canRunClose()) {
    actionErrorMessage.value = "当前支付单不满足本地关闭条件。";
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
  activeAction.value = "close";
  actionErrorMessage.value = "";
  actionResultMessage.value = "";
  try {
    const paymentDetail = await paymentApi.close(paymentOrderId, operator);
    if (paymentOrderId !== currentPaymentOrderId.value) {
      return;
    }
    detail.value = paymentDetail;
    hasLoadedData.value = true;
    loadErrorMessage.value = "";
    actionRefreshErrorMessage.value = "";
    actionResultMessage.value = paymentDetail.status === "CLOSED"
      ? `支付单 ${paymentOrderId} 已关闭。`
      : `支付单 ${paymentOrderId} 当前状态为 ${paymentDetail.status}，未执行关闭。`;
    await loadDetail(true, paymentOrderId);
  } catch (error) {
    if (paymentOrderId !== currentPaymentOrderId.value) {
      return;
    }
    actionErrorMessage.value = `关闭支付单失败：${error.message}`;
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
        <h2>支付单详情</h2>
        <p>查看支付主链路、路由轨迹、回调轨迹和事件轨迹</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" @click="router.push('/payments')">返回列表</button>
        <button class="button secondary" :disabled="!canUseSnapshot" @click="openRelatedRoute(paymentRequestsRoute({ paymentOrderId: detail?.paymentOrderId }))">
          查看支付请求
        </button>
        <button
          class="button secondary"
          :disabled="!canUseSnapshot || !paymentRecordDetailRoute"
          @click="openRelatedRoute(paymentRecordDetailRoute)"
        >
          查看支付记录
        </button>
        <button class="button secondary" :disabled="!canUseSnapshot" @click="openRelatedRoute(paymentLogsRoute({ paymentOrderId: detail?.paymentOrderId }))">
          查看处理日志
        </button>
        <button class="button secondary" :disabled="isLoading || Boolean(activeAction)" @click="loadDetail()">刷新</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !canRunQuery()" @click="handleQuery">
          {{ isActionRunning("query") ? "查单中..." : "主动查单" }}
        </button>
        <button class="button primary" :disabled="!canUseSnapshot || !canRunClose()" @click="handleClose">
          {{ isActionRunning("close") ? "关闭中..." : "关闭支付单" }}
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
        {{ hasLoadedData ? "支付单详情刷新失败，当前展示上次成功快照：" : "支付单详情加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="actionRefreshErrorMessage" class="error-banner">
        人工动作结果已更新当前支付单，但详情刷新失败，当前展示动作响应快照：{{ actionRefreshErrorMessage }}
      </div>
      <div v-if="actionErrorMessage" class="error-banner">
        {{ actionErrorMessage }}
      </div>
      <div v-if="actionResultMessage" class="state-banner">
        {{ actionResultMessage }}
      </div>
      <div v-if="isLoading" class="state-box">支付单详情加载中...</div>
      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的支付单详情快照</div>
      <template v-else-if="detail">
        <div class="detail-card-grid">
          <div class="detail-card">
            <div class="detail-label">支付单号</div>
            <div class="detail-value">{{ detail.paymentOrderId }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">跨系统链路 ID</div>
            <div class="detail-value">{{ detail.businessChainId || "-" }}</div>
            <div class="meta">用于对账台查询支付、清分、结算和账务事实</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">预付单号</div>
            <div class="detail-value">{{ detail.prepayOrderNo || "-" }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">账单号</div>
            <div class="detail-value">{{ detail.billNo || "-" }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">当前状态</div>
            <div class="detail-value"><span :class="['badge', detail.statusType]">{{ detail.status }}</span></div>
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
              <h3>支付动作说明</h3>
              <span class="meta">根据支付单当前状态控制动作</span>
            </div>
            <div class="ops-card">
              <div class="ops-row"><span>主动查单</span><span>{{ canRunQuery() ? "当前可执行" : "仅已提交渠道或终态支付单可执行" }}</span></div>
              <div class="ops-row"><span>渠道回调</span><span>仅接收真实渠道签名通知，页面不提供人工收口动作</span></div>
              <div class="ops-row"><span>关闭支付单</span><span>{{ canRunClose() ? "当前可执行" : "已提交渠道或已终态支付单不可本地关闭" }}</span></div>
            </div>
          </section>

          <section class="panel mini">
            <div class="section-title">
              <h3>处理建议</h3>
              <span class="meta">按当前状态和轨迹自动整理</span>
            </div>
            <div class="suggestion-list">
              <div v-for="item in operationSuggestions" :key="item" class="timeline-item">{{ item }}</div>
            </div>
          </section>
        </div>

        <div class="detail-grid detail-grid-wide">
          <div><strong>订单号：</strong>{{ detail.orderNo }}</div>
          <div><strong>客户：</strong>{{ detail.customerName }}</div>
          <div><strong>账单号：</strong>{{ detail.billNo || "-" }}</div>
          <div><strong>金额：</strong>{{ detail.amount }}</div>
          <div><strong>支付方式：</strong>{{ detail.paymentMethod }}</div>
          <div><strong>支付渠道：</strong>{{ detail.channel }}</div>
          <div><strong>渠道交易号：</strong>{{ detail.channelTransactionNo || "-" }}</div>
          <div><strong>配置快照版本：</strong>{{ detail.opsConfigSnapshotVersion || "—" }}</div>
          <div><strong>查单来源：</strong>{{ detail.querySource || "-" }}</div>
          <div><strong>渠道返回码：</strong>{{ detail.channelReturnCode || "-" }}</div>
          <div><strong>标准错误码：</strong>{{ detail.standardizedCode || "-" }}</div>
          <div><strong>映射版本：</strong>{{ detail.returnCodeMappingVersion || "-" }}</div>
          <div><strong>映射命中：</strong>{{ detail.returnCodeMappingMatched == null ? "-" : (detail.returnCodeMappingMatched ? "是" : "否") }}</div>
          <div><strong>是否可重试：</strong>{{ detail.retryable || "-" }}</div>
          <div><strong>需要人工介入：</strong>{{ detail.manualInterventionRequired || "-" }}</div>
          <div><strong>创建时间：</strong>{{ detail.createdAt }}</div>
        </div>

        <section v-if="detail.standardizedMessage || detail.handlingSuggestion" class="panel mini">
          <h4>渠道返回码标准化</h4>
          <div class="detail-grid detail-grid-wide">
            <div><strong>标准文案：</strong>{{ detail.standardizedMessage || "-" }}</div>
            <div><strong>处理建议：</strong>{{ detail.handlingSuggestion || "-" }}</div>
          </div>
        </section>

        <section class="panel mini">
          <h4>最近支付尝试</h4>
          <div class="detail-grid detail-grid-wide">
            <div>
              <strong>尝试状态：</strong>
              <span :class="['badge', detail.latestAttemptStatusType || 'info']">{{ detail.latestAttemptStatus || "-" }}</span>
            </div>
            <div><strong>发起终端：</strong>{{ detail.latestTerminal || "-" }}</div>
            <div><strong>客户端 IP：</strong>{{ detail.latestClientIp || "-" }}</div>
            <div><strong>配置快照版本：</strong>{{ detail.opsConfigSnapshotVersion || "—" }}</div>
            <div><strong>幂等键：</strong>{{ detail.latestIdempotencyKey || "-" }}</div>
          </div>
          <div class="payload-grid">
            <div>
              <strong>最近一次请求报文</strong>
              <pre>{{ detail.latestRequestPayload || "暂无请求报文" }}</pre>
            </div>
            <div>
              <strong>最近一次响应报文</strong>
              <pre>{{ detail.latestResponsePayload || "暂无响应报文" }}</pre>
            </div>
          </div>
        </section>

        <section class="panel mini">
          <h4>联查入口</h4>
          <div class="list-actions">
            <button class="link-button" :disabled="!canUseSnapshot || !orderRoute" @click="openRelatedRoute(orderRoute)">回到订单中心</button>
            <button class="link-button" :disabled="!canUseSnapshot || !billRoute" @click="openRelatedRoute(billRoute)">查看账单中心</button>
            <button class="link-button" :disabled="!canUseSnapshot || !paymentRecordDetailRoute" @click="openRelatedRoute(paymentRecordDetailRoute)">查看支付记录详情</button>
            <button class="link-button" :disabled="!canUseSnapshot || !cashierUrl" @click="openCashier">打开当前收银台</button>
          </div>
        </section>

        <div class="split-panels">
          <section class="panel mini">
            <h4>路由记录</h4>
            <div v-for="item in detail.routeLogs || []" :key="item" class="timeline-item">{{ item }}</div>
            <div v-if="!(detail.routeLogs || []).length" class="state-box">当前暂无路由记录</div>
          </section>
          <section class="panel mini">
            <h4>回调日志</h4>
            <div v-for="item in detail.notifyLogs || []" :key="item" class="timeline-item">{{ item }}</div>
            <div v-if="!(detail.notifyLogs || []).length" class="state-box">当前暂无回调日志</div>
          </section>
        </div>
        <section class="panel mini">
          <h4>事件轨迹</h4>
          <div v-for="item in detail.eventLogs || []" :key="item" class="timeline-item">{{ item }}</div>
          <div v-if="!(detail.eventLogs || []).length" class="state-box">当前暂无事件轨迹</div>
        </section>
      </template>
    </section>
  </div>
</template>

<style scoped>
.suggestion-list {
  display: grid;
  gap: 10px;
}
</style>
