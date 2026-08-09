<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { paymentLogApi } from "../api/client";

const route = useRoute();
const items = ref([]);
const selectedItem = ref(null);
const isLoading = ref(true);
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
const pageSize = 20;
const filters = ref({
  paymentOrderId: route.query.paymentOrderId || "",
  orderNo: route.query.orderNo || "",
  processStage: route.query.processStage || "全部",
  logLevel: route.query.logLevel || "全部",
  source: route.query.source || "",
  keyword: route.query.keyword || "",
  sortField: route.query.sortField || "createdAt",
  sortOrder: route.query.sortOrder || "desc"
});

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

function resetFilters() {
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
  pageNo.value = 1;
  loadPaymentLogs();
}

function pickItem(item) {
  selectedItem.value = item;
}

async function loadPaymentLogs() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const query = {
      paymentOrderId: filters.value.paymentOrderId,
      orderNo: filters.value.orderNo,
      processStage: filters.value.processStage,
      logLevel: filters.value.logLevel,
      source: filters.value.source,
      keyword: filters.value.keyword,
      sortField: filters.value.sortField,
      sortOrder: filters.value.sortOrder,
      pageNo: pageNo.value,
      pageSize
    };
    const [overviewResult, result] = await Promise.all([
      paymentLogApi.getOverview(query),
      paymentLogApi.getList(query)
    ]);
    overview.value = {
      ...overview.value,
      ...overviewResult
    };
    total.value = result.total;
    items.value = result.items;
    selectedItem.value = result.items[0] || null;
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

function exportLogs() {
  const exportUrl = paymentLogApi.buildExportUrl({
    paymentOrderId: filters.value.paymentOrderId,
    orderNo: filters.value.orderNo,
    processStage: filters.value.processStage,
    logLevel: filters.value.logLevel,
    source: filters.value.source,
    keyword: filters.value.keyword,
    sortField: filters.value.sortField,
    sortOrder: filters.value.sortOrder
  });
  window.open(exportUrl, "_blank", "noopener,noreferrer");
}

function goToPage(nextPage) {
  if (nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  loadPaymentLogs();
}

onMounted(loadPaymentLogs);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付处理日志</h2>
        <p>按处理阶段查看支付提交、路由、回调和业务事件日志，支撑异常定位与测试回归</p>
      </div>
      <button class="button primary" @click="exportLogs">导出日志</button>
    </div>

    <section class="card-grid">
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

    <section class="panel overview-panel">
      <div class="section-title">
        <h3>风险总览</h3>
        <span class="meta">最近日志时间：{{ metrics.latestLogAt }}</span>
      </div>
      <div class="overview-grid">
        <article class="overview-card danger">
          <p class="overview-title">回调异常优先排查</p>
          <strong>{{ metrics.callbackErrorCount }}</strong>
          <span>聚焦渠道回调阶段 ERROR 日志，优先核对签名、幂等和状态机推进。</span>
        </article>
        <article class="overview-card warn">
          <p class="overview-title">业务事件告警</p>
          <strong>{{ metrics.eventWarnCount }}</strong>
          <span>重点检查 `PAYMENT_CLOSED` 等业务事件是否与真实订单状态一致。</span>
        </article>
        <article class="overview-card info">
          <p class="overview-title">关键字命中</p>
          <strong>{{ metrics.callbackKeywordCount }}</strong>
          <span>当前筛选条件下命中“回调”关键字的日志量，可快速圈定通知链路问题面。</span>
        </article>
      </div>
    </section>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        支付处理日志加载失败：{{ errorMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>支付单号</label>
          <input v-model="filters.paymentOrderId" placeholder="请输入支付单号" />
        </div>
        <div class="field">
          <label>订单号</label>
          <input v-model="filters.orderNo" placeholder="请输入订单号" />
        </div>
        <div class="field">
          <label>处理阶段</label>
          <select v-model="filters.processStage">
            <option>全部</option>
            <option>支付提交</option>
            <option>支付路由</option>
            <option>渠道回调</option>
            <option>业务事件</option>
          </select>
        </div>
        <div class="field">
          <label>日志级别</label>
          <select v-model="filters.logLevel">
            <option>全部</option>
            <option>INFO</option>
            <option>WARN</option>
            <option>ERROR</option>
          </select>
        </div>
        <div class="field">
          <label>日志来源</label>
          <input v-model="filters.source" placeholder="如 wx_h5 / payment-core" />
        </div>
        <div class="field">
          <label>日志关键字</label>
          <input v-model="filters.keyword" placeholder="如 回调 / 路由 / SUCCESS" />
        </div>
        <div class="field">
          <label>排序字段</label>
          <select v-model="filters.sortField">
            <option value="createdAt">创建时间</option>
            <option value="logLevel">日志级别</option>
            <option value="processStage">处理阶段</option>
          </select>
        </div>
        <div class="field">
          <label>排序方向</label>
          <select v-model="filters.sortOrder">
            <option value="desc">倒序</option>
            <option value="asc">正序</option>
          </select>
        </div>
        <div class="field">
          <label>当前说明</label>
          <input value="已支持订单号、来源、关键字检索与排序，生产环境需接入检索与告警平台" disabled />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="applyFilters">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">支付处理日志加载中...</div>

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
                  <RouterLink class="link-button" :to="`/payments/${item.paymentOrderId}`">
                    {{ item.paymentOrderId }}
                  </RouterLink>
                </td>
                <td>{{ item.orderNo }}</td>
                <td>{{ item.processStage }}</td>
                <td><span :class="['badge', item.logLevelType]">{{ item.logLevel }}</span></td>
                <td>{{ item.source }}</td>
                <td class="flow-summary-cell">{{ item.message }}</td>
                <td>{{ item.createdAt }}</td>
                <td><button class="link-button" @click="pickItem(item)">查看详情</button></td>
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
              <RouterLink class="link-button" :to="`/payments/${selectedItem.paymentOrderId}`">查看支付单</RouterLink>
              <RouterLink class="link-button" :to="`/payment-flows?paymentOrderId=${selectedItem.paymentOrderId}`">查看支付流水</RouterLink>
              <RouterLink class="link-button" :to="`/payment-requests?paymentOrderId=${selectedItem.paymentOrderId}`">查看支付请求</RouterLink>
              <RouterLink class="link-button" :to="`/payment-events?paymentOrderId=${selectedItem.paymentOrderId}`">查看支付事件</RouterLink>
            </div>
          </div>
          <div v-else class="state-box">选择左侧日志后，可在这里查看详情与排障建议。</div>
        </aside>
      </div>
      <div v-if="total > pageSize" class="pager">
        <span>共 {{ total }} 条日志</span>
        <button class="button secondary" :disabled="pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
        <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button class="button secondary" :disabled="pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
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
