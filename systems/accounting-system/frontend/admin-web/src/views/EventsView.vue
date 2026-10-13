<script setup>
import { computed, onMounted, ref } from "vue";
import { eventApi } from "../api/client";

const rows = ref([]);
const selectedRow = ref(null);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const loading = ref(false);
const message = ref("");
const filters = ref({ eventType: "", bizNo: "", paymentOrderId: "" });

const dashboardMetrics = computed(() => ({
  total: total.value,
  successTotal: rows.value.filter((item) => item.eventStatus === "处理成功").length,
  failedTotal: rows.value.filter((item) => item.eventStatus && item.eventStatus !== "处理成功").length,
  paymentSuccessTotal: rows.value.filter((item) => item.eventType === "PAYMENT_SUCCESS").length,
  settlementCompensationTotal: rows.value.filter((item) => item.eventType === "SETTLEMENT_COMPENSATION").length
}));

const parsedPayload = computed(() => {
  if (!selectedRow.value?.payload) {
    return null;
  }
  try {
    return JSON.parse(selectedRow.value.payload);
  } catch (error) {
    return { rawPayload: selectedRow.value.payload };
  }
});

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));

async function loadRows(targetPage = pageNo.value) {
  loading.value = true;
  message.value = "";
  try {
    const result = await eventApi.getList({ ...filters.value, pageNo: targetPage, pageSize });
    rows.value = result.items;
    total.value = result.total;
    pageNo.value = result.pageNo || targetPage;
    selectedRow.value = result.items[0] || null;
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

function pickRow(row) {
  selectedRow.value = row;
}

function resetFilters() {
  pageNo.value = 1;
  filters.value = { eventType: "", bizNo: "", paymentOrderId: "" };
  loadRows();
}

function searchRows() {
  pageNo.value = 1;
  loadRows();
}

function changePage(targetPage) {
  if (loading.value || targetPage < 1 || targetPage > totalPages.value || targetPage === pageNo.value) {
    return;
  }
  loadRows(targetPage);
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>账户事件</h2>
        <p>支付成功、清分结果与补偿事件核对工作台；事件消费由受控服务端发起</p>
      </div>
    </div>

    <section class="card-grid metric-grid">
      <article class="card">
        <p class="card-title">事件总数</p>
        <p class="card-value">{{ dashboardMetrics.total }}</p>
      </article>
      <article class="card">
        <p class="card-title">处理成功</p>
        <p class="card-value">{{ dashboardMetrics.successTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">失败 / 待处理</p>
        <p class="card-value">{{ dashboardMetrics.failedTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">支付成功事件</p>
        <p class="card-value">{{ dashboardMetrics.paymentSuccessTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">结算补偿事件</p>
        <p class="card-value">{{ dashboardMetrics.settlementCompensationTotal }}</p>
      </article>
    </section>

    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>事件类型</label><input v-model="filters.eventType" /></div>
        <div class="field"><label>业务单号</label><input v-model="filters.bizNo" /></div>
        <div class="field"><label>支付单号</label><input v-model="filters.paymentOrderId" /></div>
        <div class="toolbar-actions">
          <button class="button primary" @click="searchRows">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <p class="state-box">此页仅核对已消费事件。支付、清分和退款事件由持有服务间签名密钥的上游发送；结算补偿须先接入可信身份、审批及受控服务端签名，不在浏览器中操作。</p>

      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">事件加载中...</div>
      <div v-else class="split-panels">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>事件号</th>
                <th>类型</th>
                <th>业务单号</th>
                <th>状态</th>
                <th>摘要</th>
                <th>时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.eventNo">
                <td>{{ row.eventNo }}</td>
                <td>{{ row.eventType }}</td>
                <td>{{ row.bizNo }}</td>
                <td><span class="badge" :class="row.statusType">{{ row.eventStatus }}</span></td>
                <td>{{ row.summary }}</td>
                <td>{{ row.createdAt }}</td>
                <td>
                  <button class="button secondary" @click="pickRow(row)">查看链路</button>
                </td>
              </tr>
              <tr v-if="rows.length === 0">
                <td colspan="7" class="empty-cell">暂无账户事件数据。</td>
              </tr>
            </tbody>
          </table>
          <div class="pagination" aria-label="账户事件分页">
            <span class="pagination-summary">第 {{ pageNo }} / {{ totalPages }} 页，共 {{ total }} 条</span>
            <div class="pagination-actions">
              <button class="button secondary" :disabled="loading || pageNo <= 1" @click="changePage(pageNo - 1)">上一页</button>
              <button class="button secondary" :disabled="loading || pageNo >= totalPages" @click="changePage(pageNo + 1)">下一页</button>
            </div>
          </div>
        </div>

        <div class="panel" style="margin-bottom:0">
          <div class="section-head">
            <h3 style="margin:0">事件链路</h3>
          </div>
          <template v-if="selectedRow">
            <div class="detail-grid">
              <div class="detail-card"><div class="detail-label">事件号</div><div class="detail-value">{{ selectedRow.eventNo }}</div></div>
              <div class="detail-card"><div class="detail-label">事件类型</div><div class="detail-value">{{ selectedRow.eventType }}</div></div>
              <div class="detail-card"><div class="detail-label">事件状态</div><div class="detail-value"><span class="badge" :class="selectedRow.statusType">{{ selectedRow.eventStatus }}</span></div></div>
              <div class="detail-card"><div class="detail-label">业务单号</div><div class="detail-value">{{ selectedRow.bizNo }}</div></div>
              <div class="detail-card"><div class="detail-label">摘要</div><div class="detail-value">{{ selectedRow.summary }}</div></div>
              <div class="detail-card"><div class="detail-label">创建时间</div><div class="detail-value">{{ selectedRow.createdAt }}</div></div>
            </div>
            <div class="trace-panel" v-if="selectedRow.compensationHint">
              <h4>补偿排查提示</h4>
              <p>{{ selectedRow.compensationHint }}</p>
            </div>
            <div class="trace-panel">
              <h4>链路判断</h4>
              <ul class="trace-list">
                <li>第 1 步：上游业务产出 {{ selectedRow.eventType }} 事件。</li>
                <li>第 2 步：账务系统按 {{ selectedRow.bizNo || "业务标识" }} 消费并落库事件表。</li>
                <li>第 3 步：根据载荷信息决定入账、冻结、调账或清分结果入账。</li>
                <li>第 4 步：如事件失败，需要结合载荷与业务单号做补偿重放。</li>
              </ul>
            </div>
            <div class="trace-panel">
              <h4>事件载荷</h4>
              <pre>{{ JSON.stringify(parsedPayload, null, 2) }}</pre>
            </div>
          </template>
          <div v-else class="state-box">选择左侧事件后，可查看业务链路与事件载荷。</div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.metric-grid {
  margin-bottom: 18px;
}

.section-head {
  margin-bottom: 12px;
}

.trace-panel {
  margin-top: 16px;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid #dbeafe;
  background: #f8fbff;
}

.trace-panel h4 {
  margin: 0 0 12px;
}

.trace-panel p {
  margin: 0;
  color: #334155;
  line-height: 1.7;
}

.trace-list {
  margin: 0;
  padding-left: 18px;
  color: #475569;
  line-height: 1.7;
}

pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  color: #0f172a;
  font-size: 12px;
  line-height: 1.6;
}

.empty-cell {
  padding: 16px 0;
  text-align: center;
  color: #64748b;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 14px;
}

.pagination-summary {
  color: #64748b;
  font-size: 13px;
}

.pagination-actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 720px) {
  .pagination {
    align-items: stretch;
    flex-direction: column;
  }

  .pagination-actions .button {
    flex: 1;
  }
}
</style>
