<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { paymentApi } from "../api/client";

const router = useRouter();
const items = ref([]);
const selectedItem = ref(null);
const isLoading = ref(true);
const errorMessage = ref("");
const actionMessage = ref("");
const activePaymentOrderId = ref("");
const activeAction = ref("");
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const filters = ref({
  paymentOrderId: "",
  orderNo: "",
  paymentMethod: "全部",
  status: "全部"
});

const metrics = computed(() => ({
  total: total.value,
  successTotal: items.value.filter((item) => item.status === "SUCCESS").length,
  callbackPendingTotal: items.value.filter((item) => item.status === "WAIT_CALLBACK").length,
  amountTotal: items.value.reduce((sum, item) => sum + Number(item.amount || 0), 0).toFixed(2)
}));

function resetFilters() {
  filters.value = {
    paymentOrderId: "",
    orderNo: "",
    paymentMethod: "全部",
    status: "全部"
  };
  pageNo.value = 1;
  refreshList();
}

async function refreshList() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const result = await paymentApi.getList({
      paymentOrderId: filters.value.paymentOrderId,
      orderNo: filters.value.orderNo,
      paymentMethod: filters.value.paymentMethod,
      status: filters.value.status,
      pageNo: pageNo.value,
      pageSize
    });
    items.value = result.items;
    total.value = result.total;
    selectedItem.value = result.items[0] || null;
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

function applyFilters() {
  pageNo.value = 1;
  refreshList();
}

function goToPage(nextPage) {
  if (nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  refreshList();
}

function openDetail(paymentOrderId) {
  router.push(`/payments/${paymentOrderId}`);
}

function pickItem(item) {
  selectedItem.value = item;
}

function isActionRunning(paymentOrderId, actionName) {
  return activePaymentOrderId.value === paymentOrderId && activeAction.value === actionName;
}

async function handleQuery(paymentOrderId) {
  activePaymentOrderId.value = paymentOrderId;
  activeAction.value = "query";
  try {
    const paymentDetail = await paymentApi.query(paymentOrderId);
    actionMessage.value = `支付单 ${paymentOrderId} 当前状态为 ${paymentDetail.status}。`;
    await refreshList();
  } catch (error) {
    actionMessage.value = `支付单 ${paymentOrderId} 查单失败：${error.message}`;
  } finally {
    activePaymentOrderId.value = "";
    activeAction.value = "";
  }
}

async function handleClose(paymentOrderId) {
  activePaymentOrderId.value = paymentOrderId;
  activeAction.value = "close";
  try {
    const paymentDetail = await paymentApi.close(paymentOrderId);
    actionMessage.value = paymentDetail.status === "CLOSED"
      ? `支付单 ${paymentOrderId} 已关闭。`
      : `支付单 ${paymentOrderId} 当前状态为 ${paymentDetail.status}，未执行关闭。`;
    await refreshList();
  } catch (error) {
    actionMessage.value = `支付单 ${paymentOrderId} 关闭失败：${error.message}`;
  } finally {
    activePaymentOrderId.value = "";
    activeAction.value = "";
  }
}

async function handleCallback(paymentOrderId) {
  activePaymentOrderId.value = paymentOrderId;
  activeAction.value = "callback";
  try {
    const paymentDetail = await paymentApi.callback("WX_H5", paymentOrderId);
    actionMessage.value = `支付单 ${paymentOrderId} 已模拟回调，当前状态为 ${paymentDetail.status}。`;
    await refreshList();
  } catch (error) {
    actionMessage.value = `支付单 ${paymentOrderId} 模拟回调失败：${error.message}`;
  } finally {
    activePaymentOrderId.value = "";
    activeAction.value = "";
  }
}

function exportPayments() {
  const exportUrl = paymentApi.buildExportUrl({
    paymentOrderId: filters.value.paymentOrderId,
    orderNo: filters.value.orderNo,
    paymentMethod: filters.value.paymentMethod,
    status: filters.value.status
  });
  window.open(exportUrl, "_blank", "noopener,noreferrer");
}

onMounted(refreshList);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付单管理</h2>
        <p>统一查看支付单、支付方式、渠道交易和回调收敛情况</p>
      </div>
      <button class="button primary" @click="exportPayments">导出支付单</button>
    </div>

    <section class="card-grid">
      <article class="card">
        <p class="card-title">支付单总数</p>
        <p class="card-value">{{ metrics.total }}</p>
      </article>
      <article class="card">
        <p class="card-title">支付成功</p>
        <p class="card-value">{{ metrics.successTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">待回调</p>
        <p class="card-value">{{ metrics.callbackPendingTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">实付金额合计</p>
        <p class="card-value">{{ metrics.amountTotal }}</p>
      </article>
    </section>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        支付单数据加载失败：{{ errorMessage }}
      </div>
      <div v-if="actionMessage" class="state-banner">
        {{ actionMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>支付单号</label>
          <input v-model="filters.paymentOrderId" placeholder="请输入支付单号" />
        </div>
        <div class="field">
          <label>订单号</label>
          <input v-model="filters.orderNo" placeholder="请输入订单号" />
        </div>
        <div class="field">
          <label>支付方式</label>
          <select v-model="filters.paymentMethod">
            <option>全部</option>
            <option>微信</option>
            <option>支付宝</option>
            <option>银行转账</option>
            <option>待选渠道</option>
          </select>
        </div>
        <div class="field">
          <label>支付状态</label>
          <select v-model="filters.status">
            <option>全部</option>
            <option>SUCCESS</option>
            <option>WAIT_CALLBACK</option>
            <option>FAIL</option>
            <option>CLOSED</option>
            <option>PREPAY_CREATED</option>
          </select>
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="applyFilters">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">支付单数据加载中...</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的支付单数据</div>

      <div v-else class="detail-layout">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>支付单号</th>
                <th>订单号</th>
                <th>用户</th>
                <th>实付金额</th>
                <th>支付方式</th>
                <th>支付渠道</th>
                <th>渠道交易号</th>
                <th>支付状态</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.paymentOrderId">
                <td>{{ item.paymentOrderId }}</td>
                <td>{{ item.orderNo }}</td>
                <td>{{ item.customerName }}</td>
                <td>{{ item.amount }}</td>
                <td>{{ item.paymentMethod }}</td>
                <td>{{ item.channel }}</td>
                <td>{{ item.channelTransactionNo }}</td>
                <td><span :class="['badge', item.statusType]">{{ item.status }}</span></td>
                <td>{{ item.createdAt }}</td>
                <td>
                  <div class="list-actions">
                    <button class="link-button" @click="pickItem(item)">快照</button>
                    <button class="link-button" @click="openDetail(item.paymentOrderId)">详情</button>
                    <button class="link-button" :disabled="activePaymentOrderId === item.paymentOrderId" @click="handleQuery(item.paymentOrderId)">
                      {{ isActionRunning(item.paymentOrderId, "query") ? "查单中..." : "查单" }}
                    </button>
                    <button class="link-button" :disabled="activePaymentOrderId === item.paymentOrderId" @click="handleCallback(item.paymentOrderId)">
                      {{ isActionRunning(item.paymentOrderId, "callback") ? "回调中..." : "回调" }}
                    </button>
                    <button class="link-button" :disabled="activePaymentOrderId === item.paymentOrderId" @click="handleClose(item.paymentOrderId)">
                      {{ isActionRunning(item.paymentOrderId, "close") ? "关闭中..." : "关闭" }}
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <aside class="detail-side">
          <div v-if="selectedItem" class="detail-stack">
            <div class="section-title">
              <h3>支付单快照</h3>
              <span class="meta">{{ selectedItem.paymentOrderId }}</span>
            </div>
            <div class="detail-grid">
              <div class="detail-card"><span>订单号</span><strong>{{ selectedItem.orderNo }}</strong></div>
              <div class="detail-card"><span>客户名称</span><strong>{{ selectedItem.customerName }}</strong></div>
              <div class="detail-card"><span>实付金额</span><strong>{{ selectedItem.amount }}</strong></div>
              <div class="detail-card"><span>支付方式</span><strong>{{ selectedItem.paymentMethod }}</strong></div>
              <div class="detail-card"><span>支付渠道</span><strong>{{ selectedItem.channel }}</strong></div>
              <div class="detail-card"><span>渠道交易号</span><strong>{{ selectedItem.channelTransactionNo || "—" }}</strong></div>
              <div class="detail-card"><span>支付状态</span><strong>{{ selectedItem.status }}</strong></div>
              <div class="detail-card"><span>创建时间</span><strong>{{ selectedItem.createdAt }}</strong></div>
            </div>
            <div class="ops-card">
              <div class="ops-title">排障建议</div>
              <div class="ops-row"><span>优先动作</span><span>查单、模拟回调、关闭单据</span></div>
              <div class="ops-row"><span>重点核对</span><span>支付状态、渠道流水号、回调是否收口</span></div>
              <div class="ops-row"><span>典型场景</span><span>待回调长时间未收口、重复支付保护、关闭单保护</span></div>
            </div>
          </div>
          <div v-else class="state-box">选择左侧支付单后，可在这里查看支付单快照与排障建议。</div>
        </aside>
      </div>
      <div v-if="total > pageSize" class="pager">
        <span>共 {{ total }} 条支付单</span>
        <button class="button secondary" :disabled="pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
        <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button class="button secondary" :disabled="pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) 360px;
  gap: 16px;
}

.detail-side {
  display: grid;
  align-self: start;
}

.detail-stack {
  display: grid;
  gap: 16px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-card {
  padding: 14px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid #dbe3f0;
}

.detail-card span {
  display: block;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.detail-card strong {
  color: #0f172a;
}

.ops-card {
  padding: 16px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #dbe3f0;
}

.ops-title {
  margin-bottom: 10px;
  font-weight: 700;
}

.ops-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px dashed #dbe3f0;
}

.ops-row:last-child {
  border-bottom: 0;
}
</style>
