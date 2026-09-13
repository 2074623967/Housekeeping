<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentApi } from "../api/client";
import { paymentDetailRoute } from "../router/detailRoutes";
import { sumPaymentAmounts } from "../utils/paymentAmount";
import { canQueryPaymentChannel } from "../utils/paymentAction";

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

const metrics = computed(() => ({
  total: total.value,
  successTotal: items.value.filter((item) => item.status === "SUCCESS").length,
  callbackPendingTotal: items.value.filter((item) => item.status === "WAIT_CALLBACK").length,
  amountTotal: sumPaymentAmounts(items.value.map((item) => item.amount))
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
  if (!canUseSnapshot.value) {
    return;
  }
  router.push(paymentDetailRoute(paymentOrderId));
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
  return ["PREPAY_CREATED", "RISK_REVIEW", "RISK_BLOCKED", "RISK_REJECTED"].includes(item.status)
    && !item.channelTransactionNo;
}

function canRunQuery(item) {
  return canQueryPaymentChannel(item.status, item.latestAttemptStatus);
}

function updatePaymentSnapshot(paymentDetail) {
  const updatedItem = {
    paymentOrderId: paymentDetail.paymentOrderId,
    orderNo: paymentDetail.orderNo,
    customerName: paymentDetail.customerName,
    amount: paymentDetail.amount,
    paymentMethod: paymentDetail.paymentMethod,
    channel: paymentDetail.channel,
    channelTransactionNo: paymentDetail.channelTransactionNo,
    latestAttemptStatus: paymentDetail.latestAttemptStatus,
    status: paymentDetail.status,
    statusType: paymentDetail.statusType,
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
    const paymentDetail = await paymentApi.query(paymentOrderId, operator);
    updatePaymentSnapshot(paymentDetail);
    actionResultMessage.value = `支付单 ${paymentOrderId} 已完成主动查单，当前状态为 ${paymentDetail.status}。`;
    actionRefreshErrorMessage.value = "";
    await refreshList(paymentOrderId, true, query);
  } catch (error) {
    actionErrorMessage.value = `支付单 ${paymentOrderId} 查单失败：${error.message}`;
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
    const paymentDetail = await paymentApi.close(paymentOrderId, operator);
    updatePaymentSnapshot(paymentDetail);
    actionResultMessage.value = paymentDetail.status === "CLOSED"
      ? `支付单 ${paymentOrderId} 已关闭。`
      : `支付单 ${paymentOrderId} 当前状态为 ${paymentDetail.status}，未执行关闭。`;
    actionRefreshErrorMessage.value = "";
    await refreshList(paymentOrderId, true, query);
  } catch (error) {
    actionErrorMessage.value = `支付单 ${paymentOrderId} 关闭失败：${error.message}`;
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
        <p class="card-title">本页支付成功</p>
        <p class="card-value">{{ metrics.successTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">本页待回调</p>
        <p class="card-value">{{ metrics.callbackPendingTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">本页实付金额</p>
        <p class="card-value">{{ metrics.amountTotal }}</p>
      </article>
    </section>

    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">
        {{ hasLoadedData ? "支付单数据刷新失败，当前展示上次成功快照：" : "支付单数据加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="actionRefreshErrorMessage" class="error-banner">
        人工动作结果已更新当前支付单，但列表刷新失败，其他支付单仍为上次成功快照：{{ actionRefreshErrorMessage }}
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
                <th>订单号</th>
                <th>用户</th>
                <th>实付金额</th>
                <th>支付方式</th>
                <th>支付渠道</th>
                <th>渠道交易号</th>
                <th>支付状态</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.paymentOrderId">
                <td>{{ item.paymentOrderId }}</td>
                <td>{{ item.orderNo }}</td>
                <td>{{ item.customerName }}</td>
                <td>{{ item.amount }}</td>
                <td>{{ item.paymentMethod }}</td>
                <td>{{ item.channel }}</td>
                <td>{{ item.channelTransactionNo }}</td>
                <td><span :class="['badge', item.statusType]">{{ item.status }}</span></td>
                <td>{{ item.createdAt }}</td>
                <td>
                  <div class="list-actions">
                    <button class="link-button" :disabled="!canUseSnapshot" @click="pickItem(item)">快照</button>
                    <button class="link-button" :disabled="!canUseSnapshot" @click="openDetail(item.paymentOrderId)">详情</button>
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
              <div class="detail-card"><span>订单号</span><strong>{{ selectedItem.orderNo }}</strong></div>
              <div class="detail-card"><span>客户名称</span><strong>{{ selectedItem.customerName }}</strong></div>
              <div class="detail-card"><span>实付金额</span><strong>{{ selectedItem.amount }}</strong></div>
              <div class="detail-card"><span>支付方式</span><strong>{{ selectedItem.paymentMethod }}</strong></div>
              <div class="detail-card"><span>支付渠道</span><strong>{{ selectedItem.channel }}</strong></div>
              <div class="detail-card"><span>渠道交易号</span><strong>{{ selectedItem.channelTransactionNo || "—" }}</strong></div>
              <div class="detail-card"><span>支付状态</span><strong>{{ selectedItem.status }}</strong></div>
              <div class="detail-card"><span>最近尝试</span><strong>{{ selectedItem.latestAttemptStatus || "—" }}</strong></div>
              <div class="detail-card"><span>创建时间</span><strong>{{ selectedItem.createdAt }}</strong></div>
            </div>
            <div class="ops-card">
              <div class="ops-title">排障建议</div>
              <div class="ops-row"><span>优先动作</span><span>已提交先查单，未提交可关闭</span></div>
              <div class="ops-row"><span>重点核对</span><span>支付状态、渠道流水号、回调是否收口</span></div>
              <div class="ops-row"><span>典型场景</span><span>待回调长时间未收口、重复支付保护、关闭单保护</span></div>
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
