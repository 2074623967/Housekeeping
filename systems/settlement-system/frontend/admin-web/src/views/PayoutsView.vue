<script setup>
import { onMounted, ref } from "vue";
import { payoutApi } from "../api/client";

const rows = ref([]);
const message = ref("");
const loading = ref(false);
const actionLoading = ref(false);
const recordsLoading = ref(false);
const filters = ref({ batchNo: "", payoutStatus: "" });
const form = ref({ batchNo: "SET10001", payoutChannel: "BANK", createdBy: "结算运营" });
const selectedBatch = ref(null);
const recordRows = ref([]);
const recordMessage = ref("");
const recordFilters = ref({ payoutStatus: "" });
const retryForm = ref({ operatorName: "结算运营", reason: "失败批次人工补发" });

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const result = await payoutApi.getList({ ...filters.value, pageNo: 1, pageSize: 20 });
    rows.value = result.items;
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function createPayout() {
  actionLoading.value = true;
  message.value = "";
  try {
    await payoutApi.create(form.value);
    await loadRows();
  } catch (error) {
    message.value = error.message;
  } finally {
    actionLoading.value = false;
  }
}

async function loadRecords(row, keepFilters = false) {
  selectedBatch.value = row;
  recordsLoading.value = true;
  recordMessage.value = "";
  if (!keepFilters) {
    recordFilters.value = { payoutStatus: "" };
  }
  try {
    const result = await payoutApi.records(row.payoutBatchNo, { ...recordFilters.value, pageNo: 1, pageSize: 20 });
    recordRows.value = result.items;
  } catch (error) {
    recordRows.value = [];
    recordMessage.value = error.message;
  } finally {
    recordsLoading.value = false;
  }
}

async function retryBatch(row) {
  if (!window.confirm(`确认重试出款批次 ${row.payoutBatchNo} 吗？`)) {
    return;
  }
  actionLoading.value = true;
  message.value = "";
  try {
    const result = await payoutApi.retry(row.payoutBatchNo, retryForm.value);
    rows.value = rows.value.map((item) => (item.payoutBatchNo === result.payoutBatchNo ? result : item));
    if (selectedBatch.value?.payoutBatchNo === result.payoutBatchNo) {
      await loadRecords(result, true);
    }
  } catch (error) {
    message.value = error.message;
  } finally {
    actionLoading.value = false;
  }
}

function resetFilters() {
  filters.value = { batchNo: "", payoutStatus: "" };
  loadRows();
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>出款批次</h2>
        <p>发起结算出款和重试失败记录</p>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>批次号</label><input v-model="filters.batchNo" /></div>
        <div class="field"><label>出款状态</label><input v-model="filters.payoutStatus" /></div>
        <div class="toolbar-actions"><button class="button primary" @click="loadRows">查询</button><button class="button secondary" @click="resetFilters">重置</button></div>
      </div>
      <div class="toolbar">
        <div class="field"><label>结算批次号</label><input v-model="form.batchNo" /></div>
        <div class="field"><label>出款渠道</label><input v-model="form.payoutChannel" /></div>
        <div class="field"><label>创建人</label><input v-model="form.createdBy" /></div>
        <div class="toolbar-actions"><button class="button warn" :disabled="actionLoading" @click="createPayout">发起出款</button></div>
      </div>
      <div class="toolbar">
        <div class="field"><label>重试操作人</label><input v-model="retryForm.operatorName" /></div>
        <div class="field"><label>重试原因</label><input v-model="retryForm.reason" /></div>
        <div class="summary-box">
          <strong>操作说明</strong>
          <span>先查看失败明细，再对失败批次执行人工补发重试。</span>
        </div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">出款批次加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>出款批次号</th><th>结算批次号</th><th>渠道</th><th>状态</th><th>笔数</th><th>成功</th><th>失败</th><th>总金额</th><th>创建人</th><th>完成时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.payoutBatchNo">
              <td>{{ row.payoutBatchNo }}</td><td>{{ row.batchNo }}</td><td>{{ row.payoutChannel }}</td><td><span class="badge" :class="row.payoutStatusType">{{ row.payoutStatus }}</span></td>
              <td>{{ row.payoutCount }}</td><td>{{ row.successCount }}</td><td>{{ row.failedCount }}</td><td class="amount">{{ row.totalAmount }}</td><td>{{ row.createdBy }}</td><td>{{ row.finishedAt || "-" }}</td>
              <td class="actions-cell">
                <button class="button secondary button-inline" :disabled="actionLoading" @click="loadRecords(row)">查看出款记录</button>
                <button
                  v-if="row.failedCount > 0 || row.payoutStatus === '部分失败' || row.payoutStatus === '已失败'"
                  class="button danger button-inline"
                  :disabled="actionLoading"
                  @click="retryBatch(row)"
                >
                  失败重试
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
    <section class="panel">
      <div class="topbar topbar-inner">
        <div>
          <h3>出款记录跟踪</h3>
          <p>{{ selectedBatch ? `当前批次：${selectedBatch.payoutBatchNo}` : "请选择出款批次查看出款明细" }}</p>
        </div>
      </div>
      <div v-if="selectedBatch" class="toolbar">
        <div class="field"><label>记录状态</label><input v-model="recordFilters.payoutStatus" placeholder="例如：已发放/已失败" /></div>
        <div class="summary-box">
          <strong>批次摘要</strong>
          <span>结算批次 {{ selectedBatch.batchNo }}，成功 {{ selectedBatch.successCount }}，失败 {{ selectedBatch.failedCount }}。</span>
        </div>
        <div class="toolbar-actions">
          <button class="button primary" :disabled="recordsLoading" @click="loadRecords(selectedBatch, true)">刷新记录</button>
        </div>
      </div>
      <div v-if="!selectedBatch" class="state-box">选择上方任一出款批次后，可查看记录明细和失败补发情况。</div>
      <div v-else-if="recordMessage" class="state-box">{{ recordMessage }}</div>
      <div v-else-if="recordsLoading" class="state-box">出款记录加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>出款单号</th><th>出款批次号</th><th>结算单号</th><th>对象编号</th><th>对象名称</th><th>出款金额</th><th>状态</th><th>重试次数</th><th>创建时间</th></tr></thead>
          <tbody>
            <tr v-for="row in recordRows" :key="row.payoutNo">
              <td>{{ row.payoutNo }}</td><td>{{ row.payoutBatchNo }}</td><td>{{ row.settlementNo }}</td><td>{{ row.targetNo }}</td><td>{{ row.targetName }}</td>
              <td class="amount">{{ row.payoutAmount }}</td><td><span class="badge" :class="row.payoutStatusType">{{ row.payoutStatus }}</span></td><td>{{ row.retryCount }}</td><td>{{ row.createdAt }}</td>
            </tr>
            <tr v-if="recordRows.length === 0">
              <td colspan="9" class="empty-cell">当前筛选条件下暂无出款记录。</td>
            </tr>
          </tbody>
        </table>
      </div>
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

.summary-box {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  min-height: 72px;
  padding: 12px 14px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: linear-gradient(180deg, #f8fbff 0%, #eff6ff 100%);
  color: #1e3a8a;
}

.button-inline {
  padding: 8px 12px;
}

.actions-cell {
  display: flex;
  gap: 8px;
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
