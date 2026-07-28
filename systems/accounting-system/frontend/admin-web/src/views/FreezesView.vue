<script setup>
import { computed, onMounted, ref } from "vue";
import { freezeApi } from "../api/client";

const rows = ref([]);
const selectedRow = ref(null);
const loading = ref(false);
const message = ref("");
const filters = ref({ accountNo: "", freezeStatus: "" });
const form = ref({ accountNo: "", bizNo: "", freezeType: "人工冻结", freezeReason: "风险确认", operatorName: "运营小王", freezeAmount: "10.00" });

const metrics = computed(() => ({
  total: rows.value.length,
  activeTotal: rows.value.filter((item) => item.freezeStatus !== "已解冻").length,
  unfrozenTotal: rows.value.filter((item) => item.freezeStatus === "已解冻").length,
  amountTotal: rows.value.reduce((sum, item) => sum + Number(item.freezeAmount || 0), 0).toFixed(2)
}));

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const result = await freezeApi.getList({ ...filters.value, pageNo: 1, pageSize: 50 });
    rows.value = result.items;
    selectedRow.value = result.items[0] || null;
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function createFreeze() {
  message.value = "";
  try {
    await freezeApi.create({ ...form.value, freezeAmount: Number(form.value.freezeAmount) });
    await loadRows();
  } catch (error) {
    message.value = error.message;
  }
}

async function unfreeze(freezeNo) {
  message.value = "";
  try {
    await freezeApi.unfreeze(freezeNo, { operatorName: "运营小王", unfreezeReason: "审核通过" });
    await loadRows();
  } catch (error) {
    message.value = error.message;
  }
}

function pickRow(row) {
  selectedRow.value = row;
}

function resetFilters() {
  filters.value = { accountNo: "", freezeStatus: "" };
  loadRows();
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar"><div><h2>冻结单</h2><p>冻结、解冻与原因留痕，支持风险处置和状态排查</p></div></div>
    <section class="card-grid metric-grid">
      <article class="card">
        <p class="card-title">冻结单总数</p>
        <p class="card-value">{{ metrics.total }}</p>
      </article>
      <article class="card">
        <p class="card-title">冻结中</p>
        <p class="card-value">{{ metrics.activeTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">已解冻</p>
        <p class="card-value">{{ metrics.unfrozenTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">冻结金额合计</p>
        <p class="card-value">{{ metrics.amountTotal }}</p>
      </article>
    </section>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>账户号</label><input v-model="filters.accountNo" /></div>
        <div class="field"><label>冻结状态</label><input v-model="filters.freezeStatus" /></div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadRows">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>
      <div class="toolbar">
        <div class="field"><label>账户号</label><input v-model="form.accountNo" /></div>
        <div class="field"><label>业务单号</label><input v-model="form.bizNo" /></div>
        <div class="field"><label>冻结原因</label><input v-model="form.freezeReason" /></div>
        <div class="field"><label>金额</label><input v-model="form.freezeAmount" /></div>
        <div class="toolbar-actions"><button class="button warn" @click="createFreeze">创建冻结单</button></div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">冻结单加载中...</div>
      <div v-else class="split-panels">
        <div class="table-wrap">
          <table>
            <thead><tr><th>冻结单号</th><th>账户号</th><th>业务单号</th><th>类型</th><th>原因</th><th>金额</th><th>状态</th><th>操作人</th><th>时间</th><th>解冻时间</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="row in rows" :key="row.freezeNo">
                <td>{{ row.freezeNo }}</td><td>{{ row.accountNo }}</td><td>{{ row.bizNo }}</td><td>{{ row.freezeType }}</td><td>{{ row.freezeReason }}</td><td>{{ row.freezeAmount }}</td><td><span class="badge" :class="row.statusType">{{ row.freezeStatus }}</span></td><td>{{ row.operatorName }}</td><td>{{ row.createdAt }}</td><td>{{ row.unfrozenAt || '-' }}</td>
                <td>
                  <div class="toolbar-actions">
                    <button class="button secondary" @click="pickRow(row)">查看</button>
                    <button class="button secondary" @click="unfreeze(row.freezeNo)">解冻</button>
                  </div>
                </td>
              </tr>
              <tr v-if="rows.length === 0">
                <td colspan="11" class="empty-cell">暂无冻结单数据。</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="panel" style="margin-bottom:0">
          <div class="section-head">
            <h3 style="margin:0">冻结详情</h3>
          </div>
          <template v-if="selectedRow">
            <div class="detail-grid">
              <div class="detail-card"><div class="detail-label">冻结单号</div><div class="detail-value">{{ selectedRow.freezeNo }}</div></div>
              <div class="detail-card"><div class="detail-label">账户号</div><div class="detail-value">{{ selectedRow.accountNo }}</div></div>
              <div class="detail-card"><div class="detail-label">状态</div><div class="detail-value"><span class="badge" :class="selectedRow.statusType">{{ selectedRow.freezeStatus }}</span></div></div>
              <div class="detail-card"><div class="detail-label">业务单号</div><div class="detail-value">{{ selectedRow.bizNo }}</div></div>
              <div class="detail-card"><div class="detail-label">冻结原因</div><div class="detail-value">{{ selectedRow.freezeReason }}</div></div>
              <div class="detail-card"><div class="detail-label">冻结金额</div><div class="detail-value">{{ selectedRow.freezeAmount }}</div></div>
            </div>
            <div class="trace-panel">
              <h4>处置建议</h4>
              <ul class="trace-list">
                <li>核对冻结原因是否来自风控、投诉、退款争议或人工止付。</li>
                <li>确认冻结金额是否与上游业务单金额一致，避免超冻或漏冻。</li>
                <li>解冻前需确认风控审批、退款状态和账务余额是否允许释放。</li>
              </ul>
            </div>
          </template>
          <div v-else class="state-box">选择左侧冻结单后，可在右侧查看详情与处置建议。</div>
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
