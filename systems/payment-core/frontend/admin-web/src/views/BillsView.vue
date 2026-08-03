<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { billApi } from "../api/client";

const route = useRoute();
const router = useRouter();
const items = ref([]);
const selectedItem = ref(null);
const isLoading = ref(true);
const errorMessage = ref("");
const total = ref(0);
const overview = ref({
  totalBillCount: 0,
  paidBillCount: 0,
  unpaidBillCount: 0,
  partialPaidBillCount: 0,
  overdueBillCount: 0,
  totalBillAmount: "¥0.00",
  totalPaidAmount: "¥0.00",
  totalUnpaidAmount: "¥0.00"
});
const pageNo = ref(1);
const pageSize = 20;
const filters = ref({
  billNo: route.query.billNo || "",
  orderNo: route.query.orderNo || "",
  customerName: route.query.customerName || "",
  billStatus: route.query.billStatus || "全部",
  sortField: route.query.sortField || "createdAt",
  sortOrder: route.query.sortOrder || "desc"
});

const metrics = computed(() => ({
  total: overview.value.totalBillCount,
  paidTotal: overview.value.paidBillCount,
  unpaidTotal: overview.value.unpaidBillCount,
  partialPaidTotal: overview.value.partialPaidBillCount,
  overdueTotal: overview.value.overdueBillCount,
  unpaidAmountTotal: overview.value.totalUnpaidAmount,
  billAmountTotal: overview.value.totalBillAmount,
  paidAmountTotal: overview.value.totalPaidAmount
}));

function resetFilters() {
  filters.value = {
    billNo: "",
    orderNo: "",
    customerName: "",
    billStatus: "全部",
    sortField: "createdAt",
    sortOrder: "desc"
  };
  pageNo.value = 1;
  loadBills();
}

function applyFilters() {
  pageNo.value = 1;
  loadBills();
}

async function loadBills() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const query = {
      billNo: filters.value.billNo,
      orderNo: filters.value.orderNo,
      customerName: filters.value.customerName,
      billStatus: filters.value.billStatus,
      sortField: filters.value.sortField,
      sortOrder: filters.value.sortOrder,
      pageNo: pageNo.value,
      pageSize
    };
    const [overviewResult, result] = await Promise.all([
      billApi.getOverview(query),
      billApi.getList(query)
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

function goToPage(nextPage) {
  if (nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  loadBills();
}

function pickItem(item) {
  selectedItem.value = item;
}

function exportBills() {
  const exportUrl = billApi.buildExportUrl({
    billNo: filters.value.billNo,
    orderNo: filters.value.orderNo,
    customerName: filters.value.customerName,
    billStatus: filters.value.billStatus,
    sortField: filters.value.sortField,
    sortOrder: filters.value.sortOrder
  });
  window.open(exportUrl, "_blank", "noopener,noreferrer");
}

function openOrders(item) {
  router.push(`/orders?orderNo=${item.orderNo}`);
}

function openPayments(item) {
  router.push(`/payments?orderNo=${item.orderNo}`);
}

onMounted(loadBills);

watch(
  () => [route.query.billNo, route.query.orderNo, route.query.billStatus],
  ([billNo, orderNo, billStatus]) => {
    filters.value = {
      billNo: billNo || "",
      orderNo: orderNo || "",
      customerName: route.query.customerName || "",
      billStatus: billStatus || "全部",
      sortField: route.query.sortField || "createdAt",
      sortOrder: route.query.sortOrder || "desc"
    };
    pageNo.value = 1;
    loadBills();
  }
);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>账单中心</h2>
        <p>按交易账单视角查看订单应收、已收和待收进展，为支付排查提供中间桥梁</p>
      </div>
      <button class="button primary" @click="exportBills">导出账单</button>
    </div>

    <section class="card-grid">
      <article class="card">
        <p class="card-title">账单总数</p>
        <p class="card-value">{{ metrics.total }}</p>
      </article>
      <article class="card">
        <p class="card-title">已支付 / 已结清</p>
        <p class="card-value">{{ metrics.paidTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">待支付账单</p>
        <p class="card-value">{{ metrics.unpaidTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">部分支付账单</p>
        <p class="card-value">{{ metrics.partialPaidTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">待支付金额合计</p>
        <p class="card-value">{{ metrics.unpaidAmountTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">账单应收合计</p>
        <p class="card-value">{{ metrics.billAmountTotal }}</p>
      </article>
    </section>

    <section class="panel overview-panel">
      <div class="section-title">
        <h3>账单风险总览</h3>
        <span class="meta">用于识别逾期账单、部分支付和待收金额聚集风险</span>
      </div>
      <div class="overview-grid">
        <article class="overview-card danger">
          <p class="overview-title">逾期未结清账单</p>
          <strong>{{ metrics.overdueTotal }}</strong>
          <span>优先联查订单履约状态、支付单状态和催缴动作，避免应收长期挂账。</span>
        </article>
        <article class="overview-card.warn">
          <p class="overview-title">已付金额合计</p>
          <strong>{{ metrics.paidAmountTotal }}</strong>
          <span>帮助运营快速判断当前筛选条件下的回款进度和账单收口体量。</span>
        </article>
        <article class="overview-card.info">
          <p class="overview-title">待收金额合计</p>
          <strong>{{ metrics.unpaidAmountTotal }}</strong>
          <span>重点关注尾款催缴、部分支付补收和超期账单的持续增长风险。</span>
        </article>
      </div>
    </section>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        账单数据加载失败：{{ errorMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>账单号</label>
          <input v-model="filters.billNo" placeholder="请输入账单号" />
        </div>
        <div class="field">
          <label>订单号</label>
          <input v-model="filters.orderNo" placeholder="请输入订单号" />
        </div>
        <div class="field">
          <label>客户名称</label>
          <input v-model="filters.customerName" placeholder="请输入客户名称" />
        </div>
        <div class="field">
          <label>账单状态</label>
          <select v-model="filters.billStatus">
            <option>全部</option>
            <option>待支付</option>
            <option>部分支付</option>
            <option>已结清</option>
            <option>已支付</option>
          </select>
        </div>
        <div class="field">
          <label>排序字段</label>
          <select v-model="filters.sortField">
            <option value="createdAt">创建时间</option>
            <option value="dueAt">到期时间</option>
            <option value="billAmount">账单应收</option>
            <option value="unpaidAmount">待支付金额</option>
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
          <input value="当前已接入后端筛选和排序，不承接账务会计口径" disabled />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="applyFilters">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">账单数据加载中...</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的账单数据</div>

      <div v-else class="detail-layout">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>账单号</th>
                <th>订单号</th>
                <th>客户</th>
                <th>账单应收</th>
                <th>已支付</th>
                <th>待支付</th>
                <th>账单状态</th>
                <th>到期时间</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.billNo">
                <td>{{ item.billNo }}</td>
                <td>{{ item.orderNo }}</td>
                <td>{{ item.customerName }}</td>
                <td>{{ item.billAmount }}</td>
                <td>{{ item.paidAmount }}</td>
                <td>{{ item.unpaidAmount }}</td>
                <td><span :class="['badge', item.billStatusType]">{{ item.billStatus }}</span></td>
                <td>{{ item.dueAt }}</td>
                <td>{{ item.createdAt }}</td>
                <td>
                  <button class="link-button" @click="pickItem(item)">查看快照</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <aside class="detail-side">
          <div v-if="selectedItem" class="detail-stack">
            <div class="section-title">
              <h3>账单快照</h3>
              <span class="meta">{{ selectedItem.billNo }}</span>
            </div>
            <div class="detail-grid">
              <div class="detail-card"><span>订单号</span><strong>{{ selectedItem.orderNo }}</strong></div>
              <div class="detail-card"><span>客户名称</span><strong>{{ selectedItem.customerName }}</strong></div>
              <div class="detail-card"><span>账单应收</span><strong>{{ selectedItem.billAmount }}</strong></div>
              <div class="detail-card"><span>已支付</span><strong>{{ selectedItem.paidAmount }}</strong></div>
              <div class="detail-card"><span>待支付</span><strong>{{ selectedItem.unpaidAmount }}</strong></div>
              <div class="detail-card"><span>账单状态</span><strong>{{ selectedItem.billStatus }}</strong></div>
              <div class="detail-card"><span>到期时间</span><strong>{{ selectedItem.dueAt }}</strong></div>
              <div class="detail-card"><span>创建时间</span><strong>{{ selectedItem.createdAt }}</strong></div>
            </div>
            <div class="ops-card">
              <div class="ops-title">运营建议</div>
              <div class="ops-row"><span>优先联查</span><span>订单中心 / 支付单管理 / 支付记录</span></div>
              <div class="ops-row"><span>重点核对</span><span>账单状态、待支付金额、到期时间</span></div>
              <div class="ops-row"><span>典型场景</span><span>账单部分支付、订单支付未收口、尾款催缴</span></div>
              <div class="ops-row"><span>当前风险面</span><span>{{ metrics.overdueTotal }} 笔逾期，{{ metrics.partialPaidTotal }} 笔部分支付</span></div>
            </div>
            <div class="table-inline-actions">
              <button class="link-button" @click="openOrders(selectedItem)">查看订单</button>
              <button class="link-button" @click="openPayments(selectedItem)">查看支付单</button>
            </div>
          </div>
          <div v-else class="state-box">选择左侧账单后，可在这里查看账单快照与运营建议。</div>
        </aside>
      </div>
      <div v-if="total > pageSize" class="pager">
        <span>共 {{ total }} 条账单</span>
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
