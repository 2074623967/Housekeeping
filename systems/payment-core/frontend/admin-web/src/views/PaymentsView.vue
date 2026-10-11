<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentApi } from "../api/client";
import { paymentDetailRoute } from "../router/detailRoutes";
import { paymentLogsRoute } from "../router/listRoutes";
import { normalizePaymentAmountInput, sumPaymentAmounts } from "../utils/paymentAmount";
import { canClosePaymentLocally, canQueryPaymentChannel, isChannelSubmissionUnaccepted } from "../utils/paymentAction";

const route = useRoute();
const router = useRouter();
const items = ref([]);
const selectedItem = ref(null);
const isLoading = ref(true);
const hasLoadedData = ref(false);
const loadErrorMessage = ref("");
const actionRefreshErrorMessage = ref("");
const actionErrorMessage = ref("");
const actionResultMessage = ref("");
const activePaymentOrderId = ref("");
const activeAction = ref("");
const operationOperator = ref("");
const total = ref(0);
const pageNo = ref(1);
const loadedPageNo = ref(1);
const loadedQuery = ref(null);
const pageSize = 20;
let latestLoadRequestId = 0;

function queryString(value, fallback) {
  return typeof value === "string" && value ? value : fallback;
}

function filtersFromRoute() {
  return {
    paymentOrderId: queryString(route.query.paymentOrderId, ""),
    orderNo: queryString(route.query.orderNo, ""),
    channelCode: queryString(route.query.channelCode, ""),
    paymentMethod: queryString(route.query.paymentMethod, "全部"),
    status: queryString(route.query.status, "全部")
  };
}

const filters = ref(filtersFromRoute());

const hasUnverifiablePaymentAmount = computed(() => items.value.some((item) => item.successFactIssue
  && !normalizePaymentAmountInput(String(item.amount ?? "").replace(/^¥\s*/, "").replace(/,/g, ""))));

const metrics = computed(() => ({
  total: total.value,
  successTotal: items.value.filter((item) => item.status === "SUCCESS"
    && !item.successFactIssue && !item.prepayBindingIssue).length,
  callbackPendingTotal: items.value.filter((item) => item.status === "WAIT_CALLBACK"
    && !isChannelSubmissionUnaccepted(item.status, item.latestAttemptStatus, item.channelTransactionNo)).length,
  amountTotal: hasUnverifiablePaymentAmount.value
    ? "—" : sumPaymentAmounts(items.value.map((item) => item.amount))
}));

const isSnapshotStale = computed(() => Boolean(
  hasLoadedData.value && (loadErrorMessage.value || actionRefreshErrorMessage.value)
));
const isActionRunningOnPage = computed(() => Boolean(activePaymentOrderId.value));
const canUseSnapshot = computed(() => (
  hasLoadedData.value && !isLoading.value && !isSnapshotStale.value && !isActionRunningOnPage.value
));
const canExport = computed(() => canUseSnapshot.value && total.value > 0);
function buildQuery() {
  return {
    paymentOrderId: filters.value.paymentOrderId,
    orderNo: filters.value.orderNo,
    channelCode: filters.value.channelCode,
    paymentMethod: filters.value.paymentMethod,
    status: filters.value.status,
    pageNo: pageNo.value,
    pageSize
  };
}

function resetFilters() {
  if (isLoading.value || isActionRunningOnPage.value) {
    return;
  }
  filters.value = {
    paymentOrderId: "",
    orderNo: "",
    channelCode: "",
    paymentMethod: "全部",
    status: "全部"
  };
  pageNo.value = 1;
  refreshList();
}

async function refreshList(
  preferredPaymentOrderId = selectedItem.value?.paymentOrderId,
  afterAction = false,
  query = buildQuery()
) {
  const requestId = ++latestLoadRequestId;
  isLoading.value = true;
  if (afterAction) {
    actionRefreshErrorMessage.value = "";
  } else {
    loadErrorMessage.value = "";
  }
  try {
    const result = await paymentApi.getList(query);
    if (requestId !== latestLoadRequestId) {
      return;
    }
    items.value = result.items;
    total.value = result.total;
    selectedItem.value = result.items.find((item) => item.paymentOrderId === preferredPaymentOrderId)
      || result.items[0]
      || null;
    loadedPageNo.value = query.pageNo;
    loadedQuery.value = { ...query };
    hasLoadedData.value = true;
    loadErrorMessage.value = "";
    actionRefreshErrorMessage.value = "";
    if (!afterAction) {
      actionErrorMessage.value = "";
      actionResultMessage.value = "";
    }
  } catch (error) {
    if (requestId !== latestLoadRequestId) {
      return;
    }
    if (afterAction) {
      actionRefreshErrorMessage.value = error.message;
    } else {
      loadErrorMessage.value = error.message;
      actionRefreshErrorMessage.value = "";
    }
    if (hasLoadedData.value) {
      pageNo.value = loadedPageNo.value;
    }
  } finally {
    if (requestId === latestLoadRequestId) {
      isLoading.value = false;
    }
  }
}

function applyFilters() {
  if (isLoading.value || isActionRunningOnPage.value) {
    return;
  }
  pageNo.value = 1;
  refreshList();
}

function refreshPayments() {
  if (isLoading.value || isActionRunningOnPage.value) {
    return;
  }
  const query = loadedQuery.value
    ? { ...loadedQuery.value, pageNo: loadedPageNo.value }
    : buildQuery();
  refreshList(selectedItem.value?.paymentOrderId, false, query);
}

function goToPage(nextPage) {
  if (!canUseSnapshot.value || !loadedQuery.value
      || nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  refreshList(selectedItem.value?.paymentOrderId, false, { ...loadedQuery.value, pageNo: nextPage });
}

function openDetail(paymentOrderId) {
  const paymentItem = items.value.find((item) => item.paymentOrderId === paymentOrderId);
  if (!canUseSnapshot.value || !paymentItem || paymentItem.successFactIssue || paymentItem.prepayBindingIssue) {
    return;
  }
  router.push(paymentDetailRoute(paymentOrderId));
}

function openLogs(paymentOrderId) {
  if (!canUseSnapshot.value) {
    return;
  }
  router.push(paymentLogsRoute({ paymentOrderId }));
}

function pickItem(item) {
  if (!canUseSnapshot.value) {
    return;
  }
  selectedItem.value = item;
}

function isActionRunning(paymentOrderId, actionName) {
  return activePaymentOrderId.value === paymentOrderId && activeAction.value === actionName;
}

function canRunClose(item) {
  return !item.prepayBindingIssue
    && canClosePaymentLocally(item.status, item.channelTransactionNo, item.latestAttemptStatus);
}

function canRunQuery(item) {
  return !item.successFactIssue && !item.prepayBindingIssue && canQueryPaymentChannel(
    item.status, item.latestAttemptStatus, item.latestAttemptCompletedAt, item.channelTransactionNo,
    item.channel
  );
}

function updatePaymentSnapshot(paymentDetail) {
  const updatedItem = {
    paymentOrderId: paymentDetail.paymentOrderId,
    businessChainId: paymentDetail.businessChainId,
    orderNo: paymentDetail.orderNo,
    prepayOrderNo: paymentDetail.prepayOrderNo,
    payScene: paymentDetail.payScene,
    customerName: paymentDetail.customerName,
    amount: paymentDetail.amount,
    paymentMethod: paymentDetail.paymentMethod,
    channel: paymentDetail.channel,
    channelTransactionNo: paymentDetail.channelTransactionNo,
    latestAttemptStatus: paymentDetail.latestAttemptStatus,
    latestAttemptStatusType: paymentDetail.latestAttemptStatusType,
    latestAttemptCompletedAt: paymentDetail.latestAttemptCompletedAt,
    status: paymentDetail.status,
    statusType: paymentDetail.statusType,
    successFactIssue: null,
    prepayBindingIssue: null,
    createdAt: paymentDetail.createdAt
  };
  items.value = items.value.map((item) => item.paymentOrderId === paymentDetail.paymentOrderId
    ? updatedItem
    : item);
  selectedItem.value = updatedItem;
}

async function handleQuery(paymentOrderId) {
  const paymentItem = items.value.find((item) => item.paymentOrderId === paymentOrderId);
  if (!canUseSnapshot.value || !loadedQuery.value || !paymentItem || !canRunQuery(paymentItem)) {
    actionErrorMessage.value = `支付单 ${paymentOrderId} 没有可供渠道查单的提交事实。`;
    actionResultMessage.value = "";
    return;
  }
  const operator = operationOperator.value.trim();
  if (!operator) {
    actionErrorMessage.value = "请先输入操作人。";
    actionResultMessage.value = "";
    return;
  }
  const query = { ...loadedQuery.value };
  activePaymentOrderId.value = paymentOrderId;
  activeAction.value = "query";
  actionErrorMessage.value = "";
  actionResultMessage.value = "";
  try {
    const paymentDetail = await paymentApi.query(paymentOrderId, operator, {
      ...paymentItem,
      orderNo: paymentItem.orderNo,
      amount: paymentItem.amount,
      prepayOrderNo: paymentItem.prepayOrderNo
    });
    updatePaymentSnapshot(paymentDetail);
    actionResultMessage.value = `支付单 ${paymentOrderId} 已完成主动查单，当前状态为 ${paymentDetail.status}。`;
    actionRefreshErrorMessage.value = "";
    await refreshList(paymentOrderId, true, query);
  } catch (error) {
    actionErrorMessage.value = `支付单 ${paymentOrderId} 查单失败：${error.message}；请以刷新后的支付事实为准。`;
    await refreshList(paymentOrderId, true, query);
  } finally {
    activePaymentOrderId.value = "";
    activeAction.value = "";
  }
}

async function handleClose(paymentOrderId) {
  const paymentItem = items.value.find((item) => item.paymentOrderId === paymentOrderId);
  if (!canUseSnapshot.value || !loadedQuery.value || !paymentItem || !canRunClose(paymentItem)) {
    actionErrorMessage.value = `支付单 ${paymentOrderId} 当前不满足人工关闭条件。`;
    actionResultMessage.value = "";
    return;
  }
  const operator = operationOperator.value.trim();
  if (!operator) {
    actionErrorMessage.value = "请先输入操作人。";
    actionResultMessage.value = "";
    return;
  }
  const query = { ...loadedQuery.value };
  activePaymentOrderId.value = paymentOrderId;
  activeAction.value = "close";
  actionErrorMessage.value = "";
  actionResultMessage.value = "";
  try {
    const paymentDetail = await paymentApi.close(paymentOrderId, operator, {
      ...paymentItem,
      orderNo: paymentItem.orderNo,
      amount: paymentItem.amount,
      prepayOrderNo: paymentItem.prepayOrderNo
    });
    updatePaymentSnapshot(paymentDetail);
    actionResultMessage.value = paymentDetail.status === "CLOSED"
      ? `支付单 ${paymentOrderId} 已关闭。`
      : `支付单 ${paymentOrderId} 当前状态为 ${paymentDetail.status}，未执行关闭。`;
    actionRefreshErrorMessage.value = "";
    await refreshList(paymentOrderId, true, query);
  } catch (error) {
    actionErrorMessage.value = `支付单 ${paymentOrderId} 关闭失败：${error.message}；请以刷新后的支付事实为准。`;
    await refreshList(paymentOrderId, true, query);
  } finally {
    activePaymentOrderId.value = "";
    activeAction.value = "";
  }
}

function exportPayments() {
  if (!canExport.value || !loadedQuery.value) {
    return;
  }
  const exportUrl = paymentApi.buildExportUrl(loadedQuery.value);
  window.open(exportUrl, "_blank", "noopener,noreferrer");
}

onMounted(refreshList);

watch(
  () => [route.query.paymentOrderId, route.query.orderNo, route.query.channelCode,
    route.query.paymentMethod, route.query.status],
  () => {
    filters.value = filtersFromRoute();
    pageNo.value = 1;
    refreshList();
  }
);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付单管理</h2>
        <p>统一查看支付单、支付方式、渠道交易和回调收敛情况</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" :disabled="isLoading || isActionRunningOnPage" @click="refreshPayments">
          {{ isLoading ? "刷新中..." : "刷新" }}
        </button>
        <button class="button primary" :disabled="!canExport" @click="exportPayments">导出支付单</button>
      </div>
    </div>

    <section v-if="hasLoadedData" class="card-grid">
      <article class="card">
        <p class="card-title">支付单总数</p>
        <p class="card-value">{{ metrics.total }}</p>
      </article>
      <article class="card">
        <p class="card-title">本页成功事实无异常</p>
        <p class="card-value">{{ metrics.successTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">本页待回调</p>
        <p class="card-value">{{ metrics.callbackPendingTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">{{ hasUnverifiablePaymentAmount ? "本页实付金额（异常值不汇总）" : "本页实付金额" }}</p>
        <p class="card-value">{{ metrics.amountTotal }}</p>
      </article>
    </section>

    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">
        {{ hasLoadedData ? "支付单数据刷新失败，当前展示上次成功快照：" : "支付单数据加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="actionRefreshErrorMessage" class="error-banner">
        支付动作后列表刷新失败，当前状态未经确认，页面仍展示上次成功快照：{{ actionRefreshErrorMessage }}
      </div>
      <div v-if="actionErrorMessage" class="error-banner">
        {{ actionErrorMessage }}
      </div>
      <div v-if="actionResultMessage" class="state-banner">
        {{ actionResultMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>支付单号</label>
          <input v-model="filters.paymentOrderId" :disabled="isLoading || isActionRunningOnPage" placeholder="请输入支付单号" />
        </div>
        <div class="field">
          <label>订单号</label>
          <input v-model="filters.orderNo" :disabled="isLoading || isActionRunningOnPage" placeholder="请输入订单号" />
        </div>
        <div class="field">
          <label>渠道编码</label>
          <input v-model="filters.channelCode" :disabled="isLoading || isActionRunningOnPage" maxlength="64" placeholder="请输入渠道编码" />
        </div>
        <div class="field">
          <label>支付方式</label>
          <select v-model="filters.paymentMethod" :disabled="isLoading || isActionRunningOnPage">
            <option>全部</option>
            <option>微信</option>
            <option>支付宝</option>
            <option>银行转账</option>
            <option>待选渠道</option>
          </select>
        </div>
        <div class="field">
          <label>支付状态</label>
          <select v-model="filters.status" :disabled="isLoading || isActionRunningOnPage">
            <option>全部</option>
            <option>SUCCESS</option>
            <option>WAIT_CALLBACK</option>
            <option>FAIL</option>
            <option>CLOSED</option>
            <option>PREPAY_CREATED</option>
            <option value="DAY_END_PENDING">日终待收口</option>
            <option value="SUCCESS_TIME_MISSING">成功时间缺失</option>
            <option value="SUCCESS_FACT_MISMATCH">成功事实不一致</option>
            <option>RISK_REVIEW</option>
            <option>RISK_BLOCKED</option>
            <option>RISK_REJECTED</option>
          </select>
        </div>
        <div class="field">
          <label>操作人</label>
          <input v-model.trim="operationOperator" :disabled="isLoading || isActionRunningOnPage" maxlength="64" placeholder="请输入操作人" />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" :disabled="isLoading || isActionRunningOnPage" @click="applyFilters">
            {{ isLoading ? "查询中..." : "查询" }}
          </button>
          <button class="button secondary" :disabled="isLoading || isActionRunningOnPage" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">支付单数据加载中...</div>

      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的支付单快照</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的支付单数据</div>

      <div v-else class="detail-layout">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>支付单号</th>
                <th>业务链路</th>
                <th>支付场景</th>
                <th>订单号</th>
                <th>用户</th>
                <th>实付金额</th>
                <th>支付方式</th>
                <th>支付渠道</th>
                <th>渠道交易号</th>
                <th>支付状态</th>
                <th>最近尝试</th>
                <th>最近尝试完成时间</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.paymentOrderId">
                <td>{{ item.paymentOrderId }}</td>
                <td>{{ item.businessChainId }}</td>
                <td>{{ item.payScene || "—" }} <span v-if="item.prepayBindingIssue" class="badge warn">{{ item.prepayBindingIssue === "PREPAY_BINDING_MISSING" ? "预付缺失待核查" : "多预付待核查" }}</span></td>
                <td>{{ item.orderNo }}</td>
                <td>{{ item.customerName }}</td>
                <td>{{ item.amount }}</td>
                <td>{{ item.paymentMethod }}</td>
                <td>{{ item.channel }}</td>
                <td>{{ item.channelTransactionNo }}</td>
                <td><span :class="['badge', item.successFactIssue ? 'danger' : item.statusType]">{{ item.status }}</span> <span v-if="isChannelSubmissionUnaccepted(item.status, item.latestAttemptStatus, item.channelTransactionNo)" class="badge warn">渠道受理未确认</span> <span v-if="item.successFactIssue" class="badge danger">{{ item.successFactIssue === "SUCCESS_TIME_MISSING" ? "成功时间缺失" : "成功事实异常" }}</span></td>
                <td><span :class="['badge', item.latestAttemptStatusType || 'info']">{{ item.latestAttemptStatus || "—" }}</span></td>
                <td>{{ item.latestAttemptCompletedAt || "—" }}</td>
                <td>{{ item.createdAt }}</td>
                <td>
                  <div class="list-actions">
                    <button class="link-button" :disabled="!canUseSnapshot" @click="pickItem(item)">快照</button>
                    <button v-if="item.successFactIssue || item.prepayBindingIssue" class="link-button" :disabled="!canUseSnapshot" @click="openLogs(item.paymentOrderId)">处理日志</button>
                    <button v-else class="link-button" :disabled="!canUseSnapshot" @click="openDetail(item.paymentOrderId)">详情</button>
                    <button class="link-button" :disabled="!canUseSnapshot || !canRunQuery(item)" @click="handleQuery(item.paymentOrderId)">
                      {{ isActionRunning(item.paymentOrderId, "query") ? "查单中..." : "查单" }}
                    </button>
                    <button class="link-button" :disabled="!canUseSnapshot || !canRunClose(item)" @click="handleClose(item.paymentOrderId)">
                      {{ isActionRunning(item.paymentOrderId, "close") ? "关闭中..." : "关闭" }}
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <aside class="detail-side">
          <div v-if="selectedItem" class="detail-stack">
            <div class="section-title">
              <h3>支付单快照</h3>
              <span class="meta">{{ selectedItem.paymentOrderId }}</span>
            </div>
            <div class="detail-grid">
              <div class="detail-card"><span>业务链路</span><strong>{{ selectedItem.businessChainId }}</strong></div>
              <div class="detail-card"><span>支付场景</span><strong>{{ selectedItem.payScene || "—" }}</strong></div>
              <div v-if="selectedItem.prepayBindingIssue" class="detail-card"><span>预付归属</span><strong>{{ selectedItem.prepayBindingIssue === "PREPAY_BINDING_MISSING" ? "预付归属缺失，待人工核查" : "多预付冲突，待人工核查" }}</strong></div>
              <div class="detail-card"><span>订单号</span><strong>{{ selectedItem.orderNo }}</strong></div>
              <div class="detail-card"><span>客户名称</span><strong>{{ selectedItem.customerName }}</strong></div>
              <div class="detail-card"><span>实付金额</span><strong>{{ selectedItem.amount }}</strong></div>
              <div class="detail-card"><span>支付方式</span><strong>{{ selectedItem.paymentMethod }}</strong></div>
              <div class="detail-card"><span>支付渠道</span><strong>{{ selectedItem.channel }}</strong></div>
              <div class="detail-card"><span>渠道交易号</span><strong>{{ selectedItem.channelTransactionNo || "—" }}</strong></div>
              <div class="detail-card"><span>支付状态</span><strong>{{ selectedItem.status }}{{ selectedItem.successFactIssue ? " · " + (selectedItem.successFactIssue === "SUCCESS_TIME_MISSING" ? "成功时间缺失" : "成功事实异常") : "" }}</strong></div>
              <div class="detail-card"><span>最近尝试</span><strong>{{ selectedItem.latestAttemptStatus || "—" }}</strong></div>
              <div class="detail-card"><span>最近尝试完成时间</span><strong>{{ selectedItem.latestAttemptCompletedAt || "—" }}</strong></div>
              <div class="detail-card"><span>创建时间</span><strong>{{ selectedItem.createdAt }}</strong></div>
            </div>
            <div class="ops-card">
              <div class="ops-title">排障建议</div>
              <template v-if="selectedItem.successFactIssue || selectedItem.prepayBindingIssue">
                <div class="ops-row"><span>优先动作</span><span>保留当前异常快照，联查处理日志与最新支付尝试</span></div>
                <div class="ops-row"><span>详情门禁</span><span>成功事实异常或预付归属未确认时，不从列表推断可执行的支付动作</span></div>
              </template>
              <template v-else-if="isChannelSubmissionUnaccepted(selectedItem.status, selectedItem.latestAttemptStatus, selectedItem.channelTransactionNo)">
                <div class="ops-row"><span>优先动作</span><span>渠道受理尚未确认，请等待状态推进，刷新详情核对，不可主动查单或关单</span></div>
                <div class="ops-row"><span>状态说明</span><span>列表显示持久化状态，最近尝试仍在渠道提交中，不能按已受理待回调处理</span></div>
              </template>
              <template v-else>
                <div class="ops-row"><span>优先动作</span><span>已提交先查单，未提交可关闭</span></div>
                <div class="ops-row"><span>重点核对</span><span>支付状态、渠道流水号、回调是否收口</span></div>
                <div class="ops-row"><span>典型场景</span><span>待回调长时间未收口、重复支付保护、关闭单保护</span></div>
              </template>
            </div>
          </div>
          <div v-else class="state-box">选择左侧支付单后，可在这里查看支付单快照与排障建议。</div>
        </aside>
      </div>
      <div v-if="hasLoadedData && total > pageSize" class="pager">
        <span>共 {{ total }} 条支付单</span>
        <button class="button secondary" :disabled="!canUseSnapshot || pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
        <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button class="button secondary" :disabled="!canUseSnapshot || pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) 360px;
  gap: 16px;
}

.detail-side {
  display: grid;
  align-self: start;
  min-width: 0;
}

.detail-stack {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-card {
  padding: 14px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid #dbe3f0;
}

.detail-card span {
  display: block;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.detail-card strong {
  color: #0f172a;
  word-break: break-word;
}

.ops-card {
  padding: 16px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #dbe3f0;
}

.ops-title {
  margin-bottom: 10px;
  font-weight: 700;
}

.ops-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px dashed #dbe3f0;
}

.ops-row:last-child {
  border-bottom: 0;
}

@media (max-width: 1200px) {
  .detail-layout {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .ops-row {
    flex-direction: column;
  }
}
</style>
