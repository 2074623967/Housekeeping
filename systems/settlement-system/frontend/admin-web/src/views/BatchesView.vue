<script setup>
import { onMounted, ref } from "vue";
import { batchApi, orderApi } from "../api/client";

const rows = ref([]);
const message = ref("");
const loading = ref(false);
const filters = ref({ batchDate: "", batchStatus: "" });
const form = ref({ batchDate: "2026-07-20", settlementType: "MANUAL", createdBy: "结算运营", idempotencyKey: "" });
const selectedBatch = ref(null);
const batchDetail = ref(null);
const detailRows = ref([]);
const detailLoading = ref(false);
const detailMessage = ref("");

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const result = await batchApi.getList({ ...filters.value, pageNo: 1, pageSize: 20 });
    rows.value = result.items;
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function createBatch() {
  await batchApi.create({ ...form.value, idempotencyKey: form.value.idempotencyKey || `SET-${Date.now()}` });
  await loadRows();
}

async function openDetail(row) {
  selectedBatch.value = row;
  detailLoading.value = true;
  detailMessage.value = "";
  try {
    batchDetail.value = await batchApi.getDetail(row.batchNo);
    const result = await orderApi.getList({ batchNo: row.batchNo, pageNo: 1, pageSize: 20 });
    detailRows.value = result.items;
  } catch (error) {
    batchDetail.value = null;
    detailRows.value = [];
    detailMessage.value = error.message;
  } finally {
    detailLoading.value = false;
  }
}

function resetFilters() {
  filters.value = { batchDate: "", batchStatus: "" };
  loadRows();
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>结算批次</h2>
        <p>管理结算任务发起、批次状态和处理进度</p>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>批次日期</label><input v-model="filters.batchDate" /></div>
        <div class="field"><label>批次状态</label><input v-model="filters.batchStatus" /></div>
        <div class="toolbar-actions"><button class="button primary" @click="loadRows">查询</button><button class="button secondary" @click="resetFilters">重置</button></div>
      </div>
      <div class="toolbar">
        <div class="field"><label>批次日期</label><input v-model="form.batchDate" /></div>
        <div class="field"><label>结算类型</label><input v-model="form.settlementType" /></div>
        <div class="field"><label>创建人</label><input v-model="form.createdBy" /></div>
        <div class="toolbar-actions"><button class="button warn" @click="createBatch">发起结算</button></div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">结算批次加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>批次号</th><th>日期</th><th>类型</th><th>状态</th><th>总数</th><th>总金额</th><th>审核数</th><th>出款数</th><th>创建人</th><th>完成时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.batchNo">
              <td>{{ row.batchNo }}</td><td>{{ row.batchDate }}</td><td>{{ row.settlementType }}</td><td><span class="badge" :class="row.batchStatusType">{{ row.batchStatus }}</span></td>
              <td>{{ row.totalCount }}</td><td>{{ row.totalAmount }}</td><td>{{ row.auditedCount }}</td><td>{{ row.payoutCount }}</td><td>{{ row.createdBy }}</td><td>{{ row.finishedAt || "-" }}</td>
              <td><button class="button secondary button-inline" @click="openDetail(row)">查看批次明细</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
    <section class="panel">
      <div class="topbar topbar-inner">
        <div>
          <h3>批次明细跟踪</h3>
          <p>{{ selectedBatch ? `当前批次：${selectedBatch.batchNo}` : "请选择上方结算批次查看摘要和关联结算单" }}</p>
        </div>
      </div>
      <div v-if="!selectedBatch" class="state-box">点击上方任意批次后，可查看批次摘要和关联结算单。</div>
      <div v-else-if="detailMessage" class="state-box">{{ detailMessage }}</div>
      <div v-else-if="detailLoading" class="state-box">批次详情加载中...</div>
      <template v-else-if="batchDetail">
        <div class="detail-grid">
          <article class="card">
            <p class="card-title">批次状态</p>
            <p class="card-value">{{ batchDetail.batchStatus }}</p>
          </article>
          <article class="card">
            <p class="card-title">结算总笔数</p>
            <p class="card-value">{{ batchDetail.totalCount }}</p>
          </article>
          <article class="card">
            <p class="card-title">审核完成数</p>
            <p class="card-value">{{ batchDetail.auditedCount }}</p>
          </article>
          <article class="card">
            <p class="card-title">已出款数</p>
            <p class="card-value">{{ batchDetail.payoutCount }}</p>
          </article>
        </div>
        <div class="state-box detail-summary">
          <strong>{{ batchDetail.batchNo }}</strong>
          <span>批次日期 {{ batchDetail.batchDate }}，结算类型 {{ batchDetail.settlementType }}，总金额 {{ batchDetail.totalAmount }}。</span>
        </div>
        <div class="table-wrap">
          <table>
            <thead><tr><th>结算单号</th><th>对象类型</th><th>对象编号</th><th>对象名称</th><th>应结</th><th>扣减</th><th>实结</th><th>审核状态</th><th>出款状态</th></tr></thead>
            <tbody>
              <tr v-for="row in detailRows" :key="row.settlementNo">
                <td>{{ row.settlementNo }}</td><td>{{ row.targetType }}</td><td>{{ row.targetNo }}</td><td>{{ row.targetName }}</td>
                <td class="amount">{{ row.shouldSettleAmount }}</td><td class="amount">{{ row.deductAmount }}</td><td class="amount">{{ row.netSettleAmount }}</td>
                <td>{{ row.auditStatus }}</td><td><span class="badge" :class="row.payoutStatusType">{{ row.payoutStatus }}</span></td>
              </tr>
              <tr v-if="detailRows.length === 0">
                <td colspan="9" class="empty-cell">当前批次下暂无关联结算单。</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.topbar-inner {
  margin-bottom: 12px;
}

.topbar-inner h3 {
  margin: 0;
  font-size: 20px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.detail-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 16px;
}

.button-inline {
  padding: 8px 12px;
}

.amount {
  color: #b45309;
  font-weight: 700;
}

.empty-cell {
  color: #64748b;
  text-align: center;
}
</style>
