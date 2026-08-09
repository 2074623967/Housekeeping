<script setup>
import { computed, onMounted, ref } from "vue";
import { cashierSessionApi } from "../api/client";

const items = ref([]);
const selectedItem = ref(null);
const isLoading = ref(true);
const errorMessage = ref("");
const total = ref(0);
const overview = ref({
  totalSessionCount: 0,
  expiredSessionCount: 0,
  successSessionCount: 0,
  payingSessionCount: 0,
  pendingSessionCount: 0,
  distinctTerminalCount: 0,
  expiringSoonCount: 0,
  totalAmount: "¥0.00"
});
const pageNo = ref(1);
const pageSize = 20;
const filters = ref({
  sessionNo: "",
  paymentOrderId: "",
  orderNo: "",
  customerName: "",
  terminal: "全部",
  sessionStatus: "全部",
  sortField: "createdAt",
  sortOrder: "desc"
});

const metrics = computed(() => ({
  total: overview.value.totalSessionCount,
  expiredTotal: overview.value.expiredSessionCount,
  successTotal: overview.value.successSessionCount,
  payingTotal: overview.value.payingSessionCount,
  pendingTotal: overview.value.pendingSessionCount,
  terminalCount: overview.value.distinctTerminalCount,
  expiringSoonCount: overview.value.expiringSoonCount,
  totalAmount: overview.value.totalAmount
}));

function resetFilters() {
  filters.value = {
    sessionNo: "",
    paymentOrderId: "",
    orderNo: "",
    customerName: "",
    terminal: "全部",
    sessionStatus: "全部",
    sortField: "createdAt",
    sortOrder: "desc"
  };
  pageNo.value = 1;
  loadCashierSessions();
}

function applyFilters() {
  pageNo.value = 1;
  loadCashierSessions();
}

function pickItem(item) {
  selectedItem.value = item;
}

async function loadCashierSessions() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const query = {
      sessionNo: filters.value.sessionNo,
      paymentOrderId: filters.value.paymentOrderId,
      orderNo: filters.value.orderNo,
      customerName: filters.value.customerName,
      terminal: filters.value.terminal,
      sessionStatus: filters.value.sessionStatus,
      sortField: filters.value.sortField,
      sortOrder: filters.value.sortOrder,
      pageNo: pageNo.value,
      pageSize
    };
    const [overviewResult, result] = await Promise.all([
      cashierSessionApi.getOverview(query),
      cashierSessionApi.getList(query)
    ]);
    overview.value = {
      ...overview.value,
      ...overviewResult
    };
    items.value = result.items;
    total.value = result.total;
    selectedItem.value = result.items[0] || null;
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

function exportSessions() {
  const exportUrl = cashierSessionApi.buildExportUrl({
    sessionNo: filters.value.sessionNo,
    paymentOrderId: filters.value.paymentOrderId,
    orderNo: filters.value.orderNo,
    customerName: filters.value.customerName,
    terminal: filters.value.terminal,
    sessionStatus: filters.value.sessionStatus,
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
  loadCashierSessions();
}

onMounted(loadCashierSessions);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>收银台会话管理</h2>
        <p>查看预付单会话、终端来源、支付关联和失效状态，快速定位收银台异常</p>
      </div>
      <button class="button primary" @click="exportSessions">导出会话</button>
    </div>

    <section class="card-grid">
      <article class="card">
        <p class="card-title">会话总数</p>
        <p class="card-value">{{ metrics.total }}</p>
      </article>
      <article class="card">
        <p class="card-title">已失效会话</p>
        <p class="card-value">{{ metrics.expiredTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">成功 / 已完成</p>
        <p class="card-value">{{ metrics.successTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">支付中</p>
        <p class="card-value">{{ metrics.payingTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">待支付</p>
        <p class="card-value">{{ metrics.pendingTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">涉及终端数</p>
        <p class="card-value">{{ metrics.terminalCount }}</p>
      </article>
    </section>

    <section class="panel overview-panel">
      <div class="section-title">
        <h3>会话风险总览</h3>
        <span class="meta">用于识别即将失效、金额聚集和终端分布异常</span>
      </div>
      <div class="overview-grid">
        <article class="overview-card warn">
          <p class="overview-title">即将失效会话</p>
          <strong>{{ metrics.expiringSoonCount }}</strong>
          <span>15 分钟内即将失效且未成功的会话，适合优先排查用户跳失或终端兼容问题。</span>
        </article>
        <article class="overview-card danger">
          <p class="overview-title">已失效会话</p>
          <strong>{{ metrics.expiredTotal }}</strong>
          <span>重点联查预付单、支付单和终端来源，确认是否存在重复拉起和超时未支付问题。</span>
        </article>
        <article class="overview-card info">
          <p class="overview-title">会话金额合计</p>
          <strong>{{ metrics.totalAmount }}</strong>
          <span>帮助运营快速识别当前筛选条件下的会话金额体量和终端流量压力。</span>
        </article>
      </div>
    </section>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        收银台会话加载失败：{{ errorMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>会话号</label>
          <input v-model="filters.sessionNo" placeholder="请输入预付单/会话号" />
        </div>
        <div class="field">
          <label>订单号</label>
          <input v-model="filters.orderNo" placeholder="请输入订单号" />
        </div>
        <div class="field">
          <label>支付单号</label>
          <input v-model="filters.paymentOrderId" placeholder="请输入支付单号" />
        </div>
        <div class="field">
          <label>客户名称</label>
          <input v-model="filters.customerName" placeholder="请输入客户名称" />
        </div>
        <div class="field">
          <label>终端场景</label>
          <select v-model="filters.terminal">
            <option>全部</option>
            <option>H5</option>
            <option>PC</option>
            <option>APP</option>
            <option>小程序</option>
          </select>
        </div>
        <div class="field">
          <label>会话状态</label>
          <select v-model="filters.sessionStatus">
            <option>全部</option>
            <option>待支付</option>
            <option>支付中</option>
            <option>支付成功</option>
            <option>已完成</option>
            <option>已失效</option>
          </select>
        </div>
        <div class="field">
          <label>排序字段</label>
          <select v-model="filters.sortField">
            <option value="createdAt">创建时间</option>
            <option value="expiresAt">失效时间</option>
            <option value="amount">会话金额</option>
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
          <input value="当前已接入后端筛选和排序，便于定位终端会话异常" disabled />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="applyFilters">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">收银台会话加载中...</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的收银台会话</div>

      <div v-else class="detail-layout">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>会话号</th>
                <th>预付单号</th>
                <th>支付单号</th>
                <th>订单号</th>
                <th>客户</th>
                <th>终端</th>
                <th>金额</th>
                <th>会话状态</th>
                <th>创建时间</th>
                <th>失效时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.sessionNo">
                <td>{{ item.sessionNo }}</td>
                <td>{{ item.prepayOrderNo }}</td>
                <td>{{ item.paymentOrderId }}</td>
                <td>{{ item.orderNo }}</td>
                <td>{{ item.customerName }}</td>
                <td>{{ item.terminal }}</td>
                <td>{{ item.amount }}</td>
                <td><span :class="['badge', item.sessionStatusType]">{{ item.sessionStatus }}</span></td>
                <td>{{ item.createdAt }}</td>
                <td>{{ item.expiresAt }}</td>
                <td><button class="link-button" @click="pickItem(item)">查看详情</button></td>
              </tr>
            </tbody>
          </table>
        </div>

        <aside class="detail-side">
          <div v-if="selectedItem" class="detail-stack">
            <div class="section-title">
              <h3>会话详情</h3>
              <span class="meta">{{ selectedItem.sessionNo }}</span>
            </div>
            <div class="detail-grid">
              <div class="detail-card"><span>预付单号</span><strong>{{ selectedItem.prepayOrderNo }}</strong></div>
              <div class="detail-card"><span>支付单号</span><strong>{{ selectedItem.paymentOrderId }}</strong></div>
              <div class="detail-card"><span>订单号</span><strong>{{ selectedItem.orderNo }}</strong></div>
              <div class="detail-card"><span>客户名称</span><strong>{{ selectedItem.customerName }}</strong></div>
              <div class="detail-card"><span>终端</span><strong>{{ selectedItem.terminal }}</strong></div>
              <div class="detail-card"><span>会话状态</span><strong>{{ selectedItem.sessionStatus }}</strong></div>
              <div class="detail-card"><span>会话金额</span><strong>{{ selectedItem.amount }}</strong></div>
              <div class="detail-card"><span>创建时间</span><strong>{{ selectedItem.createdAt }}</strong></div>
              <div class="detail-card detail-card-wide"><span>失效时间</span><strong>{{ selectedItem.expiresAt }}</strong></div>
            </div>
            <div class="ops-card">
              <div class="ops-title">排查建议</div>
              <div class="ops-row"><span>优先联查</span><span>订单中心 / 支付单详情 / 当前收银台</span></div>
              <div class="ops-row"><span>典型问题</span><span>会话失效、终端跳转失败、支付中未收口</span></div>
              <div class="ops-row"><span>重点核对</span><span>终端、失效时间、支付单状态是否一致</span></div>
              <div class="ops-row"><span>当前风险面</span><span>{{ metrics.expiringSoonCount }} 条即将失效，{{ metrics.expiredTotal }} 条已失效</span></div>
            </div>
            <div class="table-inline-actions">
              <RouterLink class="link-button" :to="`/orders?orderNo=${selectedItem.orderNo}`">查看订单</RouterLink>
              <RouterLink class="link-button" :to="`/payments/${selectedItem.paymentOrderId}`">查看支付单</RouterLink>
              <RouterLink class="link-button" :to="`/payment-flows?paymentOrderId=${selectedItem.paymentOrderId}`">查看支付流水</RouterLink>
              <a
                class="link-button"
                :href="`/cashier/${selectedItem.prepayOrderNo}`"
                target="_blank"
                rel="noopener noreferrer"
              >
                打开收银台
              </a>
            </div>
          </div>
          <div v-else class="state-box">选择左侧会话后，可在这里查看详情与排查建议。</div>
        </aside>
      </div>
      <div v-if="total > pageSize" class="pager">
        <span>共 {{ total }} 条会话</span>
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

.overview-card.warn {
  background: #fff7ed;
  border-color: #fed7aa;
}

.overview-card.danger {
  background: #fff1f2;
  border-color: #fecdd3;
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
