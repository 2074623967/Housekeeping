<script setup>
import { computed, onMounted, ref } from "vue";
import { settlementApi } from "../api/client";

const items = ref([]);
const selectedItem = ref(null);
const isLoading = ref(true);
const errorMessage = ref("");
const total = ref(0);
const overview = ref({
  totalSettlementCount: 0,
  pendingAuditCount: 0,
  payoutPendingCount: 0,
  payingCount: 0,
  payoutSuccessCount: 0,
  totalNetSettleAmount: "¥0.00",
  totalDeductAmount: "¥0.00",
  totalDepositImpactAmount: "¥0.00",
  negativeNetSettleCount: 0
});
const pageNo = ref(1);
const pageSize = 20;
const filters = ref({
  settlementOrderId: "",
  workerKeyword: "",
  settlementStatus: "全部",
  payoutStatus: "全部"
});

const metrics = computed(() => ({
  total: overview.value.totalSettlementCount,
  pendingAuditTotal: overview.value.pendingAuditCount,
  payoutPendingTotal: overview.value.payoutPendingCount,
  payingTotal: overview.value.payingCount,
  payoutSuccessTotal: overview.value.payoutSuccessCount,
  netSettleTotal: overview.value.totalNetSettleAmount,
  deductTotal: overview.value.totalDeductAmount,
  depositImpactTotal: overview.value.totalDepositImpactAmount,
  negativeNetSettleCount: overview.value.negativeNetSettleCount
}));

function resetFilters() {
  filters.value = {
    settlementOrderId: "",
    workerKeyword: "",
    settlementStatus: "全部",
    payoutStatus: "全部"
  };
  pageNo.value = 1;
  loadWorkerSettlements();
}

function applyFilters() {
  pageNo.value = 1;
  loadWorkerSettlements();
}

function pickItem(item) {
  selectedItem.value = item;
}

async function loadWorkerSettlements() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const query = {
      settlementOrderId: filters.value.settlementOrderId,
      workerKeyword: filters.value.workerKeyword,
      settlementStatus: filters.value.settlementStatus,
      payoutStatus: filters.value.payoutStatus,
      pageNo: pageNo.value,
      pageSize
    };
    const [overviewResult, result] = await Promise.all([
      settlementApi.getWorkerOverview(query),
      settlementApi.getWorkerList(query)
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
  loadWorkerSettlements();
}

function exportWorkerSettlements() {
  const exportUrl = settlementApi.buildWorkerExportUrl({
    settlementOrderId: filters.value.settlementOrderId,
    workerKeyword: filters.value.workerKeyword,
    settlementStatus: filters.value.settlementStatus,
    payoutStatus: filters.value.payoutStatus
  });
  window.open(exportUrl, "_blank", "noopener,noreferrer");
}

onMounted(loadWorkerSettlements);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>服务者结算单</h2>
        <p>查看家政服务者的待结算、审核、出款状态</p>
      </div>
      <button class="button primary" @click="exportWorkerSettlements">导出结算单</button>
    </div>

    <section class="card-grid">
      <article class="card">
        <p class="card-title">结算单总数</p>
        <p class="card-value">{{ metrics.total }}</p>
      </article>
      <article class="card">
        <p class="card-title">待审核</p>
        <p class="card-value">{{ metrics.pendingAuditTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">待出款</p>
        <p class="card-value">{{ metrics.payoutPendingTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">出款中</p>
        <p class="card-value">{{ metrics.payingTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">出款成功</p>
        <p class="card-value">{{ metrics.payoutSuccessTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">实结金额合计</p>
        <p class="card-value">{{ metrics.netSettleTotal }}</p>
      </article>
    </section>

    <section class="panel overview-panel">
      <div class="section-title">
        <h3>结算风险总览</h3>
        <span class="meta">用于支付侧快速判断服务者出款压力与欠款风险</span>
      </div>
      <div class="overview-grid">
        <article class="overview-card warn">
          <p class="overview-title">扣减金额合计</p>
          <strong>{{ metrics.deductTotal }}</strong>
          <span>聚焦投诉赔付、罚款、欠款冲抵等扣减因素对结算净额的影响。</span>
        </article>
        <article class="overview-card info">
          <p class="overview-title">保证金影响合计</p>
          <strong>{{ metrics.depositImpactTotal }}</strong>
          <span>用于识别保证金抵扣、冻结释放对服务者本期结算的联动影响。</span>
        </article>
        <article class="overview-card danger">
          <p class="overview-title">净额为负结算单</p>
          <strong>{{ metrics.negativeNetSettleCount }}</strong>
          <span>重点关注欠款承接、后续调账和保证金补扣是否需要跨系统继续收口。</span>
        </article>
      </div>
    </section>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        服务者结算数据加载失败：{{ errorMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>结算单号</label>
          <input v-model="filters.settlementOrderId" placeholder="请输入结算单号" />
        </div>
        <div class="field">
          <label>服务者 ID</label>
          <input v-model="filters.workerKeyword" placeholder="请输入服务者名称" />
        </div>
        <div class="field">
          <label>出款状态</label>
          <select v-model="filters.payoutStatus">
            <option>全部</option>
            <option>待出款</option>
            <option>出款中</option>
            <option>出款成功</option>
          </select>
        </div>
        <div class="field">
          <label>结算状态</label>
          <select v-model="filters.settlementStatus">
            <option>全部</option>
            <option>待审核</option>
            <option>待出款</option>
            <option>出款成功</option>
          </select>
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="applyFilters">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">服务者结算数据加载中...</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的服务者结算数据</div>

      <div v-else class="detail-layout">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>结算单号</th>
                <th>服务者</th>
                <th>账期</th>
                <th>应结金额</th>
                <th>已扣减金额</th>
                <th>实结金额</th>
                <th>保证金影响</th>
                <th>结算状态</th>
                <th>出款状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.settlementOrderId">
                <td>{{ item.settlementOrderId }}</td>
                <td>{{ item.workerName }}</td>
                <td>{{ item.period }}</td>
                <td>{{ item.amountShouldSettle }}</td>
                <td>{{ item.deductAmount }}</td>
                <td>{{ item.amountNetSettle }}</td>
                <td>{{ item.depositImpactAmount }}</td>
                <td><span :class="['badge', item.statusType]">{{ item.status }}</span></td>
                <td><span :class="['badge', item.payoutStatusType]">{{ item.payoutStatus }}</span></td>
                <td>
                  <div class="list-actions">
                    <button class="link-button" @click="pickItem(item)">查看快照</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <aside class="detail-side">
          <div v-if="selectedItem" class="detail-stack">
            <div class="section-title">
              <h3>结算快照</h3>
              <span class="meta">{{ selectedItem.settlementOrderId }}</span>
            </div>
            <div class="detail-grid">
              <div class="detail-card"><span>服务者</span><strong>{{ selectedItem.workerName }}</strong></div>
              <div class="detail-card"><span>账期</span><strong>{{ selectedItem.period }}</strong></div>
              <div class="detail-card"><span>应结金额</span><strong>{{ selectedItem.amountShouldSettle }}</strong></div>
              <div class="detail-card"><span>已扣减金额</span><strong>{{ selectedItem.deductAmount }}</strong></div>
              <div class="detail-card"><span>实结金额</span><strong>{{ selectedItem.amountNetSettle }}</strong></div>
              <div class="detail-card"><span>保证金影响</span><strong>{{ selectedItem.depositImpactAmount }}</strong></div>
              <div class="detail-card"><span>结算状态</span><strong>{{ selectedItem.status }}</strong></div>
              <div class="detail-card"><span>出款状态</span><strong>{{ selectedItem.payoutStatus }}</strong></div>
            </div>
            <div class="ops-card">
              <div class="ops-title">说明</div>
              <div class="ops-row"><span>当前定位</span><span>payment-core 只保留轻量查询入口</span></div>
              <div class="ops-row"><span>后续归属</span><span>完整审批、出款、核销作业迁入 settlement-system</span></div>
              <div class="ops-row"><span>当前用途</span><span>支付侧快速核对服务者结算状态和金额影响</span></div>
              <div class="ops-row"><span>风险提示</span><span>{{ metrics.negativeNetSettleCount }} 笔净额为负，需关注欠款和保证金联动</span></div>
            </div>
          </div>
          <div v-else class="state-box">选择左侧结算单后，可在这里查看结算快照与定位说明。</div>
        </aside>
      </div>
      <div v-if="total > pageSize" class="pager">
        <span>共 {{ total }} 条服务者结算单</span>
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

.overview-card.info {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.overview-card.danger {
  background: #fff1f2;
  border-color: #fecdd3;
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
