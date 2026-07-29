<script setup>
import { computed, onMounted, ref } from "vue";
import { ledgerApi } from "../api/client";

const rows = ref([]);
const selectedRow = ref(null);
const total = ref(0);
const loading = ref(false);
const message = ref("");
const filters = ref({ accountNo: "", bizNo: "", bizType: "" });

const metrics = computed(() => ({
  total: total.value,
  creditTotal: rows.value.filter((item) => item.direction === "贷").length,
  debitTotal: rows.value.filter((item) => item.direction === "借").length,
  amountTotal: rows.value.reduce((sum, item) => sum + Number(item.amount || 0), 0).toFixed(2)
}));

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const result = await ledgerApi.getList({ ...filters.value, pageNo: 1, pageSize: 50 });
    rows.value = result.items;
    total.value = result.total;
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
  filters.value = { accountNo: "", bizNo: "", bizType: "" };
  loadRows();
}

onMounted(() => {
  const query = new URLSearchParams(window.location.search);
  filters.value.accountNo = query.get("accountNo") || "";
  loadRows();
});
</script>

<template>
  <div>
    <div class="topbar"><div><h2>账户流水</h2><p>所有入账、出账、冻结、解冻、调账动作留痕</p></div></div>
    <section class="card-grid metric-grid">
      <article class="card">
        <p class="card-title">流水总数</p>
        <p class="card-value">{{ metrics.total }}</p>
      </article>
      <article class="card">
        <p class="card-title">贷方流水</p>
        <p class="card-value">{{ metrics.creditTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">借方流水</p>
        <p class="card-value">{{ metrics.debitTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">流水金额合计</p>
        <p class="card-value">{{ metrics.amountTotal }}</p>
      </article>
    </section>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>账户号</label><input v-model="filters.accountNo" /></div>
        <div class="field"><label>业务单号</label><input v-model="filters.bizNo" /></div>
        <div class="field"><label>业务类型</label><input v-model="filters.bizType" /></div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadRows">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">流水加载中...</div>
      <div v-else class="split-panels">
        <div class="table-wrap">
          <table>
            <thead><tr><th>流水号</th><th>账户号</th><th>业务类型</th><th>业务单号</th><th>方向</th><th>金额</th><th>前余额</th><th>后余额</th><th>状态</th><th>时间</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="row in rows" :key="row.ledgerNo">
                <td>{{ row.ledgerNo }}</td><td>{{ row.accountNo }}</td><td>{{ row.bizType }}</td><td>{{ row.bizNo }}</td><td>{{ row.direction }}</td><td>{{ row.amount }}</td><td>{{ row.beforeBalance }}</td><td>{{ row.afterBalance }}</td><td><span class="badge" :class="row.statusType">{{ row.ledgerStatus }}</span></td><td>{{ row.createdAt }}</td>
                <td><button class="button secondary" @click="pickRow(row)">查看</button></td>
              </tr>
              <tr v-if="rows.length === 0">
                <td colspan="11" class="empty-cell">暂无流水数据。</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="panel" style="margin-bottom:0">
          <div class="section-head">
            <h3 style="margin:0">流水详情</h3>
          </div>
          <template v-if="selectedRow">
            <div class="detail-grid">
              <div class="detail-card"><div class="detail-label">流水号</div><div class="detail-value">{{ selectedRow.ledgerNo }}</div></div>
              <div class="detail-card"><div class="detail-label">账户号</div><div class="detail-value">{{ selectedRow.accountNo }}</div></div>
              <div class="detail-card"><div class="detail-label">状态</div><div class="detail-value"><span class="badge" :class="selectedRow.statusType">{{ selectedRow.ledgerStatus }}</span></div></div>
              <div class="detail-card"><div class="detail-label">业务类型</div><div class="detail-value">{{ selectedRow.bizType }}</div></div>
              <div class="detail-card"><div class="detail-label">方向</div><div class="detail-value">{{ selectedRow.direction }}</div></div>
              <div class="detail-card"><div class="detail-label">金额</div><div class="detail-value">{{ selectedRow.amount }}</div></div>
              <div class="detail-card"><div class="detail-label">前余额</div><div class="detail-value">{{ selectedRow.beforeBalance }}</div></div>
              <div class="detail-card"><div class="detail-label">后余额</div><div class="detail-value">{{ selectedRow.afterBalance }}</div></div>
              <div class="detail-card"><div class="detail-label">业务单号</div><div class="detail-value">{{ selectedRow.bizNo }}</div></div>
            </div>
            <div class="trace-panel">
              <h4>核对建议</h4>
              <ul class="trace-list">
                <li>核对业务类型与上下游单据是否一致，确认没有重复入账或漏账。</li>
                <li>检查前后余额变化是否满足借贷方向和金额公式。</li>
                <li>若与支付、退款、清分链路不一致，应回查事件表和业务单据。</li>
              </ul>
            </div>
          </template>
          <div v-else class="state-box">选择左侧流水后，可在右侧查看余额变化与核对建议。</div>
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
