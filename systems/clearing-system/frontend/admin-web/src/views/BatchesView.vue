<script setup>
import { onMounted, ref } from "vue";
import { batchApi, orderApi } from "../api/client";

const rows = ref([]);
const loading = ref(false);
const actionLoading = ref(false);
const detailLoading = ref(false);
const message = ref("");
const detailMessage = ref("");
const filters = ref({ batchDate: "", batchStatus: "" });
const form = ref({ batchDate: "2026-07-20", sourceType: "MANUAL", createdBy: "清分运营", idempotencyKey: "" });
const rerunForm = ref({ operatorName: "清分运营", reason: "人工补偿重跑" });
const selectedBatch = ref(null);
const batchDetail = ref(null);
const detailOrders = ref([]);

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const result = await batchApi.getList({ ...filters.value, pageNo: 1, pageSize: 20 });
    rows.value = result.items;
    if (selectedBatch.value) {
      const latestBatch = result.items.find((item) => item.batchNo === selectedBatch.value.batchNo);
      if (latestBatch) {
        selectedBatch.value = latestBatch;
      }
    }
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function createBatch() {
  actionLoading.value = true;
  message.value = "";
  try {
    await batchApi.create({ ...form.value, idempotencyKey: form.value.idempotencyKey || `BATCH-${Date.now()}` });
    await loadRows();
  } catch (error) {
    message.value = error.message;
  } finally {
    actionLoading.value = false;
  }
}

async function rerunBatch(row) {
  if (!window.confirm(`确认重跑清分批次 ${row.batchNo} 吗？`)) {
    return;
  }
  actionLoading.value = true;
  message.value = "";
  try {
    await batchApi.rerun(row.batchNo, rerunForm.value);
    await loadRows();
    await openDetail(row.batchNo);
  } catch (error) {
    message.value = error.message;
  } finally {
    actionLoading.value = false;
  }
}

async function openDetail(batchNo) {
  selectedBatch.value = rows.value.find((item) => item.batchNo === batchNo) || selectedBatch.value;
  detailLoading.value = true;
  detailMessage.value = "";
  batchDetail.value = null;
  detailOrders.value = [];
  try {
    batchDetail.value = await batchApi.getDetail(batchNo);
    const result = await orderApi.getList({ batchNo, pageNo: 1, pageSize: 20 });
    detailOrders.value = result.items;
  } catch (error) {
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
        <h2>清分批次</h2>
        <p>管理清分任务发起、批次状态、重跑补偿和批次下清分结果排查</p>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>批次日期</label><input v-model="filters.batchDate" placeholder="2026-07-20" /></div>
        <div class="field"><label>批次状态</label><input v-model="filters.batchStatus" placeholder="处理中 / 已完成" /></div>
        <div class="summary-box">
          <strong>运营说明</strong>
          <span>先查看批次摘要和关联清分单，再决定是否需要人工重跑或补偿。</span>
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadRows">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>
      <div class="toolbar">
        <div class="field"><label>批次日期</label><input v-model="form.batchDate" /></div>
        <div class="field"><label>来源类型</label><input v-model="form.sourceType" /></div>
        <div class="field"><label>创建人</label><input v-model="form.createdBy" /></div>
        <div class="toolbar-actions"><button class="button warn" :disabled="actionLoading" @click="createBatch">发起清分</button></div>
      </div>
      <div class="toolbar">
        <div class="field"><label>重跑操作人</label><input v-model="rerunForm.operatorName" /></div>
        <div class="field"><label>重跑原因</label><input v-model="rerunForm.reason" /></div>
        <div class="summary-box">
          <strong>重跑约束</strong>
          <span>重跑必须保留历史版本，建议仅在规则异常、补偿计算或批次失败时使用。</span>
        </div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">清分批次加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>批次号</th><th>批次日期</th><th>来源</th><th>状态</th><th>订单数</th><th>总金额</th><th>成功</th><th>失败</th><th>版本</th><th>创建人</th><th>完成时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.batchNo">
              <td>{{ row.batchNo }}</td><td>{{ row.batchDate }}</td><td>{{ row.sourceType }}</td>
              <td><span class="badge" :class="row.batchStatusType">{{ row.batchStatus }}</span></td>
              <td>{{ row.totalOrderCount }}</td><td class="amount">{{ row.totalAmount }}</td><td>{{ row.successOrderCount }}</td><td class="risk-count">{{ row.failedOrderCount }}</td><td>{{ row.versionNo }}</td><td>{{ row.createdBy }}</td><td>{{ row.finishedAt || "-" }}</td>
              <td class="actions-cell">
                <button class="button secondary button-inline" :disabled="detailLoading" @click="openDetail(row.batchNo)">查看批次明细</button>
                <button class="button warn button-inline" :disabled="actionLoading" @click="rerunBatch(row)">重跑</button>
              </td>
            </tr>
            <tr v-if="rows.length === 0">
              <td colspan="12" class="empty-cell">当前筛选条件下暂无清分批次。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="panel">
      <div class="topbar topbar-inner">
        <div>
          <h3>批次下钻</h3>
          <p>{{ selectedBatch ? `当前批次：${selectedBatch.batchNo}` : "请选择上方清分批次查看摘要和关联清分结果" }}</p>
        </div>
      </div>
      <div v-if="!selectedBatch" class="state-box">点击上方任意批次后，可查看批次摘要和关联清分结果。</div>
      <div v-else-if="detailMessage" class="state-box">{{ detailMessage }}</div>
      <div v-else-if="detailLoading" class="state-box">批次详情加载中...</div>
      <template v-else-if="batchDetail">
        <div class="detail-summary-grid">
          <div class="summary-card">
            <strong>批次信息</strong>
            <span>{{ batchDetail.batchDate }} / {{ batchDetail.sourceType }} / {{ batchDetail.versionNo }}</span>
          </div>
          <div class="summary-card">
            <strong>状态与结果</strong>
            <span>{{ batchDetail.batchStatus }} / 成功 {{ batchDetail.successOrderCount }} / 失败 {{ batchDetail.failedOrderCount }}</span>
          </div>
          <div class="summary-card">
            <strong>金额与创建人</strong>
            <span>{{ batchDetail.totalAmount }} / {{ batchDetail.createdBy }}</span>
          </div>
        </div>
        <div class="table-wrap">
          <table>
            <thead><tr><th>清分单号</th><th>支付单号</th><th>订单号</th><th>订单金额</th><th>商家</th><th>服务者</th><th>平台</th><th>渠道费</th><th>状态</th></tr></thead>
            <tbody>
              <tr v-for="row in detailOrders" :key="row.clearingNo">
                <td>{{ row.clearingNo }}</td><td>{{ row.paymentOrderId }}</td><td>{{ row.orderNo }}</td><td class="amount">{{ row.orderAmount }}</td><td class="amount">{{ row.merchantAmount }}</td><td class="amount">{{ row.workerAmount }}</td><td class="amount">{{ row.platformAmount }}</td><td class="amount deduct">{{ row.channelFeeAmount }}</td>
                <td><span class="badge" :class="row.clearingStatusType">{{ row.clearingStatus }}</span></td>
              </tr>
              <tr v-if="detailOrders.length === 0">
                <td colspan="9" class="empty-cell">当前批次下暂无关联清分结果。</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.summary-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid #dbeafe;
  background: linear-gradient(180deg, #f8fbff 0%, #eff6ff 100%);
  color: #1e3a8a;
}

.topbar-inner {
  margin-bottom: 12px;
}

.topbar-inner h3 {
  margin: 0;
  font-size: 20px;
}

.detail-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.actions-cell {
  display: flex;
  gap: 8px;
}

.button-inline {
  padding: 8px 12px;
}

.amount {
  color: #b45309;
  font-weight: 700;
}

.deduct,
.risk-count {
  color: #b91c1c;
  font-weight: 700;
}

.empty-cell {
  color: #64748b;
  text-align: center;
}
</style>
