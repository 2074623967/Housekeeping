<script setup>
import { computed, onMounted, ref } from "vue";
import { adjustmentApi } from "../api/client";

const rows = ref([]);
const selectedRow = ref(null);
const loading = ref(false);
const message = ref("");
const filters = ref({ accountNo: "", adjustStatus: "" });
const form = ref({ accountNo: "", adjustDirection: "贷方", adjustAmount: "8.00", adjustReason: "系统补偿", createdBy: "财务小李" });

const metrics = computed(() => ({
  total: rows.value.length,
  pendingTotal: rows.value.filter((item) => item.adjustStatus !== "已生效").length,
  effectiveTotal: rows.value.filter((item) => item.adjustStatus === "已生效").length,
  amountTotal: rows.value.reduce((sum, item) => sum + Number(item.adjustAmount || 0), 0).toFixed(2)
}));

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const result = await adjustmentApi.getList({ ...filters.value, pageNo: 1, pageSize: 50 });
    rows.value = result.items;
    selectedRow.value = result.items[0] || null;
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function createAdjustment() {
  message.value = "";
  try {
    await adjustmentApi.create({ ...form.value, adjustAmount: Number(form.value.adjustAmount) });
    await loadRows();
  } catch (error) {
    message.value = error.message;
  }
}

async function approve(adjustNo) {
  message.value = "";
  try {
    await adjustmentApi.approve(adjustNo, { approvedBy: "财务主管" });
    await loadRows();
  } catch (error) {
    message.value = error.message;
  }
}

function pickRow(row) {
  selectedRow.value = row;
}

function resetFilters() {
  filters.value = { accountNo: "", adjustStatus: "" };
  loadRows();
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar"><div><h2>调账单</h2><p>人工修正与系统补偿，支持审批和生效状态排查</p></div></div>
    <section class="card-grid metric-grid">
      <article class="card">
        <p class="card-title">调账单总数</p>
        <p class="card-value">{{ metrics.total }}</p>
      </article>
      <article class="card">
        <p class="card-title">待审批 / 待生效</p>
        <p class="card-value">{{ metrics.pendingTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">已生效</p>
        <p class="card-value">{{ metrics.effectiveTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">调账金额合计</p>
        <p class="card-value">{{ metrics.amountTotal }}</p>
      </article>
    </section>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>账户号</label><input v-model="filters.accountNo" /></div>
        <div class="field"><label>调账状态</label><input v-model="filters.adjustStatus" /></div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadRows">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>
      <div class="toolbar">
        <div class="field"><label>账户号</label><input v-model="form.accountNo" /></div>
        <div class="field"><label>方向</label><input v-model="form.adjustDirection" /></div>
        <div class="field"><label>调账原因</label><input v-model="form.adjustReason" /></div>
        <div class="field"><label>金额</label><input v-model="form.adjustAmount" /></div>
        <div class="toolbar-actions"><button class="button warn" @click="createAdjustment">创建调账单</button></div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">调账单加载中...</div>
      <div v-else class="split-panels">
        <div class="table-wrap">
          <table>
            <thead><tr><th>调账单号</th><th>账户号</th><th>方向</th><th>金额</th><th>原因</th><th>状态</th><th>创建人</th><th>审批人</th><th>时间</th><th>审批时间</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="row in rows" :key="row.adjustNo">
                <td>{{ row.adjustNo }}</td><td>{{ row.accountNo }}</td><td>{{ row.adjustDirection }}</td><td>{{ row.adjustAmount }}</td><td>{{ row.adjustReason }}</td><td><span class="badge" :class="row.statusType">{{ row.adjustStatus }}</span></td><td>{{ row.createdBy }}</td><td>{{ row.approvedBy || '-' }}</td><td>{{ row.createdAt }}</td><td>{{ row.approvedAt || '-' }}</td>
                <td>
                  <div class="toolbar-actions">
                    <button class="button secondary" @click="pickRow(row)">查看</button>
                    <button class="button secondary" @click="approve(row.adjustNo)">审批</button>
                  </div>
                </td>
              </tr>
              <tr v-if="rows.length === 0">
                <td colspan="11" class="empty-cell">暂无调账单数据。</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="panel" style="margin-bottom:0">
          <div class="section-head">
            <h3 style="margin:0">调账详情</h3>
          </div>
          <template v-if="selectedRow">
            <div class="detail-grid">
              <div class="detail-card"><div class="detail-label">调账单号</div><div class="detail-value">{{ selectedRow.adjustNo }}</div></div>
              <div class="detail-card"><div class="detail-label">账户号</div><div class="detail-value">{{ selectedRow.accountNo }}</div></div>
              <div class="detail-card"><div class="detail-label">状态</div><div class="detail-value"><span class="badge" :class="selectedRow.statusType">{{ selectedRow.adjustStatus }}</span></div></div>
              <div class="detail-card"><div class="detail-label">方向</div><div class="detail-value">{{ selectedRow.adjustDirection }}</div></div>
              <div class="detail-card"><div class="detail-label">调账原因</div><div class="detail-value">{{ selectedRow.adjustReason }}</div></div>
              <div class="detail-card"><div class="detail-label">调账金额</div><div class="detail-value">{{ selectedRow.adjustAmount }}</div></div>
            </div>
            <div class="trace-panel">
              <h4>审批建议</h4>
              <ul class="trace-list">
                <li>确认调账原因是否有业务依据，如支付补偿、清分差错、退款回补。</li>
                <li>核对借贷方向与金额是否正确，避免造成余额反向变动。</li>
                <li>审批后需关注是否已同步生成账务流水并更新余额快照。</li>
              </ul>
            </div>
          </template>
          <div v-else class="state-box">选择左侧调账单后，可在右侧查看详情与审批建议。</div>
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

.trace-list {
  margin: 0;
  padding-left: 18px;
  color: #475569;
  line-height: 1.7;
}

.empty-cell {
  padding: 16px 0;
  text-align: center;
  color: #64748b;
}
</style>
