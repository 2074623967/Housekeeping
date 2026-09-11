<script setup>
import { computed, onMounted, ref } from "vue";
import { refundApi } from "../api/client";

const items = ref([]);
const selectedItem = ref(null);
const isLoading = ref(true);
const hasLoadedData = ref(false);
const loadErrorMessage = ref("");
const actionRefreshErrorMessage = ref("");
const actionErrorMessage = ref("");
const actionResultMessage = ref("");
const activeRefundOrderId = ref("");
const activeAction = ref("");
const refundOperator = ref("");
const total = ref(0);
const pageNo = ref(1);
const loadedPageNo = ref(1);
const pageSize = 20;
const applyForm = ref({
  paymentOrderId: "",
  refundAmount: "",
  refundMethod: "原路退款",
  refundReason: ""
});
const filters = ref({
  refundOrderId: "",
  paymentOrderId: "",
  refundStatus: "全部",
  refundMethod: "全部"
});

const metrics = computed(() => ({
  total: total.value,
  reviewingTotal: items.value.filter((item) => item.status === "REVIEWING").length,
  failTotal: items.value.filter((item) => item.status === "FAIL").length,
  refundAmountTotal: items.value.reduce((sum, item) => sum + Number(item.refundAmount || 0), 0).toFixed(2)
}));

const isActionRunningOnPage = computed(() => Boolean(activeAction.value));
const isSnapshotStale = computed(() => Boolean(
  hasLoadedData.value && (loadErrorMessage.value || actionRefreshErrorMessage.value)
));
const isWriteDisabled = computed(() => (
  !hasLoadedData.value || isLoading.value || isSnapshotStale.value || isActionRunningOnPage.value
));

function buildQuery() {
  return {
    refundOrderId: filters.value.refundOrderId,
    paymentOrderId: filters.value.paymentOrderId,
    refundStatus: filters.value.refundStatus,
    refundMethod: filters.value.refundMethod,
    pageNo: pageNo.value,
    pageSize
  };
}

function isActionRunning(refundOrderId, actionName) {
  return activeRefundOrderId.value === refundOrderId && activeAction.value === actionName;
}

function resetFilters() {
  filters.value = {
    refundOrderId: "",
    paymentOrderId: "",
    refundStatus: "全部",
    refundMethod: "全部"
  };
  pageNo.value = 1;
  loadRefunds();
}

function applyFilters() {
  pageNo.value = 1;
  loadRefunds();
}

async function loadRefunds(
  preferredRefundOrderId = selectedItem.value?.refundOrderId,
  afterAction = false,
  query = buildQuery()
) {
  isLoading.value = true;
  try {
    const result = await refundApi.getList(query);
    items.value = result.items;
    total.value = result.total;
    selectedItem.value = result.items.find((item) => item.refundOrderId === preferredRefundOrderId)
      || result.items[0]
      || null;
    loadedPageNo.value = query.pageNo;
    hasLoadedData.value = true;
    loadErrorMessage.value = "";
    actionRefreshErrorMessage.value = "";
    if (!afterAction) {
      actionErrorMessage.value = "";
      actionResultMessage.value = "";
    }
  } catch (error) {
    if (afterAction) {
      actionRefreshErrorMessage.value = error.message;
    } else {
      loadErrorMessage.value = error.message;
      actionRefreshErrorMessage.value = "";
    }
    if (hasLoadedData.value) {
      pageNo.value = loadedPageNo.value;
    }
  } finally {
    isLoading.value = false;
  }
}

function pickItem(item) {
  selectedItem.value = item;
}

function exportRefunds() {
  const exportUrl = refundApi.buildExportUrl({
    refundOrderId: filters.value.refundOrderId,
    paymentOrderId: filters.value.paymentOrderId,
    refundStatus: filters.value.refundStatus,
    refundMethod: filters.value.refundMethod
  });
  window.open(exportUrl, "_blank", "noopener,noreferrer");
}

function toRefundListItem(refund) {
  return {
    refundOrderId: refund.refundOrderId,
    paymentOrderId: refund.paymentOrderId,
    orderNo: refund.orderNo,
    customerName: refund.customerName,
    refundAmount: refund.refundAmount,
    refundMethod: refund.refundMethod,
    status: refund.status,
    statusType: refund.statusType,
    appliedAt: refund.appliedAt,
    successAt: refund.successAt
  };
}

function updateRefundSnapshot(refund) {
  const updatedItem = toRefundListItem(refund);
  items.value = items.value.map((item) => item.refundOrderId === refund.refundOrderId
    ? updatedItem
    : item);
  selectedItem.value = updatedItem;
}

async function handleApply() {
  const operator = refundOperator.value.trim();
  if (!operator) {
    actionErrorMessage.value = "请先输入操作人。";
    actionResultMessage.value = "";
    return;
  }
  activeAction.value = "apply";
  actionErrorMessage.value = "";
  actionResultMessage.value = "";
  try {
    const refund = await refundApi.apply({
      paymentOrderId: applyForm.value.paymentOrderId,
      refundAmount: Number(applyForm.value.refundAmount),
      refundMethod: applyForm.value.refundMethod,
      refundReason: applyForm.value.refundReason,
      operator
    });
    actionResultMessage.value = `退款单 ${refund.refundOrderId} 已创建，等待审核。`;
    applyForm.value = {
      paymentOrderId: "",
      refundAmount: "",
      refundMethod: "原路退款",
      refundReason: ""
    };
    pageNo.value = 1;
    actionRefreshErrorMessage.value = "";
    await loadRefunds(refund.refundOrderId, true, buildQuery());
  } catch (error) {
    actionErrorMessage.value = `发起退款失败：${error.message}`;
  } finally {
    activeAction.value = "";
  }
}

async function runRefundAction(refundOrderId, actionName, actionLabel, actionRunner) {
  const operator = refundOperator.value.trim();
  if (!operator) {
    actionErrorMessage.value = "请先输入操作人。";
    actionResultMessage.value = "";
    return;
  }
  const query = buildQuery();
  activeRefundOrderId.value = refundOrderId;
  activeAction.value = actionName;
  actionErrorMessage.value = "";
  actionResultMessage.value = "";
  try {
    const refund = await actionRunner(refundOrderId, operator);
    updateRefundSnapshot(refund);
    actionResultMessage.value = `退款单 ${refund.refundOrderId} 已${actionLabel}，当前状态为 ${refund.status}。`;
    actionRefreshErrorMessage.value = "";
    await loadRefunds(refundOrderId, true, query);
  } catch (error) {
    actionErrorMessage.value = `退款单 ${refundOrderId} ${actionLabel}失败：${error.message}`;
  } finally {
    activeRefundOrderId.value = "";
    activeAction.value = "";
  }
}

function goToPage(nextPage) {
  if (nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  loadRefunds();
}

onMounted(loadRefunds);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>退款单管理</h2>
        <p>查看标准退款、部分退款和已结算后退款场景</p>
      </div>
      <div class="toolbar-actions">
        <span v-if="hasLoadedData" class="badge warn">待处理 {{ metrics.reviewingTotal }} 笔</span>
        <button class="button secondary" :disabled="isLoading || isActionRunningOnPage" @click="loadRefunds()">刷新</button>
        <button class="button primary" @click="exportRefunds">导出退款单</button>
      </div>
    </div>

    <section v-if="hasLoadedData" class="card-grid">
      <article class="card">
        <p class="card-title">退款单总数</p>
        <p class="card-value">{{ metrics.total }}</p>
      </article>
      <article class="card">
        <p class="card-title">待审核</p>
        <p class="card-value">{{ metrics.reviewingTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">退款失败</p>
        <p class="card-value">{{ metrics.failTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">退款金额合计</p>
        <p class="card-value">{{ metrics.refundAmountTotal }}</p>
      </article>
    </section>

    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">
        {{ hasLoadedData ? "退款单数据刷新失败，当前展示上次成功快照：" : "退款单数据加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="actionRefreshErrorMessage" class="error-banner">
        退款人工动作结果已保留，但列表刷新失败，当前页面仍包含上次成功快照：{{ actionRefreshErrorMessage }}
      </div>
      <div v-if="actionErrorMessage" class="error-banner">
        {{ actionErrorMessage }}
      </div>
      <div v-if="actionResultMessage" class="success-banner">
        {{ actionResultMessage }}
      </div>

      <div v-if="hasLoadedData" class="sub-panel">
        <div>
          <h3>发起退款</h3>
          <p>仅允许对支付成功订单发起，后端会校验累计退款金额不超过原支付金额。</p>
        </div>
        <div class="toolbar compact-toolbar">
          <div class="field">
            <label>原支付单号</label>
            <input v-model="applyForm.paymentOrderId" :disabled="isWriteDisabled" placeholder="PAY202607190001" />
          </div>
          <div class="field">
            <label>退款金额</label>
            <input v-model="applyForm.refundAmount" :disabled="isWriteDisabled" type="number" min="0.01" step="0.01" placeholder="请输入金额" />
          </div>
          <div class="field">
            <label>退款方式</label>
            <select v-model="applyForm.refundMethod" :disabled="isWriteDisabled">
              <option>原路退款</option>
              <option>线下打款</option>
              <option>退转付</option>
            </select>
          </div>
          <div class="field wide-field">
            <label>退款原因</label>
            <input v-model="applyForm.refundReason" :disabled="isWriteDisabled" placeholder="客户取消服务、服务未履约等" />
          </div>
          <div class="field">
            <label>操作人</label>
            <input v-model="refundOperator" :disabled="isWriteDisabled" maxlength="64" placeholder="请输入操作人" />
          </div>
          <div class="toolbar-actions">
            <button class="button primary" :disabled="isWriteDisabled" @click="handleApply">
              {{ activeAction === "apply" ? "提交中..." : "提交退款申请" }}
            </button>
          </div>
        </div>
      </div>

      <div class="toolbar">
        <div class="field">
          <label>退款单号</label>
          <input v-model="filters.refundOrderId" :disabled="isActionRunningOnPage" placeholder="请输入退款单号" />
        </div>
        <div class="field">
          <label>原支付单号</label>
          <input v-model="filters.paymentOrderId" :disabled="isActionRunningOnPage" placeholder="请输入原支付单号" />
        </div>
        <div class="field">
          <label>退款状态</label>
          <select v-model="filters.refundStatus" :disabled="isActionRunningOnPage">
            <option>全部</option>
            <option>REVIEWING</option>
            <option>PROCESSING</option>
            <option>SUCCESS</option>
            <option>FAIL</option>
          </select>
        </div>
        <div class="field">
          <label>退款方式</label>
          <select v-model="filters.refundMethod" :disabled="isActionRunningOnPage">
            <option>全部</option>
            <option>原路退款</option>
            <option>线下打款</option>
            <option>退转付</option>
          </select>
        </div>
        <div class="toolbar-actions">
          <button class="button primary" :disabled="isLoading || isActionRunningOnPage" @click="applyFilters">查询</button>
          <button class="button secondary" :disabled="isLoading || isActionRunningOnPage" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">退款单数据加载中...</div>

      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的退款单快照</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的退款单数据</div>

      <div v-else class="detail-layout">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>退款单号</th>
                <th>原支付单号</th>
                <th>原订单号</th>
                <th>用户</th>
                <th>退款金额</th>
                <th>退款方式</th>
                <th>退款状态</th>
                <th>申请时间</th>
                <th>成功时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.refundOrderId">
                <td>{{ item.refundOrderId }}</td>
                <td>{{ item.paymentOrderId }}</td>
                <td>{{ item.orderNo }}</td>
                <td>{{ item.customerName }}</td>
                <td>{{ item.refundAmount }}</td>
                <td>{{ item.refundMethod }}</td>
                <td><span :class="['badge', item.statusType]">{{ item.status }}</span></td>
                <td>{{ item.appliedAt }}</td>
                <td>{{ item.successAt }}</td>
                <td>
                  <div class="list-actions">
                    <button class="link-button" @click="pickItem(item)">快照</button>
                    <RouterLink class="link-button" :to="`/refunds/${item.refundOrderId}`">详情</RouterLink>
                    <button
                      class="link-button"
                      :disabled="isWriteDisabled || item.status !== 'PROCESSING'"
                      @click="runRefundAction(item.refundOrderId, 'query', '完成主动查单', refundApi.query)"
                    >
                      {{ isActionRunning(item.refundOrderId, "query") ? "查询中..." : "主动查单" }}
                    </button>
                    <button
                      class="link-button"
                      :disabled="isWriteDisabled || item.status !== 'REVIEWING'"
                      @click="runRefundAction(item.refundOrderId, 'approve', '审核通过', refundApi.approve)"
                    >
                      {{ isActionRunning(item.refundOrderId, "approve") ? "审核中..." : "审核通过" }}
                    </button>
                    <button
                      class="link-button"
                      :disabled="isWriteDisabled || item.status !== 'FAIL'"
                      @click="runRefundAction(item.refundOrderId, 'retry', '重新提交', refundApi.retry)"
                    >
                      {{ isActionRunning(item.refundOrderId, "retry") ? "重试中..." : "重试" }}
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
              <h3>退款快照</h3>
              <span class="meta">{{ selectedItem.refundOrderId }}</span>
            </div>
            <div class="detail-grid">
              <div class="detail-card"><span>原支付单号</span><strong>{{ selectedItem.paymentOrderId }}</strong></div>
              <div class="detail-card"><span>原订单号</span><strong>{{ selectedItem.orderNo }}</strong></div>
              <div class="detail-card"><span>用户</span><strong>{{ selectedItem.customerName }}</strong></div>
              <div class="detail-card"><span>退款金额</span><strong>{{ selectedItem.refundAmount }}</strong></div>
              <div class="detail-card"><span>退款方式</span><strong>{{ selectedItem.refundMethod }}</strong></div>
              <div class="detail-card"><span>退款状态</span><strong>{{ selectedItem.status }}</strong></div>
              <div class="detail-card"><span>申请时间</span><strong>{{ selectedItem.appliedAt }}</strong></div>
              <div class="detail-card"><span>成功时间</span><strong>{{ selectedItem.successAt || "—" }}</strong></div>
            </div>
            <div class="ops-card">
              <div class="ops-title">处理建议</div>
              <div class="ops-row"><span>优先动作</span><span>审核、主动查单、失败重试</span></div>
              <div class="ops-row"><span>重点核对</span><span>退款状态、退款方式、退款金额</span></div>
              <div class="ops-row"><span>典型场景</span><span>部分退款、原路退款失败、自动重试承接</span></div>
            </div>
          </div>
          <div v-else class="state-box">选择左侧退款单后，可在这里查看退款快照与处理建议。</div>
        </aside>
      </div>
      <div v-if="hasLoadedData && total > pageSize" class="pager">
        <span>共 {{ total }} 条退款单</span>
        <button class="button secondary" :disabled="pageNo === 1 || isLoading || isActionRunningOnPage || isSnapshotStale" @click="goToPage(pageNo - 1)">上一页</button>
        <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button class="button secondary" :disabled="pageNo >= Math.ceil(total / pageSize) || isLoading || isActionRunningOnPage || isSnapshotStale" @click="goToPage(pageNo + 1)">下一页</button>
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
