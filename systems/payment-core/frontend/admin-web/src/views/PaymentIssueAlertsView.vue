<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentIssueApi } from "../api/client";
import { paymentDetailRoute } from "../router/detailRoutes";
import { paymentIssuesRoute, paymentLogsRoute } from "../router/listRoutes";
import { paymentIssueAlertListFiltersFromQuery } from "../utils/paymentIssueAlertListFilters";

const route = useRoute();
const router = useRouter();
const items = ref([]);
const selectedItem = ref(null);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const loadedPageNo = ref(1);
const loadedQuery = ref(null);
const isLoading = ref(true);
const hasLoadedData = ref(false);
const activeAlertNo = ref("");
const activeReceiptQueryAlertNo = ref("");
const loadErrorMessage = ref("");
const actionRefreshErrorMessage = ref("");
const actionErrorMessage = ref("");
const successMessage = ref("");
const ackOperator = ref("");
const filters = ref(paymentIssueAlertListFiltersFromQuery(route.query));
let latestLoadRequestId = 0;

const isActionRunning = computed(() => Boolean(
  activeAlertNo.value || activeReceiptQueryAlertNo.value
));
const isInteractionLocked = computed(() => isLoading.value || isActionRunning.value);
const isSnapshotStale = computed(() => Boolean(
  hasLoadedData.value && (loadErrorMessage.value || actionRefreshErrorMessage.value)
));
const canUseSnapshot = computed(() => hasLoadedData.value
  && !isInteractionLocked.value
  && !isSnapshotStale.value);
const canExport = computed(() => canUseSnapshot.value && total.value > 0 && Boolean(loadedQuery.value));

const metricCards = computed(() => [
  {
    title: "告警总数",
    value: total.value,
    hint: "当前筛选条件下的异常触达记录"
  },
  {
    title: "待确认回执",
    value: items.value.filter((item) => item.ackStatus === "待确认").length,
    hint: "需运营或研发确认触达闭环"
  },
  {
    title: "派发失败",
    value: items.value.filter((item) => item.alertStatus === "派发失败").length,
    hint: "需优先处理供应商或配置异常"
  },
  {
    title: "高等级告警",
    value: items.value.filter((item) => item.severity === "P1" || item.severity === "高").length,
    hint: "用于识别阻断级支付异常"
  }
]);

const alertSuggestions = computed(() => {
  if (!selectedItem.value) {
    return [];
  }
  const suggestions = [];
  if (selectedItem.value.ackStatus === "待确认") {
    suggestions.push("当前告警仍待确认，建议先核对接收人是否已收到通知并补录确认回执。");
  }
  if (selectedItem.value.alertStatus === "派发失败") {
    suggestions.push("当前告警派发失败，建议优先检查供应商配置、模板编码和通知器装配状态。");
  }
  if (selectedItem.value.providerDeliveryStatus === "CONFIG_MISSING") {
    suggestions.push("供应商配置缺失，建议先到支付配置中心检查告警供应商与端点别名。");
  }
  if (selectedItem.value.providerDeliveryStatus === "SEND_EXCEPTION") {
    suggestions.push("供应商发送异常，建议联查支付处理日志与 outbox 派发日志。");
  }
  if (selectedItem.value.receiptQueryState === "EXHAUSTED") {
    suggestions.push("自动回查次数已耗尽，建议核对供应商回执后执行受控人工回查或人工确认，不要推测最终送达。");
  }
  if (selectedItem.value.receiptQueryState === "QUERYING") {
    suggestions.push("当前回查 claim 仍有效，建议等待本次执行完成或占用超时，避免重复外呼。");
  }
  if (!suggestions.length) {
    suggestions.push("当前告警链路已相对完整，可结合支付单详情与异常中心继续做业务复盘。");
  }
  return suggestions;
});

function resetFilters() {
  if (isInteractionLocked.value) {
    return;
  }
  filters.value = {
    alertNo: "",
    issueNo: "",
    paymentOrderId: "",
    alertChannel: "全部",
    alertStatus: "全部",
    ackStatus: "全部",
    providerDeliveryStatus: "全部",
    receiptQueryState: "全部"
  };
  pageNo.value = 1;
  loadAlertLogs();
}

function applyFilters() {
  if (isInteractionLocked.value) {
    return;
  }
  pageNo.value = 1;
  loadAlertLogs();
}

function exportAlertLogs() {
  if (!canExport.value || !loadedQuery.value) {
    return;
  }
  const exportUrl = paymentIssueApi.buildAlertExportUrl({
    alertNo: loadedQuery.value.alertNo,
    issueNo: loadedQuery.value.issueNo,
    paymentOrderId: loadedQuery.value.paymentOrderId,
    alertChannel: loadedQuery.value.alertChannel,
    alertStatus: loadedQuery.value.alertStatus,
    ackStatus: loadedQuery.value.ackStatus,
    providerDeliveryStatus: loadedQuery.value.providerDeliveryStatus,
    receiptQueryState: loadedQuery.value.receiptQueryState
  });
  window.open(exportUrl, "_blank", "noopener,noreferrer");
}

function buildQuery() {
  return {
    alertNo: filters.value.alertNo,
    issueNo: filters.value.issueNo,
    paymentOrderId: filters.value.paymentOrderId,
    alertChannel: filters.value.alertChannel,
    alertStatus: filters.value.alertStatus,
    ackStatus: filters.value.ackStatus,
    providerDeliveryStatus: filters.value.providerDeliveryStatus,
    receiptQueryState: filters.value.receiptQueryState,
    pageNo: pageNo.value,
    pageSize
  };
}

async function loadAlertLogs(
  preferredAlertNo = selectedItem.value?.alertNo,
  query = buildQuery(),
  afterAction = false
) {
  const requestId = ++latestLoadRequestId;
  isLoading.value = true;
  loadErrorMessage.value = "";
  actionRefreshErrorMessage.value = "";
  if (!afterAction) {
    actionErrorMessage.value = "";
    successMessage.value = "";
  }
  try {
    const result = await paymentIssueApi.getAlertLogs(query);
    if (requestId !== latestLoadRequestId) {
      return false;
    }
    items.value = result.items;
    total.value = result.total;
    selectedItem.value = result.items.find((item) => item.alertNo === preferredAlertNo)
      || result.items[0]
      || null;
    pageNo.value = query.pageNo;
    loadedPageNo.value = query.pageNo;
    loadedQuery.value = { ...query };
    hasLoadedData.value = true;
    return true;
  } catch (error) {
    if (requestId !== latestLoadRequestId) {
      return false;
    }
    if (afterAction) {
      actionRefreshErrorMessage.value = error.message;
    } else {
      loadErrorMessage.value = error.message;
    }
    if (hasLoadedData.value) {
      pageNo.value = loadedPageNo.value;
    }
    return false;
  } finally {
    if (requestId === latestLoadRequestId) {
      isLoading.value = false;
    }
  }
}

async function acknowledgeAlert(alertNo) {
  const currentItem = items.value.find((item) => item.alertNo === alertNo);
  if (!canUseSnapshot.value || !loadedQuery.value || currentItem?.ackStatus !== "待确认") {
    actionErrorMessage.value = "当前告警快照不可确认，请刷新成功后重试";
    successMessage.value = "";
    return;
  }
  if (!ackOperator.value.trim()) {
    actionErrorMessage.value = "请先输入确认人";
    return;
  }
  activeAlertNo.value = alertNo;
  actionErrorMessage.value = "";
  successMessage.value = "";
  const query = { ...loadedQuery.value };
  const snapshotRequestId = latestLoadRequestId;
  try {
    await paymentIssueApi.acknowledgeAlert(alertNo, {
      operator: ackOperator.value.trim()
    });
    successMessage.value = `告警 ${alertNo} 已确认回执`;
    if (snapshotRequestId === latestLoadRequestId) {
      await loadAlertLogs(alertNo, query, true);
    }
  } catch (error) {
    actionErrorMessage.value = `确认回执失败：${error.message}`;
  } finally {
    activeAlertNo.value = "";
  }
}

async function reconcileAlertReceipt(alertNo) {
  const currentItem = items.value.find((item) => item.alertNo === alertNo);
  if (!canUseSnapshot.value || !loadedQuery.value || currentItem?.ackStatus !== "待确认"
      || !currentItem.receiptQueryState || currentItem.receiptQueryState === "QUERYING") {
    actionErrorMessage.value = "当前告警快照不可回查，请刷新成功后重试";
    successMessage.value = "";
    return;
  }
  if (!ackOperator.value.trim()) {
    actionErrorMessage.value = "请先输入操作人";
    return;
  }
  activeReceiptQueryAlertNo.value = alertNo;
  actionErrorMessage.value = "";
  successMessage.value = "";
  const query = { ...loadedQuery.value };
  const snapshotRequestId = latestLoadRequestId;
  try {
    const result = await paymentIssueApi.reconcileAlertReceipt(alertNo, {
      operator: ackOperator.value.trim()
    });
    const summary = result.summaryComment || `告警 ${alertNo} 回执回查已执行`;
    if (result.failCount > 0) {
      actionErrorMessage.value = summary;
    } else {
      successMessage.value = summary;
    }
    if (snapshotRequestId === latestLoadRequestId) {
      await loadAlertLogs(alertNo, query, true);
    }
  } catch (error) {
    actionErrorMessage.value = `回执回查失败：${error.message}`;
  } finally {
    activeReceiptQueryAlertNo.value = "";
  }
}

function goToPage(nextPage) {
  if (!canUseSnapshot.value || !loadedQuery.value
      || nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  loadAlertLogs(undefined, { ...loadedQuery.value, pageNo: nextPage });
}

function pickItem(item) {
  const currentItem = items.value.find((candidate) => candidate.alertNo === item.alertNo);
  if (!canUseSnapshot.value || !currentItem) {
    return;
  }
  selectedItem.value = currentItem;
}

function openSnapshotRoute(target) {
  if (!canUseSnapshot.value || !target) {
    return;
  }
  router.push(target);
}

function receiptQueryStateLabel(state) {
  return {
    INITIAL_WAIT: "首次等待",
    DUE: "当前可执行",
    BACKING_OFF: "退避中",
    QUERYING: "查询中",
    EXHAUSTED: "自动次数耗尽"
  }[state] || "-";
}

onMounted(loadAlertLogs);

watch(
  () => [
    route.query.alertNo,
    route.query.issueNo,
    route.query.paymentOrderId,
    route.query.alertChannel,
    route.query.alertStatus,
    route.query.ackStatus,
    route.query.providerDeliveryStatus,
    route.query.receiptQueryState
  ],
  () => {
    filters.value = paymentIssueAlertListFiltersFromQuery(route.query);
    pageNo.value = 1;
    selectedItem.value = null;
    actionErrorMessage.value = "";
    successMessage.value = "";
    loadAlertLogs();
  }
);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>异常告警明细台</h2>
        <p>统一联查 outbox、外部通知通道、供应商投递回执和人工确认状态，方便运营、研发与测试核对异常触达闭环</p>
      </div>
      <div class="list-actions">
        <button class="button primary" :disabled="!canExport" @click="exportAlertLogs">导出 CSV</button>
        <button class="button secondary" :disabled="isInteractionLocked" @click="loadAlertLogs()">刷新</button>
      </div>
    </div>

    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">
        {{ hasLoadedData ? "异常告警明细刷新失败，当前展示上次成功快照：" : "异常告警明细加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="actionErrorMessage" class="error-banner">{{ actionErrorMessage }}</div>
      <div v-if="actionRefreshErrorMessage" class="error-banner">
        人工动作结果已确认，但告警列表刷新失败，当前展示上次成功快照：{{ actionRefreshErrorMessage }}
      </div>
      <div v-if="successMessage" class="success-banner">
        {{ successMessage }}
      </div>

      <div v-if="hasLoadedData" class="detail-card-grid">
        <div v-for="card in metricCards" :key="card.title" class="detail-card">
          <div class="detail-label">{{ card.title }}</div>
          <div class="detail-value">{{ card.value }}</div>
          <div class="meta">{{ card.hint }}</div>
        </div>
      </div>

      <div class="toolbar">
        <div class="field">
          <label>告警编号</label>
          <input v-model="filters.alertNo" placeholder="请输入告警编号" :disabled="isInteractionLocked" />
        </div>
        <div class="field">
          <label>异常编号</label>
          <input v-model="filters.issueNo" placeholder="请输入异常编号" :disabled="isInteractionLocked" />
        </div>
        <div class="field">
          <label>支付单号</label>
          <input v-model="filters.paymentOrderId" placeholder="请输入支付单号" :disabled="isInteractionLocked" />
        </div>
        <div class="field">
          <label>告警通道</label>
          <select v-model="filters.alertChannel" :disabled="isInteractionLocked">
            <option>全部</option>
            <option>IN_APP_OUTBOX</option>
            <option>IM</option>
            <option>SMS</option>
            <option>EMAIL</option>
          </select>
        </div>
        <div class="field">
          <label>告警状态</label>
          <select v-model="filters.alertStatus" :disabled="isInteractionLocked">
            <option>全部</option>
            <option value="PENDING_DISPATCH">待派发（已生成 / 部分失败 / 派发失败）</option>
            <option>已生成</option>
            <option>已派发</option>
            <option>部分失败</option>
            <option>派发失败</option>
          </select>
        </div>
        <div class="field">
          <label>回执状态</label>
          <select v-model="filters.ackStatus" :disabled="isInteractionLocked">
            <option>全部</option>
            <option>待确认</option>
            <option>已确认</option>
            <option>无需回执</option>
          </select>
        </div>
        <div class="field">
          <label>供应商投递状态</label>
          <select v-model="filters.providerDeliveryStatus" :disabled="isInteractionLocked">
            <option>全部</option>
            <option>ACCEPTED</option>
            <option>DELIVERED</option>
            <option>CONFIG_MISSING</option>
            <option>NOTIFIER_MISSING</option>
            <option>SEND_EXCEPTION</option>
          </select>
        </div>
        <div class="field">
          <label>回查队列状态</label>
          <select v-model="filters.receiptQueryState" :disabled="isInteractionLocked">
            <option value="全部">全部</option>
            <option value="PENDING_RECEIPT">待回查（全部）</option>
            <option value="INITIAL_WAIT">首次等待</option>
            <option value="DUE">当前可执行</option>
            <option value="BACKING_OFF">退避中</option>
            <option value="QUERYING">查询中</option>
            <option value="EXHAUSTED">自动次数耗尽</option>
          </select>
        </div>
        <div class="field">
          <label>操作人</label>
          <input v-model="ackOperator" maxlength="64" placeholder="请输入操作人" :disabled="isInteractionLocked" />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" :disabled="isInteractionLocked" @click="applyFilters">查询</button>
          <button class="button secondary" :disabled="isInteractionLocked" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">异常告警明细加载中...</div>
      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的异常告警明细快照</div>
      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的异常告警明细</div>

      <div v-else class="detail-layout">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>告警编号</th>
                <th>异常编号</th>
                <th>支付单号</th>
                <th>异常类型</th>
                <th>严重等级</th>
                <th>责任组</th>
                <th>通道/接收人</th>
                <th>告警状态</th>
                <th>回执状态</th>
                <th>供应商信息</th>
                <th>投递回执</th>
                <th>回查状态</th>
                <th>操作</th>
                <th>创建时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.alertNo">
                <td>
                  <button class="table-link" :disabled="!canUseSnapshot" @click="pickItem(item)">{{ item.alertNo }}</button>
                </td>
                <td>{{ item.issueNo }}</td>
                <td>
                  <button class="link-button" :disabled="!canUseSnapshot" @click="openSnapshotRoute(paymentDetailRoute(item.paymentOrderId))">
                    {{ item.paymentOrderId }}
                  </button>
                </td>
                <td>{{ item.issueType }}</td>
                <td>{{ item.severity }}</td>
                <td>{{ item.responsibilityGroup }}</td>
                <td>
                  <div>{{ item.alertChannel }}</div>
                  <div class="muted-text">{{ item.receiver }}</div>
                </td>
                <td><span :class="['badge', item.alertStatusType]">{{ item.alertStatus }}</span></td>
                <td><span :class="['badge', item.ackStatusType]">{{ item.ackStatus }}</span></td>
                <td class="flow-summary-cell">
                  <div>{{ item.providerName || item.providerCode || "-" }}</div>
                  <div class="muted-text">{{ item.endpointAlias || "-" }}</div>
                  <div class="muted-text">{{ item.templateCode || "-" }}</div>
                </td>
                <td class="flow-summary-cell">
                  <div>{{ item.providerDeliveryStatus || "-" }}</div>
                  <div class="muted-text">{{ item.providerReceiptNo || "-" }}</div>
                  <div class="muted-text">{{ item.providerDeliveryMessage || "-" }}</div>
                </td>
                <td class="flow-summary-cell">
                  <div>{{ receiptQueryStateLabel(item.receiptQueryState) }}</div>
                  <div class="muted-text">次数 {{ item.receiptQueryCount ?? 0 }}</div>
                  <div class="muted-text">结果 {{ item.receiptQueryResult || "-" }}</div>
                </td>
                <td>
                  <div v-if="item.ackStatus === '待确认'" class="list-actions">
                    <button
                      v-if="item.receiptQueryState && item.receiptQueryState !== 'QUERYING'"
                      class="button secondary"
                      :disabled="!canUseSnapshot"
                      @click="reconcileAlertReceipt(item.alertNo)"
                    >
                      {{ activeReceiptQueryAlertNo === item.alertNo ? "回查中..." : "人工回查" }}
                    </button>
                    <button
                      class="button secondary"
                      :disabled="!canUseSnapshot"
                      @click="acknowledgeAlert(item.alertNo)"
                    >
                      {{ activeAlertNo === item.alertNo ? "确认中..." : "确认回执" }}
                    </button>
                  </div>
                  <span v-else class="muted-text">无需操作</span>
                </td>
                <td>{{ item.createdAt }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <aside class="detail-side">
          <div v-if="selectedItem" class="detail-stack">
            <div class="section-title">
              <h3>告警快照</h3>
              <span class="meta">{{ selectedItem.alertNo }}</span>
            </div>
            <div class="detail-grid">
              <div class="detail-card"><span>支付单号</span><strong>{{ selectedItem.paymentOrderId }}</strong></div>
              <div class="detail-card"><span>异常编号</span><strong>{{ selectedItem.issueNo }}</strong></div>
              <div class="detail-card"><span>触发来源</span><strong>{{ selectedItem.triggeredBy || "-" }}</strong></div>
              <div class="detail-card"><span>接收人</span><strong>{{ selectedItem.receiver || "-" }}</strong></div>
              <div class="detail-card"><span>供应商状态</span><strong>{{ selectedItem.providerDeliveryStatus || "-" }}</strong></div>
              <div class="detail-card"><span>回查队列</span><strong>{{ receiptQueryStateLabel(selectedItem.receiptQueryState) }}</strong></div>
              <div class="detail-card"><span>回执确认</span><strong>{{ selectedItem.ackAt || "未确认" }}</strong></div>
            </div>
            <div class="ops-card">
              <div class="ops-title">联查入口</div>
              <div class="list-actions">
                <button class="link-button" :disabled="!canUseSnapshot" @click="openSnapshotRoute(paymentDetailRoute(selectedItem.paymentOrderId))">查看支付单</button>
                <button class="link-button" :disabled="!canUseSnapshot" @click="openSnapshotRoute(paymentIssuesRoute({ issueNo: selectedItem.issueNo }))">查看异常中心</button>
                <button class="link-button" :disabled="!canUseSnapshot" @click="openSnapshotRoute(paymentLogsRoute({ paymentOrderId: selectedItem.paymentOrderId }))">查看处理日志</button>
              </div>
            </div>
            <div class="ops-card">
              <div class="ops-title">告警内容</div>
              <div class="timeline-item">{{ selectedItem.alertContent }}</div>
              <div v-if="selectedItem.renderedContentSnapshot" class="timeline-item">{{ selectedItem.renderedContentSnapshot }}</div>
            </div>
            <div class="ops-card">
              <div class="ops-title">处理建议</div>
              <div v-for="item in alertSuggestions" :key="item" class="timeline-item">{{ item }}</div>
            </div>
            <div class="ops-card">
              <div class="ops-title">供应商回执</div>
              <div class="ops-row"><span>供应商</span><span>{{ selectedItem.providerName || selectedItem.providerCode || "-" }}</span></div>
              <div class="ops-row"><span>端点</span><span>{{ selectedItem.endpointAlias || "-" }}</span></div>
              <div class="ops-row"><span>模板</span><span>{{ selectedItem.templateCode || "-" }}</span></div>
              <div class="ops-row"><span>回执号</span><span>{{ selectedItem.providerReceiptNo || "-" }}</span></div>
              <div class="ops-row"><span>回查次数</span><span>{{ selectedItem.receiptQueryCount ?? 0 }}</span></div>
              <div class="ops-row"><span>最近回查</span><span>{{ selectedItem.lastReceiptQueryAt || "-" }}</span></div>
              <div class="ops-row"><span>下次自动回查</span><span>{{ selectedItem.nextReceiptQueryAt || "-" }}</span></div>
              <div class="ops-row"><span>最近回查结果</span><span>{{ selectedItem.receiptQueryResult || "-" }}</span></div>
              <div class="timeline-item">{{ selectedItem.providerDeliveryMessage || "-" }}</div>
              <div class="timeline-item">{{ selectedItem.providerReceiptSnapshot || "-" }}</div>
            </div>
          </div>
          <div v-else class="state-box">选择左侧告警后，可在这里查看告警快照、联查入口和处理建议。</div>
        </aside>
      </div>

      <div v-if="hasLoadedData" class="pager">
        <span>共 {{ total }} 条异常告警明细</span>
        <template v-if="total > pageSize">
          <button class="button secondary" :disabled="!canUseSnapshot || pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
          <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
          <button class="button secondary" :disabled="!canUseSnapshot || pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
        </template>
      </div>
    </section>
  </div>
</template>

<style scoped>
.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) 360px;
  gap: 16px;
}

.detail-side {
  display: grid;
  align-self: start;
}

.detail-stack {
  display: grid;
  gap: 16px;
}

.table-link {
  padding: 0;
  border: none;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
}

@media (max-width: 1200px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }
}
</style>
