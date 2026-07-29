<script setup>
import { onMounted, ref } from "vue";
import { eventApi, orderApi } from "../api/client";

const rows = ref([]);
const relatedOrders = ref([]);
const selectedEvent = ref(null);
const selectedOrderDetail = ref(null);
const message = ref("");
const loading = ref(false);
const actionLoading = ref(false);
const relatedLoading = ref(false);
const detailLoading = ref(false);
const relatedMessage = ref("");
const filters = ref({ eventType: "", bizNo: "" });
const form = ref({
  paymentOrderId: "PAY202607200188",
  orderNo: "ORD202607200188",
  batchDate: "2026-07-20",
  customerName: "孙女士",
  merchantName: "徐汇门店",
  workerName: "陈阿姨",
  amount: 188
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
  relatedOrders.value = [];
  selectedOrderDetail.value = null;
  relatedLoading.value = true;
  relatedMessage.value = "";
  try {
    const result = await orderApi.getList({ paymentOrderId: eventRow.bizNo, pageNo: 1, pageSize: 20 });
    relatedOrders.value = result.items;
    if (result.items.length > 0) {
      await loadOrderDetail(result.items[0].clearingNo);
    }
  } catch (error) {
    relatedMessage.value = error.message;
  } finally {
    relatedLoading.value = false;
  }
}

async function loadOrderDetail(clearingNo) {
  detailLoading.value = true;
  relatedMessage.value = "";
  try {
    selectedOrderDetail.value = await orderApi.getDetail(clearingNo);
  } catch (error) {
    selectedOrderDetail.value = null;
    relatedMessage.value = error.message;
  } finally {
    detailLoading.value = false;
  }
}

async function consumeEvent() {
  actionLoading.value = true;
  message.value = "";
  try {
    const createdEvent = await eventApi.consumePaymentSuccess(form.value);
    await loadRows();
    await loadRelatedOrders(createdEvent);
    message.value = `支付成功事件 ${createdEvent.bizNo} 已消费，并生成清分结果`;
  } catch (error) {
    message.value = error.message;
  } finally {
    actionLoading.value = false;
  }
}

function useHousekeepingOrderCase() {
  form.value = {
    paymentOrderId: "PAY202607280688",
    orderNo: "ORD202607280688",
    batchDate: "2026-07-28",
    customerName: "刘女士",
    merchantName: "闵行门店",
    workerName: "张阿姨",
    amount: 268
  };
}

function useDeepCleanCase() {
  form.value = {
    paymentOrderId: "PAY202607281188",
    orderNo: "ORD202607281188",
    batchDate: "2026-07-28",
    customerName: "周先生",
    merchantName: "浦东保洁中心",
    workerName: "陈阿姨",
    amount: 388
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
        <h2>清分事件</h2>
        <p>消费支付成功事件并联动查看清分结果、分账明细和费用拆解</p>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>事件类型</label><input v-model="filters.eventType" placeholder="例如：PAYMENT_SUCCESS" /></div>
        <div class="field"><label>业务单号</label><input v-model="filters.bizNo" placeholder="请输入支付单号" /></div>
        <div class="summary-box">
          <strong>链路说明</strong>
          <span>事件消费后，需在同页确认是否生成清分结果，以及各收款方分账金额是否正确。</span>
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadRows">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>
      <div class="toolbar event-form-grid">
        <div class="field"><label>支付单号</label><input v-model="form.paymentOrderId" /></div>
        <div class="field"><label>订单号</label><input v-model="form.orderNo" /></div>
        <div class="field"><label>批次日期</label><input v-model="form.batchDate" /></div>
        <div class="field"><label>客户姓名</label><input v-model="form.customerName" /></div>
        <div class="field"><label>商家名称</label><input v-model="form.merchantName" /></div>
        <div class="field"><label>服务者名称</label><input v-model="form.workerName" /></div>
        <div class="field"><label>订单金额</label><input v-model="form.amount" /></div>
        <div class="toolbar-actions">
          <button class="button secondary" :disabled="actionLoading" @click="useHousekeepingOrderCase">日常保洁示例</button>
          <button class="button secondary" :disabled="actionLoading" @click="useDeepCleanCase">深度保洁示例</button>
          <button class="button warn" :disabled="actionLoading" @click="consumeEvent">模拟支付成功事件</button>
        </div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">清分事件加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>事件号</th><th>类型</th><th>业务单号</th><th>摘要</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.eventNo">
              <td>{{ row.eventNo }}</td><td>{{ row.eventType }}</td><td>{{ row.bizNo }}</td><td>{{ row.summary }}</td>
              <td><span class="badge" :class="row.statusType">{{ row.eventStatus }}</span></td><td>{{ row.createdAt }}</td>
              <td><button class="button secondary button-inline" :disabled="relatedLoading" @click="loadRelatedOrders(row)">查看清分结果</button></td>
            </tr>
            <tr v-if="rows.length === 0">
              <td colspan="7" class="empty-cell">当前筛选条件下暂无清分事件。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="panel">
      <div class="topbar topbar-inner">
        <div>
          <h3>事件关联清分结果</h3>
          <p>{{ selectedEvent ? `当前支付单号：${selectedEvent.bizNo}` : "请选择上方事件查看关联清分结果" }}</p>
        </div>
      </div>
      <div v-if="!selectedEvent" class="state-box">点击上方事件后，可查看该支付单生成的清分结果和分账明细。</div>
      <div v-else-if="relatedMessage" class="state-box">{{ relatedMessage }}</div>
      <div v-else-if="relatedLoading" class="state-box">关联清分结果加载中...</div>
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
            <strong>事件状态</strong>
            <span>{{ selectedEvent.eventStatus }}</span>
          </div>
        </div>
        <div class="state-box payload-box">
          <strong>事件载荷</strong>
          <span>{{ selectedEvent.payload }}</span>
        </div>
        <div class="table-wrap">
          <table>
            <thead><tr><th>清分单号</th><th>订单号</th><th>订单金额</th><th>商家</th><th>服务者</th><th>平台</th><th>渠道费</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="row in relatedOrders" :key="row.clearingNo">
                <td>{{ row.clearingNo }}</td><td>{{ row.orderNo }}</td><td class="amount">{{ row.orderAmount }}</td><td class="amount">{{ row.merchantAmount }}</td><td class="amount">{{ row.workerAmount }}</td><td class="amount">{{ row.platformAmount }}</td><td class="amount deduct">{{ row.channelFeeAmount }}</td>
                <td><span class="badge" :class="row.clearingStatusType">{{ row.clearingStatus }}</span></td>
                <td><button class="button secondary button-inline" :disabled="detailLoading" @click="loadOrderDetail(row.clearingNo)">查看分账明细</button></td>
              </tr>
              <tr v-if="relatedOrders.length === 0">
                <td colspan="9" class="empty-cell">该支付事件暂未生成清分结果。</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="detailLoading" class="state-box detail-loading">清分详情加载中...</div>
        <template v-else-if="selectedOrderDetail">
          <div class="detail-columns">
            <div class="detail-panel">
              <h4>费用规则快照</h4>
              <div class="table-wrap">
                <table>
                  <thead><tr><th>费用名称</th><th>费用类型</th><th>模式</th><th>费率</th><th>固定金额</th><th>承担方</th></tr></thead>
                  <tbody>
                    <tr v-for="row in selectedOrderDetail.feeRules" :key="row.feeRuleNo">
                      <td>{{ row.feeName }}</td><td>{{ row.feeType }}</td><td>{{ row.feeMode }}</td><td>{{ row.feeRate }}</td><td>{{ row.fixedAmount }}</td><td>{{ row.feeBearer }}</td>
                    </tr>
                    <tr v-if="selectedOrderDetail.feeRules.length === 0">
                      <td colspan="6" class="empty-cell">当前清分单暂无费用规则快照。</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <div class="detail-panel">
              <h4>分账明细</h4>
              <div class="table-wrap">
                <table>
                  <thead><tr><th>分账类型</th><th>对象编号</th><th>对象名称</th><th>金额</th><th>状态</th></tr></thead>
                  <tbody>
                    <tr v-for="row in selectedOrderDetail.shareItems" :key="row.shareItemNo">
                      <td>{{ row.shareType }}</td><td>{{ row.shareTargetNo }}</td><td>{{ row.shareTargetName }}</td><td class="amount">{{ row.shareAmount }}</td><td><span class="badge" :class="row.statusType">{{ row.shareStatus }}</span></td>
                    </tr>
                    <tr v-if="selectedOrderDetail.shareItems.length === 0">
                      <td colspan="5" class="empty-cell">当前清分单暂无分账明细。</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </template>
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

.detail-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.detail-panel h4 {
  margin: 0 0 12px;
  font-size: 16px;
}

.detail-loading {
  margin-top: 16px;
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
