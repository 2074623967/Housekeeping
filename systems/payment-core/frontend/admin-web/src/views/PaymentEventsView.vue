<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { paymentEventApi } from "../api/client";
import { paymentDetailRoute } from "../router/detailRoutes";
import { paymentFlowsRoute, paymentLogsRoute } from "../router/listRoutes";
import {
  paymentEventRecommendedAction,
  paymentSuccessHttpStageMeta
} from "../utils/paymentEventStage";

const route = useRoute();
const items = ref([]);
const selectedItem = ref(null);
const isLoading = ref(true);
const hasLoadedData = ref(false);
const loadErrorMessage = ref("");
const overviewRefreshErrorMessage = ref("");
const actionErrorMessage = ref("");
const successMessage = ref("");
const total = ref(0);
const overview = ref({
  totalEventCount: 0,
  successEventCount: 0,
  pendingEventCount: 0,
  failedEventCount: 0,
  deadLetterEventCount: 0,
  unknownEventCount: 0,
  failedOrDeadLetterCount: 0,
  distinctDownstreamCount: 0,
  dueRetryEventCount: 0,
  paymentSuccessEventCount: 0,
  latestPublishedAt: ""
});
const pageNo = ref(1);
const loadedPageNo = ref(1);
const pageSize = 20;
const activeEventNo = ref("");
const republishOperator = ref("");
const filters = ref({
  paymentOrderId: typeof route.query.paymentOrderId === "string" ? route.query.paymentOrderId : "",
  eventType: typeof route.query.eventType === "string" ? route.query.eventType : "全部",
  publishStatus: typeof route.query.publishStatus === "string" ? route.query.publishStatus : "全部",
  downstreamSystem: typeof route.query.downstreamSystem === "string" ? route.query.downstreamSystem : "全部",
  eventTopic: typeof route.query.eventTopic === "string" ? route.query.eventTopic : "",
  sortField: typeof route.query.sortField === "string" ? route.query.sortField : "createdAt",
  sortOrder: typeof route.query.sortOrder === "string" ? route.query.sortOrder : "desc"
});

const metrics = computed(() => ({
  total: overview.value.totalEventCount,
  successTotal: overview.value.successEventCount,
  pendingTotal: overview.value.pendingEventCount,
  failedTotal: overview.value.failedOrDeadLetterCount,
  deadLetterTotal: overview.value.deadLetterEventCount,
  unknownTotal: overview.value.unknownEventCount,
  downstreamCount: overview.value.distinctDownstreamCount,
  dueRetryCount: overview.value.dueRetryEventCount,
  paymentSuccessEventCount: overview.value.paymentSuccessEventCount,
  latestPublishedAt: overview.value.latestPublishedAt || "-"
}));

const isSnapshotStale = computed(() => Boolean(
  hasLoadedData.value && (loadErrorMessage.value || overviewRefreshErrorMessage.value)
));

function buildQuery(includePagination = true) {
  const query = {
    paymentOrderId: filters.value.paymentOrderId,
    eventType: filters.value.eventType,
    publishStatus: filters.value.publishStatus,
    downstreamSystem: filters.value.downstreamSystem,
    eventTopic: filters.value.eventTopic,
    sortField: filters.value.sortField,
    sortOrder: filters.value.sortOrder
  };
  if (includePagination) {
    query.pageNo = pageNo.value;
    query.pageSize = pageSize;
  }
  return query;
}

function resetFilters() {
  filters.value = {
    paymentOrderId: "",
    eventType: "全部",
    publishStatus: "全部",
    downstreamSystem: "全部",
    eventTopic: "",
    sortField: "createdAt",
    sortOrder: "desc"
  };
  pageNo.value = 1;
  loadEvents();
}

function applyFilters() {
  pageNo.value = 1;
  loadEvents();
}

function pickItem(item) {
  selectedItem.value = item;
}

async function loadEvents() {
  isLoading.value = true;
  try {
    const query = buildQuery();
    const [overviewResult, result] = await Promise.all([
      paymentEventApi.getOverview(query),
      paymentEventApi.getList(query)
    ]);
    overview.value = {
      ...overview.value,
      ...overviewResult
    };
    items.value = result.items;
    total.value = result.total;
    selectedItem.value = result.items[0] || null;
    loadedPageNo.value = pageNo.value;
    hasLoadedData.value = true;
    loadErrorMessage.value = "";
    overviewRefreshErrorMessage.value = "";
  } catch (error) {
    loadErrorMessage.value = error.message;
    if (hasLoadedData.value) {
      pageNo.value = loadedPageNo.value;
    }
  } finally {
    isLoading.value = false;
  }
}

function exportEvents() {
  const exportUrl = paymentEventApi.buildExportUrl(buildQuery(false));
  window.open(exportUrl, "_blank", "noopener,noreferrer");
}

async function republish(item) {
  const operator = republishOperator.value.trim();
  if (!operator) {
    actionErrorMessage.value = "请先输入重发操作人。";
    return;
  }
  activeEventNo.value = item.eventNo;
  actionErrorMessage.value = "";
  successMessage.value = "";
  try {
    const query = buildQuery();
    const result = await paymentEventApi.republish(item.eventNo, operator, query);
    items.value = result.items;
    total.value = result.total;
    selectedItem.value = result.items.find((candidate) => candidate.eventNo === item.eventNo)
      || result.items[0]
      || null;
    successMessage.value = `事件 ${item.eventNo} 已重新投递，请继续核对下游消费与业务处理结果。`;

    overviewRefreshErrorMessage.value = "";
    try {
      const overviewResult = await paymentEventApi.getOverview(query);
      overview.value = {
        ...overview.value,
        ...overviewResult
      };
    } catch (error) {
      overviewRefreshErrorMessage.value = error.message;
    }
  } catch (error) {
    actionErrorMessage.value = `事件 ${item.eventNo} 重发失败：${error.message}`;
  } finally {
    activeEventNo.value = "";
  }
}

function goToPage(nextPage) {
  if (nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  loadEvents();
}

onMounted(loadEvents);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付事件出站</h2>
        <p>统一管理 payment-core 向账户、清分、结算等下游系统投递的事件边界与重发动作</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" :disabled="isLoading || Boolean(activeEventNo)" @click="loadEvents">刷新</button>
        <button class="button primary" @click="exportEvents">导出事件</button>
      </div>
    </div>

    <section v-if="hasLoadedData" class="card-grid">
      <article class="card">
        <p class="card-title">事件总数</p>
        <p class="card-value">{{ metrics.total }}</p>
      </article>
      <article class="card">
        <p class="card-title">发布成功</p>
        <p class="card-value">{{ metrics.successTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">发布中</p>
        <p class="card-value">{{ metrics.pendingTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">发布失败</p>
        <p class="card-value">{{ metrics.failedTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">死信事件</p>
        <p class="card-value">{{ metrics.deadLetterTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">结果未知</p>
        <p class="card-value">{{ metrics.unknownTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">下游系统数</p>
        <p class="card-value">{{ metrics.downstreamCount }}</p>
      </article>
    </section>

    <section v-if="hasLoadedData" class="panel overview-panel">
      <div class="section-title">
        <h3>出站风险总览</h3>
        <span class="meta">最近发布时间：{{ metrics.latestPublishedAt }}</span>
      </div>
      <div class="overview-grid">
        <article class="overview-card danger">
          <p class="overview-title">失败或死信事件</p>
          <strong>{{ metrics.failedTotal }}</strong>
          <span>优先核对失败投递与死信积压，确认是否需要人工补偿或跨系统追单。</span>
          <RouterLink class="link-button" to="/payment-dead-letter-tasks">查看死信补偿任务</RouterLink>
        </article>
        <article class="overview-card warn">
          <p class="overview-title">到期待重试</p>
          <strong>{{ metrics.dueRetryCount }}</strong>
          <span>与自动任务候选口径一致：失败事件或可派发的待发布事件，且未设置未来重试时间。</span>
        </article>
        <article class="overview-card info">
          <p class="overview-title">支付成功事件</p>
          <strong>{{ metrics.paymentSuccessEventCount }}</strong>
          <span>重点关注支付成功事实向账务、清分、结算等下游传播是否完整落地。</span>
        </article>
      </div>
    </section>

    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">
        {{ hasLoadedData ? "支付事件出站数据刷新失败，当前展示上次成功快照：" : "支付事件出站数据加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="overviewRefreshErrorMessage" class="error-banner">
        事件列表已按重发结果更新，但总览刷新失败，当前总览仍为上次成功快照：{{ overviewRefreshErrorMessage }}
      </div>
      <div v-if="actionErrorMessage" class="error-banner">
        {{ actionErrorMessage }}
      </div>
      <div v-if="successMessage" class="success-banner">
        {{ successMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>重发操作人</label>
          <input v-model.trim="republishOperator" maxlength="64" placeholder="请输入操作人" />
        </div>
        <div class="field">
          <label>支付单号</label>
          <input v-model="filters.paymentOrderId" placeholder="请输入支付单号" />
        </div>
        <div class="field">
          <label>事件类型</label>
          <select v-model="filters.eventType">
            <option>全部</option>
            <option>PAYMENT_SUBMIT</option>
            <option>PAYMENT_PENDING</option>
            <option>PAYMENT_SUCCESS</option>
            <option>PAYMENT_CLOSED</option>
            <option>PAYMENT_EXPIRED_CLOSED</option>
            <option>REFUND_SUBMIT</option>
          </select>
        </div>
        <div class="field">
          <label>发布状态</label>
          <select v-model="filters.publishStatus">
            <option>全部</option>
            <option>PENDING</option>
            <option>SUCCESS</option>
            <option>FAILED_OR_DEAD_LETTER</option>
            <option>FAILED</option>
            <option>DEAD_LETTER</option>
            <option>UNKNOWN</option>
          </select>
        </div>
        <div class="field">
          <label>下游系统</label>
          <select v-model="filters.downstreamSystem">
            <option>全部</option>
            <option>gateway-access</option>
            <option>accounting-system</option>
            <option>clearing-system</option>
            <option>settlement-system</option>
            <option>payment-core-ops</option>
          </select>
        </div>
        <div class="field">
          <label>事件主题</label>
          <input v-model="filters.eventTopic" placeholder="如 payment.trade.succeeded.v1" />
        </div>
        <div class="field">
          <label>排序字段</label>
          <select v-model="filters.sortField">
            <option value="createdAt">创建时间</option>
            <option value="retryCount">重试次数</option>
            <option value="nextRetryAt">下次重试时间</option>
          </select>
        </div>
        <div class="field">
          <label>排序方向</label>
          <select v-model="filters.sortOrder">
            <option value="desc">倒序</option>
            <option value="asc">正序</option>
          </select>
        </div>
        <div class="toolbar-actions">
          <button class="button primary" :disabled="isLoading || Boolean(activeEventNo)" @click="applyFilters">查询</button>
          <button class="button secondary" :disabled="isLoading || Boolean(activeEventNo)" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">支付事件出站数据加载中...</div>
      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的支付事件出站快照</div>
      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的支付事件出站数据</div>

      <div v-else class="detail-layout">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>事件号</th>
                <th>事件类型</th>
                <th>支付单号</th>
                <th>业务单号</th>
                <th>事件主题</th>
                <th>下游系统</th>
                <th>发布状态</th>
                <th>HTTP 投递阶段</th>
                <th>重试次数</th>
                <th>最近发布时间</th>
                <th>下次重试时间</th>
                <th>最近重发操作人</th>
                <th>最近重发时间</th>
                <th>事件摘要</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.eventNo">
                <td>{{ item.eventNo }}</td>
                <td>{{ item.eventType }}</td>
                <td>{{ item.paymentOrderId }}</td>
                <td>{{ item.bizNo }}</td>
                <td>{{ item.eventTopic }}</td>
                <td>{{ item.downstreamSystem }}</td>
                <td><span :class="['badge', item.publishStatusType]">{{ item.publishStatus }}</span></td>
                <td>
                  <span :class="['badge', paymentSuccessHttpStageMeta(item).badgeType]">
                    {{ paymentSuccessHttpStageMeta(item).label }}
                  </span>
                </td>
                <td>{{ item.retryCount }}</td>
                <td>{{ item.lastPublishedAt || "-" }}</td>
                <td>{{ item.nextRetryAt || "-" }}</td>
                <td>{{ item.lastRepublishOperator || "-" }}</td>
                <td>{{ item.lastRepublishedAt || "-" }}</td>
                <td class="flow-summary-cell">{{ item.eventPayload }}</td>
                <td>{{ item.createdAt }}</td>
                <td>
                  <button class="link-button" @click="pickItem(item)">快照</button>
                  <button
                    class="link-button"
                    :disabled="Boolean(activeEventNo) || isSnapshotStale || !hasLoadedData || ['SUCCESS', 'UNKNOWN'].includes(item.publishStatus)"
                    @click="republish(item)"
                  >
                    {{ item.publishStatus === "SUCCESS" ? "已发布" : item.publishStatus === "UNKNOWN" ? "待核查" : "重发" }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <aside class="detail-side">
          <div v-if="selectedItem" class="detail-stack">
            <div class="section-title">
              <h3>事件快照</h3>
              <span class="meta">{{ selectedItem.eventNo }}</span>
            </div>
            <div class="detail-grid">
              <div class="detail-card"><span>事件类型</span><strong>{{ selectedItem.eventType }}</strong></div>
              <div class="detail-card"><span>支付单号</span><strong>{{ selectedItem.paymentOrderId }}</strong></div>
              <div class="detail-card"><span>业务单号</span><strong>{{ selectedItem.bizNo }}</strong></div>
              <div class="detail-card"><span>事件主题</span><strong>{{ selectedItem.eventTopic }}</strong></div>
              <div class="detail-card"><span>下游系统</span><strong>{{ selectedItem.downstreamSystem }}</strong></div>
              <div class="detail-card"><span>发布状态</span><strong>{{ selectedItem.publishStatus }}</strong></div>
              <div class="detail-card">
                <span>HTTP 投递阶段</span>
                <strong>{{ paymentSuccessHttpStageMeta(selectedItem).label }}</strong>
              </div>
              <div class="detail-card"><span>重试次数</span><strong>{{ selectedItem.retryCount }}</strong></div>
              <div class="detail-card"><span>最近重发操作人</span><strong>{{ selectedItem.lastRepublishOperator || "-" }}</strong></div>
              <div class="detail-card"><span>最近重发时间</span><strong>{{ selectedItem.lastRepublishedAt || "-" }}</strong></div>
              <div class="detail-card"><span>最近发布时间</span><strong>{{ selectedItem.lastPublishedAt || "—" }}</strong></div>
              <div class="detail-card detail-card-wide"><span>下次重试时间</span><strong>{{ selectedItem.nextRetryAt || "—" }}</strong></div>
            </div>
            <div class="ops-card">
              <div class="ops-title">排障建议</div>
              <div class="ops-row"><span>优先动作</span><span>{{ paymentEventRecommendedAction(selectedItem) }}</span></div>
              <div class="ops-row"><span>重点核对</span><span>发布状态、HTTP 投递阶段、重试次数、下次重试时间</span></div>
              <div class="ops-row"><span>典型场景</span><span>下游未消费、消息投递失败、补偿事件积压</span></div>
              <div class="ops-row"><span>当前风险面</span><span>{{ metrics.dueRetryCount }} 条到期待重试，{{ metrics.deadLetterTotal }} 条死信，{{ metrics.unknownTotal }} 条结果未知</span></div>
            </div>
            <div class="table-inline-actions">
              <RouterLink class="link-button" :to="paymentDetailRoute(selectedItem.paymentOrderId)">查看支付单</RouterLink>
              <RouterLink class="link-button" :to="paymentLogsRoute({ paymentOrderId: selectedItem.paymentOrderId })">查看支付日志</RouterLink>
              <RouterLink class="link-button" :to="paymentFlowsRoute({ paymentOrderId: selectedItem.paymentOrderId })">查看支付流水</RouterLink>
              <RouterLink class="link-button" to="/payment-dead-letter-tasks">查看死信补偿任务</RouterLink>
            </div>
          </div>
          <div v-else class="state-box">选择左侧支付事件后，可在这里查看事件快照与排障建议。</div>
        </aside>
      </div>
      <div v-if="hasLoadedData && total > pageSize" class="pager">
        <span>共 {{ total }} 条支付事件</span>
        <button class="button secondary" :disabled="pageNo === 1 || isLoading || Boolean(activeEventNo) || isSnapshotStale" @click="goToPage(pageNo - 1)">上一页</button>
        <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button class="button secondary" :disabled="pageNo >= Math.ceil(total / pageSize) || isLoading || Boolean(activeEventNo) || isSnapshotStale" @click="goToPage(pageNo + 1)">下一页</button>
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
  word-break: break-word;
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

@media (max-width: 900px) {
  .detail-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .table-wrap,
  .detail-side {
    min-width: 0;
    max-width: 100%;
  }
}
</style>
