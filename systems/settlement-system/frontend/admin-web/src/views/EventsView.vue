<script setup>
import { onMounted, ref } from "vue";
import { eventApi, orderApi } from "../api/client";

const rows = ref([]);
const relatedOrders = ref([]);
const selectedEvent = ref(null);
const message = ref("");
const loading = ref(false);
const actionLoading = ref(false);
const relatedLoading = ref(false);
const relatedMessage = ref("");
const filters = ref({ eventType: "", bizNo: "" });
const form = ref({
  clearingNo: "CLO20001",
  paymentOrderId: "PAY202607200001",
  targetType: "WORKER",
  targetNo: "WRK1001",
  targetName: "李阿姨",
  shouldSettleAmount: 120,
  deductAmount: 8,
  netSettleAmount: 112
});

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const result = await eventApi.getList({ ...filters.value, pageNo: 1, pageSize: 20 });
    rows.value = result.items;
    if (selectedEvent.value) {
      const latestEvent = result.items.find((item) => item.eventNo === selectedEvent.value.eventNo);
      if (latestEvent) {
        selectedEvent.value = latestEvent;
      }
    }
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function loadRelatedOrders(eventRow) {
  selectedEvent.value = eventRow;
  relatedLoading.value = true;
  relatedMessage.value = "";
  try {
    const result = await orderApi.getList({ clearingNo: eventRow.bizNo, pageNo: 1, pageSize: 20 });
    relatedOrders.value = result.items;
  } catch (error) {
    relatedOrders.value = [];
    relatedMessage.value = error.message;
  } finally {
    relatedLoading.value = false;
  }
}

async function consumeEvent() {
  actionLoading.value = true;
  message.value = "";
  try {
    const createdEvent = await eventApi.consumeClearingGenerated(form.value);
    await loadRows();
    await loadRelatedOrders(createdEvent);
    message.value = `清分事件 ${createdEvent.bizNo} 已消费，并生成关联结算单`;
  } catch (error) {
    message.value = error.message;
  } finally {
    actionLoading.value = false;
  }
}

function useDemoWorkerCase() {
  form.value = {
    clearingNo: "CLO20088",
    paymentOrderId: "PAY202607280088",
    targetType: "WORKER",
    targetNo: "WRK2088",
    targetName: "王阿姨",
    shouldSettleAmount: 156,
    deductAmount: 12,
    netSettleAmount: 144
  };
}

function useDemoMerchantCase() {
  form.value = {
    clearingNo: "CLO30018",
    paymentOrderId: "PAY202607280318",
    targetType: "MERCHANT",
    targetNo: "MCH3018",
    targetName: "杭州滨江门店",
    shouldSettleAmount: 68,
    deductAmount: 0,
    netSettleAmount: 68
  };
}

function resetFilters() {
  filters.value = { eventType: "", bizNo: "" };
  loadRows();
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>结算事件</h2>
        <p>承接清分结果事件，并追踪事件是否成功生成结算单</p>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div class="field">
          <label>事件类型</label>
          <input v-model="filters.eventType" placeholder="例如：CLEARING_GENERATED" />
        </div>
        <div class="field">
          <label>业务单号</label>
          <input v-model="filters.bizNo" placeholder="请输入 clearingNo" />
        </div>
        <div class="summary-box">
          <strong>链路目标</strong>
          <span>事件消费后应能在下方直接看到关联结算单，确认跨系统链路打通。</span>
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadRows">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>
      <div class="toolbar event-form-grid">
        <div class="field"><label>清分单号</label><input v-model="form.clearingNo" /></div>
        <div class="field"><label>支付单号</label><input v-model="form.paymentOrderId" /></div>
        <div class="field"><label>对象类型</label><input v-model="form.targetType" /></div>
        <div class="field"><label>对象编号</label><input v-model="form.targetNo" /></div>
        <div class="field"><label>对象名称</label><input v-model="form.targetName" /></div>
        <div class="field"><label>应结金额</label><input v-model="form.shouldSettleAmount" /></div>
        <div class="field"><label>扣减金额</label><input v-model="form.deductAmount" /></div>
        <div class="field"><label>实结金额</label><input v-model="form.netSettleAmount" /></div>
        <div class="toolbar-actions">
          <button class="button secondary" :disabled="actionLoading" @click="useDemoWorkerCase">服务者示例</button>
          <button class="button secondary" :disabled="actionLoading" @click="useDemoMerchantCase">商家示例</button>
          <button class="button warn" :disabled="actionLoading" @click="consumeEvent">消费清分事件</button>
        </div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">结算事件加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>事件号</th>
              <th>事件类型</th>
              <th>业务单号</th>
              <th>摘要</th>
              <th>状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.eventNo">
              <td>{{ row.eventNo }}</td>
              <td>{{ row.eventType }}</td>
              <td>{{ row.bizNo }}</td>
              <td>{{ row.summary }}</td>
              <td><span class="badge" :class="row.statusType">{{ row.eventStatus }}</span></td>
              <td>{{ row.createdAt }}</td>
              <td><button class="button secondary button-inline" :disabled="relatedLoading" @click="loadRelatedOrders(row)">查看关联结算单</button></td>
            </tr>
            <tr v-if="rows.length === 0">
              <td colspan="7" class="empty-cell">当前筛选条件下暂无结算事件。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="panel">
      <div class="topbar topbar-inner">
        <div>
          <h3>事件关联结算单</h3>
          <p>{{ selectedEvent ? `当前业务单号：${selectedEvent.bizNo}` : "请选择上方事件查看生成的结算单" }}</p>
        </div>
      </div>
      <div v-if="!selectedEvent" class="state-box">点击上方事件后，可查看该清分事件生成的结算单列表。</div>
      <div v-else-if="relatedMessage" class="state-box">{{ relatedMessage }}</div>
      <div v-else-if="relatedLoading" class="state-box">关联结算单加载中...</div>
      <template v-else>
        <div class="detail-summary-grid">
          <div class="summary-card">
            <strong>事件类型</strong>
            <span>{{ selectedEvent.eventType }}</span>
          </div>
          <div class="summary-card">
            <strong>业务单号</strong>
            <span>{{ selectedEvent.bizNo }}</span>
          </div>
          <div class="summary-card">
            <strong>消费状态</strong>
            <span>{{ selectedEvent.eventStatus }}</span>
          </div>
        </div>
        <div class="state-box payload-box">
          <strong>事件载荷</strong>
          <span>{{ selectedEvent.payload }}</span>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>结算单号</th>
                <th>清分单号</th>
                <th>对象类型</th>
                <th>对象编号</th>
                <th>对象名称</th>
                <th>应结</th>
                <th>扣减</th>
                <th>实结</th>
                <th>审核状态</th>
                <th>结算状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in relatedOrders" :key="row.settlementNo">
                <td>{{ row.settlementNo }}</td>
                <td>{{ row.clearingNo }}</td>
                <td>{{ row.targetType }}</td>
                <td>{{ row.targetNo }}</td>
                <td>{{ row.targetName }}</td>
                <td class="amount">{{ row.shouldSettleAmount }}</td>
                <td class="amount deduct">{{ row.deductAmount }}</td>
                <td class="amount">{{ row.netSettleAmount }}</td>
                <td><span class="badge" :class="row.auditStatusType">{{ row.auditStatus }}</span></td>
                <td><span class="badge" :class="row.settlementStatusType">{{ row.settlementStatus }}</span></td>
              </tr>
              <tr v-if="relatedOrders.length === 0">
                <td colspan="10" class="empty-cell">该事件暂未生成关联结算单。</td>
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
  justify-content: center;
  gap: 6px;
  min-height: 72px;
  padding: 12px 14px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: linear-gradient(180deg, #f8fbff 0%, #eff6ff 100%);
  color: #1e3a8a;
}

.event-form-grid {
  grid-template-columns: repeat(5, minmax(0, 1fr));
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

.payload-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.button-inline {
  padding: 8px 12px;
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
