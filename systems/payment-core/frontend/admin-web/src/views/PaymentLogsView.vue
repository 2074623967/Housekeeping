<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentLogApi } from "../api/client";
import { paymentDetailRoute, paymentLogDetailRoute } from "../router/detailRoutes";
import { paymentEventsRoute, paymentFlowsRoute, paymentLogsRoute, paymentRequestsRoute } from "../router/listRoutes";

const route = useRoute();
const router = useRouter();
const items = ref([]);
const selectedItem = ref(null);
const isLoading = ref(true);
const hasLoadedData = ref(false);
const errorMessage = ref("");
const total = ref(0);
const overview = ref({
  totalLogCount: 0,
  errorLogCount: 0,
  warnLogCount: 0,
  infoLogCount: 0,
  distinctStageCount: 0,
  distinctSourceCount: 0,
  callbackErrorCount: 0,
  eventWarnCount: 0,
  callbackKeywordCount: 0,
  latestLogAt: ""
});
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
    processStage: queryString(route.query.processStage, "全部"),
    logLevel: queryString(route.query.logLevel, "全部"),
    source: queryString(route.query.source, ""),
    keyword: queryString(route.query.keyword, ""),
    sortField: queryString(route.query.sortField, "createdAt"),
    sortOrder: queryString(route.query.sortOrder, "desc")
  };
}

const filters = ref(filtersFromRoute());

const metrics = computed(() => ({
  total: overview.value.totalLogCount,
  errorTotal: overview.value.errorLogCount,
  warnTotal: overview.value.warnLogCount,
  infoTotal: overview.value.infoLogCount,
  stageCount: overview.value.distinctStageCount,
  sourceCount: overview.value.distinctSourceCount,
  callbackErrorCount: overview.value.callbackErrorCount,
  eventWarnCount: overview.value.eventWarnCount,
  callbackKeywordCount: overview.value.callbackKeywordCount,
  latestLogAt: overview.value.latestLogAt || "-"
}));
const isSnapshotStale = computed(() => hasLoadedData.value && Boolean(errorMessage.value));
const canUseSnapshot = computed(() => Boolean(hasLoadedData.value && !isLoading.value && !isSnapshotStale.value));
const canExport = computed(() => hasLoadedData.value && total.value > 0 && !isSnapshotStale.value);

function buildQuery() {
  return {
    ...filters.value,
    pageNo: pageNo.value,
    pageSize
  };
}

function resetFilters() {
  if (isLoading.value) {
    return;
  }
  filters.value = {
    paymentOrderId: "",
    orderNo: "",
    processStage: "全部",
    logLevel: "全部",
    source: "",
    keyword: "",
    sortField: "createdAt",
    sortOrder: "desc"
  };
  pageNo.value = 1;
  loadPaymentLogs();
}

function applyFilters() {
  if (isLoading.value) {
    return;
  }
  pageNo.value = 1;
  loadPaymentLogs();
}

function refreshPaymentLogs() {
  if (isLoading.value) {
    return;
  }
  loadPaymentLogs();
}

function pickItem(item) {
  if (!canUseSnapshot.value) {
    return;
  }
  selectedItem.value = item;
}

function openPaymentDetail(paymentOrderId) {
  if (!canUseSnapshot.value || !paymentOrderId) return;
  router.push(paymentDetailRoute(paymentOrderId));
}

function openLogDetail(logNo) {
  if (!canUseSnapshot.value || !logNo) return;
  router.push(paymentLogDetailRoute(logNo));
}

function openPaymentFlows(paymentOrderId) {
  if (!canUseSnapshot.value || !paymentOrderId) return;
  router.push(paymentFlowsRoute({ paymentOrderId }));
}

function openPaymentRequests(paymentOrderId) {
  if (!canUseSnapshot.value || !paymentOrderId) return;
  router.push(paymentRequestsRoute({ paymentOrderId }));
}

function openPaymentEvents(paymentOrderId) {
  if (!canUseSnapshot.value || !paymentOrderId) return;
  router.push(paymentEventsRoute({ paymentOrderId }));
}

function openCallbackErrorLogs() {
  if (!canUseSnapshot.value || !(metrics.value.callbackErrorCount > 0)) return;
  router.push(paymentLogsRoute({ processStage: "渠道回调", logLevel: "ERROR" }));
}

function openEventWarnLogs() {
  if (!canUseSnapshot.value || !(metrics.value.eventWarnCount > 0)) return;
  router.push(paymentLogsRoute({ processStage: "业务事件", logLevel: "WARN" }));
}

function openCallbackKeywordLogs() {
  if (!canUseSnapshot.value || !(metrics.value.callbackKeywordCount > 0)) return;
  router.push(paymentLogsRoute({ keyword: "回调" }));
}

async function loadPaymentLogs(query = buildQuery()) {
  const requestId = ++latestLoadRequestId;
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const [overviewResult, result] = await Promise.all([
      paymentLogApi.getOverview(query),
      paymentLogApi.getList(query)
    ]);
    if (requestId !== latestLoadRequestId) {
      return;
    }
    overview.value = {
      ...overview.value,
      ...overviewResult
    };
    total.value = result.total;
    items.value = result.items;
    selectedItem.value = result.items[0] || null;
    loadedPageNo.value = query.pageNo;
    loadedQuery.value = { ...query };
    hasLoadedData.value = true;
  } catch (error) {
    if (requestId !== latestLoadRequestId) {
      return;
    }
    errorMessage.value = error.message;
    if (hasLoadedData.value) {
      pageNo.value = loadedPageNo.value;
    }
  } finally {
    if (requestId === latestLoadRequestId) {
      isLoading.value = false;
    }
  }
}

function exportLogs() {
  if (isLoading.value || !canExport.value || !loadedQuery.value) {
    return;
  }
  const exportUrl = paymentLogApi.buildExportUrl(loadedQuery.value);
  window.open(exportUrl, "_blank", "noopener,noreferrer");
}

function goToPage(nextPage) {
  if (isLoading.value || isSnapshotStale.value || !loadedQuery.value
      || nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  loadPaymentLogs({ ...loadedQuery.value, pageNo: nextPage });
}

onMounted(loadPaymentLogs);

watch(
  () => [route.query.paymentOrderId, route.query.orderNo, route.query.processStage,
    route.query.logLevel, route.query.source, route.query.keyword,
    route.query.sortField, route.query.sortOrder],
  () => {
    filters.value = filtersFromRoute();
    pageNo.value = 1;
    loadPaymentLogs();
  }
);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付处理日志</h2>
        <p>按处理阶段查看支付提交、路由、回调和业务事件日志，支撑异常定位与测试回归</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" :disabled="isLoading" @click="refreshPaymentLogs">
          {{ isLoading ? "刷新中..." : "刷新" }}
        </button>
        <button class="button primary" :disabled="isLoading || !canExport" @click="exportLogs">导出日志</button>
      </div>
    </div>

    <section v-if="hasLoadedData" class="card-grid">
      <article class="card">
        <p class="card-title">日志总数</p>
        <p class="card-value">{{ metrics.total }}</p>
      </article>
      <article class="card">
        <p class="card-title">错误日志</p>
        <p class="card-value">{{ metrics.errorTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">告警日志</p>
        <p class="card-value">{{ metrics.warnTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">信息日志</p>
        <p class="card-value">{{ metrics.infoTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">涉及阶段数</p>
        <p class="card-value">{{ metrics.stageCount }}</p>
      </article>
      <article class="card">
        <p class="card-title">涉及来源数</p>
        <p class="card-value">{{ metrics.sourceCount }}</p>
      </article>
      <article class="card">
        <p class="card-title">回调错误数</p>
        <p class="card-value">{{ metrics.callbackErrorCount }}</p>
      </article>
      <article class="card">
        <p class="card-title">事件告警数</p>
        <p class="card-value">{{ metrics.eventWarnCount }}</p>
      </article>
      <article class="card">
        <p class="card-title">回调关键词命中</p>
        <p class="card-value">{{ metrics.callbackKeywordCount }}</p>
      </article>
    </section>

    <section v-if="hasLoadedData" class="panel overview-panel">
      <div class="section-title">
        <h3>风险总览</h3>
        <span class="meta">最近日志时间：{{ metrics.latestLogAt }}</span>
      </div>
      <div class="overview-grid">
        <article class="overview-card danger">
          <p class="overview-title">回调异常优先排查</p>
          <strong>{{ metrics.callbackErrorCount }}</strong>
          <span>聚焦渠道回调阶段 ERROR 日志，优先核对签名、幂等和状态机推进。</span>
          <button class="link-button" :disabled="!canUseSnapshot || !(metrics.callbackErrorCount > 0)" @click="openCallbackErrorLogs">
            查看回调错误
          </button>
        </article>
        <article class="overview-card warn">
          <p class="overview-title">业务事件告警</p>
          <strong>{{ metrics.eventWarnCount }}</strong>
          <span>重点检查 `PAYMENT_CLOSED` 等业务事件是否与真实订单状态一致。</span>
          <button class="link-button" :disabled="!canUseSnapshot || !(metrics.eventWarnCount > 0)" @click="openEventWarnLogs">
            查看事件告警
          </button>
        </article>
        <article class="overview-card info">
          <p class="overview-title">关键字命中</p>
          <strong>{{ metrics.callbackKeywordCount }}</strong>
          <span>当前筛选条件下命中“回调”关键字的日志量，可快速圈定通知链路问题面。</span>
          <button class="link-button" :disabled="!canUseSnapshot || !(metrics.callbackKeywordCount > 0)" @click="openCallbackKeywordLogs">
            查看回调日志
          </button>
        </article>
      </div>
    </section>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        {{ hasLoadedData ? "支付处理日志刷新失败，当前展示上次成功快照：" : "支付处理日志加载失败：" }}{{ errorMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>支付单号</label>
          <input v-model="filters.paymentOrderId" placeholder="请输入支付单号" :disabled="isLoading" />
        </div>
        <div class="field">
          <label>订单号</label>
          <input v-model="filters.orderNo" placeholder="请输入订单号" :disabled="isLoading" />
        </div>
        <div class="field">
          <label>处理阶段</label>
          <select v-model="filters.processStage" :disabled="isLoading">
            <option>全部</option>
            <option>支付提交</option>
            <option>支付路由</option>
            <option>渠道回调</option>
            <option>业务事件</option>
          </select>
        </div>
        <div class="field">
          <label>日志级别</label>
          <select v-model="filters.logLevel" :disabled="isLoading">
            <option>全部</option>
            <option>INFO</option>
            <option>WARN</option>
            <option>ERROR</option>
          </select>
        </div>
        <div class="field">
          <label>日志来源</label>
          <input v-model="filters.source" placeholder="如 wx_h5 / payment-core" :disabled="isLoading" />
        </div>
        <div class="field">
          <label>日志关键字</label>
          <input v-model="filters.keyword" placeholder="如 回调 / 路由 / SUCCESS" :disabled="isLoading" />
        </div>
        <div class="field">
          <label>排序字段</label>
          <select v-model="filters.sortField" :disabled="isLoading">
            <option value="createdAt">创建时间</option>
            <option value="logLevel">日志级别</option>
            <option value="processStage">处理阶段</option>
          </select>
        </div>
        <div class="field">
          <label>排序方向</label>
          <select v-model="filters.sortOrder" :disabled="isLoading">
            <option value="desc">倒序</option>
            <option value="asc">正序</option>
          </select>
        </div>
        <div class="field">
          <label>当前说明</label>
          <input value="已支持订单号、来源、关键字检索与排序，生产环境需接入检索与告警平台" disabled />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" :disabled="isLoading" @click="applyFilters">
            {{ isLoading ? "查询中..." : "查询" }}
          </button>
          <button class="button secondary" :disabled="isLoading" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">支付处理日志加载中...</div>

      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的支付处理日志快照</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的支付处理日志</div>

      <div v-else class="detail-layout">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>日志编号</th>
                <th>支付单号</th>
                <th>订单号</th>
                <th>处理阶段</th>
                <th>级别</th>
                <th>来源</th>
                <th>日志消息</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.logNo">
                <td>{{ item.logNo }}</td>
                <td>
                  <button class="link-button" :disabled="!canUseSnapshot" @click="openPaymentDetail(item.paymentOrderId)">
                    {{ item.paymentOrderId }}
                  </button>
                </td>
                <td>{{ item.orderNo }}</td>
                <td>{{ item.processStage }}</td>
                <td><span :class="['badge', item.logLevelType]">{{ item.logLevel }}</span></td>
                <td>{{ item.source }}</td>
                <td class="flow-summary-cell">{{ item.message }}</td>
                <td>{{ item.createdAt }}</td>
                <td>
                  <button class="link-button" :disabled="!canUseSnapshot" @click="openLogDetail(item.logNo)">查看详情</button>
                  <button class="link-button" :disabled="!canUseSnapshot" @click="pickItem(item)">查看快照</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <aside class="detail-side">
          <div v-if="selectedItem" class="detail-stack">
            <div class="section-title">
              <h3>日志详情</h3>
              <span class="meta">{{ selectedItem.logNo }}</span>
            </div>
            <div class="detail-grid">
              <div class="detail-card"><span>支付单号</span><strong>{{ selectedItem.paymentOrderId }}</strong></div>
              <div class="detail-card"><span>订单号</span><strong>{{ selectedItem.orderNo }}</strong></div>
              <div class="detail-card"><span>处理阶段</span><strong>{{ selectedItem.processStage }}</strong></div>
              <div class="detail-card"><span>日志级别</span><strong>{{ selectedItem.logLevel }}</strong></div>
              <div class="detail-card"><span>来源</span><strong>{{ selectedItem.source }}</strong></div>
              <div class="detail-card"><span>创建时间</span><strong>{{ selectedItem.createdAt }}</strong></div>
              <div class="detail-card detail-card-wide"><span>日志消息</span><strong>{{ selectedItem.message }}</strong></div>
            </div>
            <div class="ops-card">
              <div class="ops-title">排障建议</div>
              <div class="ops-row"><span>优先联查</span><span>支付单详情 / 支付请求 / 支付事件</span></div>
              <div class="ops-row"><span>适用角色</span><span>运营 / 研发 / 测试共用同一入口</span></div>
              <div class="ops-row"><span>重点核对</span><span>处理阶段、来源系统、级别和关键字</span></div>
              <div class="ops-row"><span>当前过滤命中</span><span>{{ metrics.callbackKeywordCount }} 条回调相关日志</span></div>
            </div>
            <div class="table-inline-actions">
              <button class="link-button" :disabled="!canUseSnapshot" @click="openLogDetail(selectedItem.logNo)">查看日志详情</button>
              <button class="link-button" :disabled="!canUseSnapshot" @click="openPaymentDetail(selectedItem.paymentOrderId)">查看支付单</button>
              <button class="link-button" :disabled="!canUseSnapshot" @click="openPaymentFlows(selectedItem.paymentOrderId)">查看支付流水</button>
              <button class="link-button" :disabled="!canUseSnapshot" @click="openPaymentRequests(selectedItem.paymentOrderId)">查看支付请求</button>
              <button class="link-button" :disabled="!canUseSnapshot" @click="openPaymentEvents(selectedItem.paymentOrderId)">查看支付事件</button>
            </div>
          </div>
          <div v-else class="state-box">选择左侧日志后，可在这里查看详情与排障建议。</div>
        </aside>
      </div>
      <div v-if="hasLoadedData && total > pageSize" class="pager">
        <span>共 {{ total }} 条日志</span>
        <button class="button secondary" :disabled="isLoading || isSnapshotStale || pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
        <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button class="button secondary" :disabled="isLoading || isSnapshotStale || pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.overview-panel {
  display: grid;
  gap: 16px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.overview-card {
  display: grid;
  gap: 8px;
  padding: 18px;
  border-radius: 18px;
  border: 1px solid #dbe3f0;
  background: #f8fafc;
}

.overview-card strong {
  font-size: 28px;
  color: #0f172a;
}

.overview-card span {
  color: #475569;
  line-height: 1.6;
}

.overview-title {
  margin: 0;
  font-size: 13px;
  color: #64748b;
}

.overview-card.danger {
  background: #fff1f2;
  border-color: #fecdd3;
}

.overview-card.warn {
  background: #fff7ed;
  border-color: #fed7aa;
}

.overview-card.info {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) 360px;
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
}

.detail-card-wide {
  grid-column: 1 / -1;
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
  .overview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
