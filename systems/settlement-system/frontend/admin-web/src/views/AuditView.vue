<script setup>
import { computed, onMounted, ref } from "vue";
import { orderApi } from "../api/client";

const rows = ref([]);
const message = ref("");
const loading = ref(false);
const actionLoading = ref(false);
const detailLoading = ref(false);
const filters = ref({ batchNo: "", targetType: "", settlementStatus: "待审核" });
const auditForm = ref({ operatorName: "财务主管", auditRemark: "结算资料完整，审核通过" });
const selectedOrder = ref(null);
const selectedDetail = ref(null);
const detailMessage = ref("");

const targetTypeOptions = ["WORKER", "MERCHANT"];
const settlementStatusOptions = ["待审核", "待出款", "已退回", "已出款"];

const pendingCount = computed(() => rows.value.filter((item) => item.auditStatus === "待审核").length);
const rejectedCount = computed(() => rows.value.filter((item) => item.auditStatus === "已退回").length);
const detailItems = computed(() => selectedDetail.value?.items || []);
const detailLogs = computed(() => selectedDetail.value?.auditLogs || []);

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

async function openDetail(row) {
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

async function submitAudit(row, approved) {
  const actionLabel = approved ? "通过" : "驳回";
  if (!window.confirm(`确认${actionLabel}结算单 ${row.settlementNo} 吗？`)) {
    return;
  }
  actionLoading.value = true;
  message.value = "";
  try {
    if (approved) {
      await orderApi.audit(row.settlementNo, auditForm.value);
    } else {
      await orderApi.reject(row.settlementNo, auditForm.value);
    }
    await loadRows();
    await openDetail({ ...row, settlementNo: row.settlementNo });
    message.value = `结算单 ${row.settlementNo} 已${actionLabel}`;
  } catch (error) {
    message.value = error.message;
  } finally {
    actionLoading.value = false;
  }
}

function fillApproveRemark() {
  auditForm.value.auditRemark = "结算资料完整，审核通过";
}

function fillRejectRemark() {
  auditForm.value.auditRemark = "金额或资料异常，退回业务修正";
}

function resetFilters() {
  filters.value = { batchNo: "", targetType: "", settlementStatus: "待审核" };
  loadRows();
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>审核工作台</h2>
        <p>按待审核队列进行财务审核、驳回复核和金额明细核验</p>
      </div>
    </div>

    <section class="panel">
      <div class="card-grid audit-cards">
        <article class="card">
          <p class="card-title">待审核结算单</p>
          <p class="card-value">{{ pendingCount }}</p>
        </article>
        <article class="card">
          <p class="card-title">已退回结算单</p>
          <p class="card-value">{{ rejectedCount }}</p>
        </article>
      </div>
    </section>

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
            <option v-for="item in settlementStatusOptions" :key="item" :value="item">{{ item }}</option>
          </select>
        </div>
        <div class="summary-box">
          <strong>审核策略</strong>
          <span>先点开详情核对金额和日志，再执行通过或驳回，所有动作必须留痕。</span>
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
          <input v-model="auditForm.auditRemark" placeholder="请输入审核原因" />
        </div>
        <div class="toolbar-actions">
          <button class="button secondary" @click="fillApproveRemark">填充通过话术</button>
          <button class="button danger" @click="fillRejectRemark">填充驳回话术</button>
        </div>
      </div>

      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">审核队列加载中...</div>
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
              <th>审核状态</th>
              <th>结算状态</th>
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
              <td><span class="badge" :class="row.auditStatusType">{{ row.auditStatus }}</span></td>
              <td><span class="badge" :class="row.settlementStatusType">{{ row.settlementStatus }}</span></td>
              <td class="actions-cell">
                <button class="button secondary button-inline" :disabled="detailLoading" @click="openDetail(row)">查看详情</button>
                <button class="button primary button-inline" :disabled="actionLoading" @click="submitAudit(row, true)">通过</button>
                <button class="button danger button-inline" :disabled="actionLoading" @click="submitAudit(row, false)">驳回</button>
              </td>
            </tr>
            <tr v-if="rows.length === 0">
              <td colspan="11" class="empty-cell">当前筛选条件下暂无审核单据。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="panel">
      <div class="topbar topbar-inner">
        <div>
          <h3>审核详情面板</h3>
          <p>{{ selectedOrder ? `当前结算单：${selectedOrder.settlementNo}` : "请选择上方结算单查看审核明细" }}</p>
        </div>
      </div>
      <div v-if="!selectedOrder" class="state-box">从上方审核队列选择结算单后，可查看金额明细和完整审核日志。</div>
      <div v-else-if="detailMessage" class="state-box">{{ detailMessage }}</div>
      <div v-else-if="detailLoading" class="state-box">审核详情加载中...</div>
      <template v-else-if="selectedDetail">
        <div class="detail-summary-grid">
          <div class="summary-card">
            <strong>对象信息</strong>
            <span>{{ selectedDetail.order.targetName }} / {{ selectedDetail.order.targetNo }}</span>
          </div>
          <div class="summary-card">
            <strong>批次与状态</strong>
            <span>{{ selectedDetail.order.batchNo }} / {{ selectedDetail.order.settlementStatus }}</span>
          </div>
          <div class="summary-card">
            <strong>审核与出款</strong>
            <span>{{ selectedDetail.order.auditStatus }} / {{ selectedDetail.order.payoutStatus }}</span>
          </div>
        </div>

        <div class="detail-columns">
          <div class="detail-panel">
            <h4>金额明细</h4>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>明细项名称</th>
                    <th>明细项类型</th>
                    <th>金额</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in detailItems" :key="`${row.itemName}-${row.itemType}`">
                    <td>{{ row.itemName }}</td>
                    <td>{{ row.itemType }}</td>
                    <td class="amount" :class="{ deduct: row.itemType === '扣减' }">{{ row.amount }}</td>
                  </tr>
                  <tr v-if="detailItems.length === 0">
                    <td colspan="3" class="empty-cell">当前结算单暂无金额明细。</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div class="detail-panel">
            <h4>审核日志</h4>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>操作时间</th>
                    <th>操作动作</th>
                    <th>结果</th>
                    <th>操作人</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in detailLogs" :key="`${row.createdAt}-${row.operatorName}-${row.action}`">
                    <td>{{ row.createdAt }}</td>
                    <td>{{ row.action }}</td>
                    <td>{{ row.result }}</td>
                    <td>{{ row.operatorName }}</td>
                  </tr>
                  <tr v-if="detailLogs.length === 0">
                    <td colspan="4" class="empty-cell">当前结算单暂无审核日志。</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.audit-cards {
  grid-template-columns: repeat(2, minmax(0, 1fr));
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

.detail-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.detail-panel h4 {
  margin: 0 0 12px;
  font-size: 16px;
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
</style>
