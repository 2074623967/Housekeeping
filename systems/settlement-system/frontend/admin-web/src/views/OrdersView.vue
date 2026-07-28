<script setup>
import { computed, onMounted, ref } from "vue";
import { orderApi } from "../api/client";

const rows = ref([]);
const message = ref("");
const loading = ref(false);
const actionLoading = ref(false);
const detailLoading = ref(false);
const filters = ref({ batchNo: "", targetType: "", settlementStatus: "" });
const auditForm = ref({ operatorName: "财务经理", auditRemark: "资料齐全，金额核验通过" });
const detailFilters = ref({ activeTab: "items" });
const selectedOrder = ref(null);
const selectedDetail = ref(null);
const detailMessage = ref("");

const statusOptions = ["待审核", "审核通过", "审核驳回", "待出款", "出款中", "已完成"];
const targetTypeOptions = ["SERVICE_PROVIDER", "MERCHANT"];

const auditLogRows = computed(() => selectedDetail.value?.auditLogs || []);
const itemRows = computed(() => selectedDetail.value?.items || []);

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const result = await orderApi.getList({ ...filters.value, pageNo: 1, pageSize: 20 });
    rows.value = result.items;
    if (selectedOrder.value) {
      const latestOrder = result.items.find((item) => item.settlementNo === selectedOrder.value.settlementNo);
      if (latestOrder) {
        selectedOrder.value = latestOrder;
      }
    }
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function loadDetail(row) {
  selectedOrder.value = row;
  selectedDetail.value = null;
  detailLoading.value = true;
  detailMessage.value = "";
  try {
    selectedDetail.value = await orderApi.getDetail(row.settlementNo);
  } catch (error) {
    detailMessage.value = error.message;
  } finally {
    detailLoading.value = false;
  }
}

async function submitAudit(row, actionType) {
  const actionLabel = actionType === "approve" ? "通过" : "驳回";
  if (!window.confirm(`确认${actionLabel}结算单 ${row.settlementNo} 吗？`)) {
    return;
  }
  actionLoading.value = true;
  message.value = "";
  try {
    if (actionType === "approve") {
      await orderApi.audit(row.settlementNo, auditForm.value);
    } else {
      await orderApi.reject(row.settlementNo, auditForm.value);
    }
    await loadRows();
    await loadDetail({ ...row, settlementNo: row.settlementNo });
  } catch (error) {
    message.value = error.message;
  } finally {
    actionLoading.value = false;
  }
}

function resetFilters() {
  filters.value = { batchNo: "", targetType: "", settlementStatus: "" };
  loadRows();
}

function settlementAmountSummary(order) {
  if (!order) {
    return "请选择结算单查看金额构成、审核链路和处理动作。";
  }
  return `应结 ${order.shouldSettleAmount}，扣减 ${order.deductAmount}，实结 ${order.netSettleAmount}，当前审核状态 ${order.auditStatus}。`;
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>结算单</h2>
        <p>统一查看服务者/商家的应结金额、审核状态、出款进度和异常处理动作</p>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div class="field">
          <label>批次号</label>
          <input v-model="filters.batchNo" placeholder="例如：SET10001" />
        </div>
        <div class="field">
          <label>对象类型</label>
          <select v-model="filters.targetType">
            <option value="">全部</option>
            <option v-for="item in targetTypeOptions" :key="item" :value="item">{{ item }}</option>
          </select>
        </div>
        <div class="field">
          <label>结算状态</label>
          <select v-model="filters.settlementStatus">
            <option value="">全部</option>
            <option v-for="item in statusOptions" :key="item" :value="item">{{ item }}</option>
          </select>
        </div>
        <div class="summary-box">
          <strong>运营说明</strong>
          <span>列表支持同页完成查看、审核、驳回和明细下钻，减少审核跳转成本。</span>
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadRows">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>
      <div class="toolbar">
        <div class="field">
          <label>审核人</label>
          <input v-model="auditForm.operatorName" placeholder="请输入审核人" />
        </div>
        <div class="field field-span-2">
          <label>审核备注</label>
          <input v-model="auditForm.auditRemark" placeholder="请输入审核通过/驳回原因" />
        </div>
        <div class="summary-box">
          <strong>金额摘要</strong>
          <span>{{ settlementAmountSummary(selectedOrder) }}</span>
        </div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">结算单加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>结算单号</th>
              <th>批次号</th>
              <th>对象类型</th>
              <th>对象编号</th>
              <th>对象名称</th>
              <th>应结</th>
              <th>扣减</th>
              <th>实结</th>
              <th>结算状态</th>
              <th>审核状态</th>
              <th>出款状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.settlementNo">
              <td>{{ row.settlementNo }}</td>
              <td>{{ row.batchNo }}</td>
              <td>{{ row.targetType }}</td>
              <td>{{ row.targetNo }}</td>
              <td>{{ row.targetName }}</td>
              <td class="amount">{{ row.shouldSettleAmount }}</td>
              <td class="amount deduct">{{ row.deductAmount }}</td>
              <td class="amount">{{ row.netSettleAmount }}</td>
              <td><span class="badge" :class="row.settlementStatusType">{{ row.settlementStatus }}</span></td>
              <td><span class="badge" :class="row.auditStatusType || 'info'">{{ row.auditStatus }}</span></td>
              <td><span class="badge" :class="row.payoutStatusType">{{ row.payoutStatus }}</span></td>
              <td class="actions-cell">
                <button class="button secondary button-inline" :disabled="detailLoading" @click="loadDetail(row)">查看详情</button>
                <button class="button primary button-inline" :disabled="actionLoading" @click="submitAudit(row, 'approve')">审核通过</button>
                <button class="button danger button-inline" :disabled="actionLoading" @click="submitAudit(row, 'reject')">驳回</button>
              </td>
            </tr>
            <tr v-if="rows.length === 0">
              <td colspan="12" class="empty-cell">当前筛选条件下暂无结算单。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="panel">
      <div class="topbar topbar-inner">
        <div>
          <h3>结算单处理明细</h3>
          <p>{{ selectedOrder ? `当前结算单：${selectedOrder.settlementNo}` : "请选择上方结算单查看处理链路" }}</p>
        </div>
      </div>
      <div v-if="selectedOrder" class="detail-summary-grid">
        <div class="summary-card">
          <strong>对象信息</strong>
          <span>{{ selectedOrder.targetName }} / {{ selectedOrder.targetNo }}</span>
        </div>
        <div class="summary-card">
          <strong>批次与状态</strong>
          <span>{{ selectedOrder.batchNo }} / {{ selectedOrder.settlementStatus }}</span>
        </div>
        <div class="summary-card">
          <strong>审核与出款</strong>
          <span>{{ selectedOrder.auditStatus }} / {{ selectedOrder.payoutStatus }}</span>
        </div>
      </div>
      <div v-if="selectedOrder" class="detail-tabs">
        <button
          class="tab-button"
          :class="{ active: detailFilters.activeTab === 'items' }"
          @click="detailFilters.activeTab = 'items'"
        >
          金额明细
        </button>
        <button
          class="tab-button"
          :class="{ active: detailFilters.activeTab === 'logs' }"
          @click="detailFilters.activeTab = 'logs'"
        >
          审核日志
        </button>
      </div>
      <div v-if="!selectedOrder" class="state-box">选择上方任一结算单后，可在本页完成详情查看和审核处理。</div>
      <div v-else-if="detailMessage" class="state-box">{{ detailMessage }}</div>
      <div v-else-if="detailLoading" class="state-box">结算单详情加载中...</div>
      <div v-else-if="selectedDetail" class="table-wrap">
        <table v-if="detailFilters.activeTab === 'items'">
          <thead>
            <tr>
              <th>明细项名称</th>
              <th>明细项类型</th>
              <th>金额</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in itemRows" :key="`${selectedDetail.order.settlementNo}-${row.itemName}-${row.itemType}`">
              <td>{{ row.itemName }}</td>
              <td>{{ row.itemType }}</td>
              <td class="amount" :class="{ deduct: row.itemType === '扣减' }">{{ row.amount }}</td>
            </tr>
            <tr v-if="itemRows.length === 0">
              <td colspan="3" class="empty-cell">当前结算单暂无明细项。</td>
            </tr>
          </tbody>
        </table>
        <table v-else>
          <thead>
            <tr>
              <th>操作时间</th>
              <th>操作动作</th>
              <th>操作结果</th>
              <th>操作人</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in auditLogRows" :key="`${selectedDetail.order.settlementNo}-${row.action}-${row.operatorName}-${row.createdAt}`">
              <td>{{ row.createdAt }}</td>
              <td>{{ row.action }}</td>
              <td>{{ row.result }}</td>
              <td>{{ row.operatorName }}</td>
            </tr>
            <tr v-if="auditLogRows.length === 0">
              <td colspan="4" class="empty-cell">当前结算单暂无审核日志。</td>
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

.field-span-2 {
  grid-column: span 2;
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

.deduct {
  color: #b91c1c;
}

.empty-cell {
  color: #64748b;
  text-align: center;
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

.detail-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}

.tab-button {
  border: 1px solid #dbe3f0;
  border-radius: 999px;
  background: #f8fafc;
  color: #475569;
  padding: 8px 14px;
  cursor: pointer;
  font-weight: 600;
}

.tab-button.active {
  background: #059669;
  color: #ffffff;
  border-color: #059669;
}
</style>
