<script setup>
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { matchesPaymentRequestDetailSnapshot, paymentRequestApi } from "../api/client";
import {
  billDetailRoute,
  cashierSessionDetailRoute,
  orderDetailRoute,
  paymentDetailRoute,
  paymentRecordDetailRoute
} from "../router/detailRoutes";
import { buildAppWebCashierUrl } from "../router/externalUrls";
import {
  paymentFlowsRoute,
  paymentLogsRoute,
  paymentRequestsRoute,
  paymentRoutesRoute
} from "../router/listRoutes";

const route = useRoute();
const router = useRouter();
const detail = ref(null);
const isLoading = ref(true);
const hasLoadedData = ref(false);
const loadErrorMessage = ref("");
let latestLoadRequestId = 0;

const currentRequestNo = computed(() => typeof route.params.requestNo === "string"
  ? route.params.requestNo
  : "");
const isSnapshotStale = computed(() => Boolean(hasLoadedData.value && loadErrorMessage.value));
const canUseSnapshot = computed(() => Boolean(
  hasLoadedData.value
  && detail.value?.requestNo === currentRequestNo.value
  && !isLoading.value
  && !isSnapshotStale.value
));
const relatedRoutes = computed(() => ({
  order: detail.value?.standardOrderNo ? orderDetailRoute(detail.value.standardOrderNo) : null,
  bill: detail.value?.billNo ? billDetailRoute(detail.value.billNo) : null,
  payment: detail.value?.paymentOrderId && !detail.value.successFactIssue
    ? paymentDetailRoute(detail.value.paymentOrderId) : null,
  paymentRecord: detail.value?.paymentOrderId
    ? paymentRecordDetailRoute(detail.value.paymentOrderId)
    : null,
  cashierSession: detail.value?.prepayOrderNo
    ? cashierSessionDetailRoute(detail.value.prepayOrderNo)
    : null,
  paymentFlow: detail.value?.paymentOrderId
    ? paymentFlowsRoute({ paymentOrderId: detail.value.paymentOrderId })
    : null,
  paymentRoute: detail.value?.paymentOrderId
    ? paymentRoutesRoute({ paymentOrderId: detail.value.paymentOrderId })
    : null,
  paymentLog: detail.value?.paymentOrderId
    ? paymentLogsRoute({ paymentOrderId: detail.value.paymentOrderId })
    : null
}));
const cashierUrl = computed(() => detail.value?.prepayOrderNo
  ? buildAppWebCashierUrl(detail.value.prepayOrderNo, undefined, detail.value.payScene)
  : "");
const metricCards = computed(() => detail.value ? [
  { title: "请求状态", value: detail.value.requestStatus || "-", hint: detail.value.requestNo },
  { title: "支付渠道", value: detail.value.channelCode || "-", hint: detail.value.paymentMethod || "支付方式未记录" },
  { title: "路由结果", value: detail.value.routeResult || "未关联", hint: detail.value.routeNo || "本次请求无路由记录" },
  { title: "来源终端", value: detail.value.terminal || "-", hint: detail.value.sourceAppId || "来源应用未记录" }
] : []);

async function loadDetail(requestNo = currentRequestNo.value) {
  const requestId = ++latestLoadRequestId;
  const expectedSnapshot = requestNo === currentRequestNo.value && detail.value
    ? {
      requestNo: detail.value.requestNo,
      paymentOrderId: detail.value.paymentOrderId,
      businessChainId: detail.value.businessChainId,
      prepayOrderNo: detail.value.prepayOrderNo,
      payScene: detail.value.payScene,
      orderNo: detail.value.orderNo,
      standardOrderNo: detail.value.standardOrderNo,
      billNo: detail.value.billNo,
      paymentMethod: detail.value.paymentMethod,
      routeNo: detail.value.routeNo
    }
    : null;
  isLoading.value = true;
  loadErrorMessage.value = "";
  try {
    const requestDetail = await paymentRequestApi.getDetail(requestNo);
    if (requestId !== latestLoadRequestId || requestNo !== currentRequestNo.value) {
      return;
    }
    if (!requestDetail || requestDetail.requestNo !== requestNo) {
      throw new Error("支付请求详情响应为空或与当前路由不一致");
    }
    if (expectedSnapshot && !matchesPaymentRequestDetailSnapshot(expectedSnapshot, requestDetail)) {
      throw new Error("支付请求详情响应为空或与当前支付关联快照不一致");
    }
    detail.value = requestDetail;
    hasLoadedData.value = true;
  } catch (error) {
    if (requestId !== latestLoadRequestId || requestNo !== currentRequestNo.value) {
      return;
    }
    loadErrorMessage.value = error.message;
  } finally {
    if (requestId === latestLoadRequestId) {
      isLoading.value = false;
    }
  }
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

function formatValue(value) {
  return value == null || value === "" ? "-" : value;
}

function formatPayload(value) {
  return value || "暂无报文数据";
}

watch(currentRequestNo, (requestNo) => {
  latestLoadRequestId += 1;
  detail.value = null;
  hasLoadedData.value = false;
  loadErrorMessage.value = "";
  loadDetail(requestNo);
}, { immediate: true });
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付请求详情</h2>
        <p>按唯一支付尝试核对请求报文、响应报文、渠道路由与业务关联</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" @click="router.push(paymentRequestsRoute({ requestNo: currentRequestNo }))">返回请求列表</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.order" @click="openRelatedRoute(relatedRoutes.order)">标准订单</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.bill" @click="openRelatedRoute(relatedRoutes.bill)">账单</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.payment" @click="openRelatedRoute(relatedRoutes.payment)">支付单</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.paymentRecord" @click="openRelatedRoute(relatedRoutes.paymentRecord)">支付记录</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.cashierSession" @click="openRelatedRoute(relatedRoutes.cashierSession)">收银台会话</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.paymentFlow" @click="openRelatedRoute(relatedRoutes.paymentFlow)">支付流水</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.paymentRoute" @click="openRelatedRoute(relatedRoutes.paymentRoute)">路由结果</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.paymentLog" @click="openRelatedRoute(relatedRoutes.paymentLog)">处理日志</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !cashierUrl" @click="openCashier">用户收银台</button>
        <button class="button primary" :disabled="isLoading" @click="loadDetail()">{{ isLoading ? "刷新中..." : "刷新" }}</button>
      </div>
    </div>

    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">
        {{ hasLoadedData ? "支付请求详情刷新失败，当前展示上次成功快照：" : "支付请求详情加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="canUseSnapshot && detail?.successFactIssue" class="error-banner">
        所属支付单成功事实异常（{{ detail.successFactIssue }}）；保留请求详情，支付单详情不可用。
      </div>
      <div v-if="isLoading" class="state-box">支付请求详情加载中...</div>
      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的支付请求详情快照</div>
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
            <div class="section-title">
              <h3>请求事实</h3>
              <span class="meta">本次支付尝试的不可混淆快照</span>
            </div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>请求编号：</strong>{{ formatValue(detail.requestNo) }}</div>
              <div><strong>预付单号：</strong>{{ formatValue(detail.prepayOrderNo) }}</div>
              <div><strong>支付场景：</strong>{{ formatValue(detail.payScene) }}</div>
              <div><strong>支付方式：</strong>{{ formatValue(detail.paymentMethod) }}</div>
              <div><strong>来源应用：</strong>{{ formatValue(detail.sourceAppId) }}</div>
              <div><strong>渠道编码：</strong>{{ formatValue(detail.channelCode) }}</div>
              <div><strong>来源终端：</strong>{{ formatValue(detail.terminal) }}</div>
              <div><strong>客户端 IP：</strong>{{ formatValue(detail.clientIp) }}</div>
              <div><strong>幂等键：</strong><span class="mono-text">{{ formatValue(detail.idempotencyKey) }}</span></div>
              <div><strong>请求状态：</strong><span :class="['badge', detail.requestStatusType || 'info']">{{ formatValue(detail.requestStatus) }}</span> <span v-if="detail.requestStatus === '渠道提交中'" class="badge warn">渠道受理未确认</span> <span v-if="detail.requestStatus === '渠道流水归属冲突，待人工核查'" class="badge danger">渠道流水归属冲突</span></div>
              <div><strong>运营配置快照：</strong>{{ formatValue(detail.opsConfigSnapshotVersion) }}</div>
              <div><strong>创建时间：</strong>{{ formatValue(detail.createdAt) }}</div>
              <div><strong>完成时间：</strong>{{ formatValue(detail.completedAt) }}</div>
            </div>
          </section>

          <section class="panel mini">
            <div class="section-title">
              <h3>关联与路由</h3>
              <span class="meta">路由仅按当前请求号精确关联</span>
            </div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>业务订单号：</strong>{{ formatValue(detail.orderNo) }}</div>
              <div><strong>标准订单号：</strong>{{ formatValue(detail.standardOrderNo) }}</div>
              <div><strong>账单号：</strong>{{ formatValue(detail.billNo) }}</div>
              <div><strong>支付单号：</strong>{{ formatValue(detail.paymentOrderId) }}</div>
              <div><strong>业务链路：</strong>{{ formatValue(detail.businessChainId) }}</div>
              <div><strong>路由编号：</strong>{{ formatValue(detail.routeNo) }}</div>
              <div><strong>路由规则：</strong>{{ formatValue(detail.routeRule) }}</div>
              <div><strong>路由结果：</strong>{{ formatValue(detail.routeResult) }}</div>
              <div><strong>路由配置快照：</strong>{{ formatValue(detail.routeOpsConfigSnapshotVersion) }}</div>
            </div>
            <div class="ops-card detail-panel">
              <div v-if="detail.requestStatus === '渠道提交中'" class="ops-row"><span>受理核查</span><span>先核对提交日志，等待状态推进；勿主动查单或重复提交。</span></div>
              <div v-else-if="detail.requestStatus === '渠道流水归属冲突，待人工核查'" class="ops-row"><span>人工核查</span><span>核对渠道原始事实与流水归属；勿主动查单、重复提交或绑定争议流水。</span></div>
              <div class="ops-row"><span>标准订单</span><span>{{ detail.standardOrderNo ? "归属已确认" : "归属未确认，暂不可联查" }}</span></div>
              <div class="ops-row"><span>请求路由</span><span>{{ detail.routeNo ? "已精确关联" : "本次请求无路由事实" }}</span></div>
              <div class="ops-row"><span>导航状态</span><span>{{ canUseSnapshot ? "当前快照有效" : "当前快照不可用于联查" }}</span></div>
            </div>
          </section>
        </div>

        <div class="payload-grid detail-panel">
          <div>
            <strong>请求报文</strong>
            <pre>{{ formatPayload(detail.requestPayload) }}</pre>
          </div>
          <div>
            <strong>响应报文</strong>
            <pre>{{ formatPayload(detail.responsePayload) }}</pre>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.mono-text {
  font-family: "SFMono-Regular", Consolas, monospace;
  overflow-wrap: anywhere;
}
</style>
