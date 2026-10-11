<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentRecordApi } from "../api/client";
import { paymentRecordDetailRoute } from "../router/detailRoutes";
import { refundsRoute } from "../router/listRoutes";
import { sumPaymentAmounts } from "../utils/paymentAmount";
import { isChannelSubmissionUnaccepted } from "../utils/paymentAction";

const route = useRoute();
const router = useRouter();
const rows = ref([]);
const selectedRow = ref(null);
const total = ref(0);
const pageNo = ref(1);
const loadedPageNo = ref(1);
const loadedQuery = ref(null);
const pageSize = 20;
const isLoading = ref(false);
const hasLoadedData = ref(false);
const errorMessage = ref("");
let latestLoadRequestId = 0;
const filters = ref({
  customerName: "",
  businessOrderNo: "",
  paymentType: "",
  paymentStatus: "全部",
  paymentChannel: "",
  sortField: "createdAt",
  sortOrder: "desc"
});

const pageConfig = computed(() => ({
  title: route.meta.pageTitle || "统一支付记录",
  recordType: route.meta.recordType || "ALL"
}));

const metrics = computed(() => ({
  total: total.value,
  successTotal: rows.value.filter((item) => item.paymentStatus === "支付成功" && !item.successFactIssue).length,
  closedTotal: rows.value.filter((item) => item.paymentStatus === "已关闭").length,
  refundAmountTotal: `¥${sumPaymentAmounts(rows.value.map((item) => item.refundedAmount))}`
}));
const isSnapshotStale = computed(() => hasLoadedData.value && Boolean(errorMessage.value));
const canExport = computed(() => hasLoadedData.value && total.value > 0 && !isSnapshotStale.value);
const canUseSnapshot = computed(() => hasLoadedData.value && !isLoading.value && !isSnapshotStale.value);

const columns = [
  { key: "serialNo", label: "序号", className: "col-serial" },
  { key: "paymentOrderId", label: "支付单号", hint: "支付系统唯一ID", className: "col-payment-id" },
  { key: "businessChainId", label: "业务链路", hint: "跨系统排障链路ID", className: "col-payment-id" },
  { key: "businessOrderNo", label: "业务订单号", hint: "业务方唯一ID", className: "col-business-order" },
  { key: "paymentRequestNo", label: "支付请求号", hint: "商户订单号", className: "col-payment-request" },
  { key: "applicationSourceId", label: "应用来源ID", className: "col-source" },
  { key: "businessLineId", label: "业务线ID", className: "col-line" },
  { key: "externalTransactionNo", label: "外部交易流水号", className: "col-external" },
  { key: "paymentGateway", label: "支付网关", className: "col-gateway" },
  { key: "paymentChannel", label: "支付渠道", className: "col-channel" },
  { key: "paymentType", label: "支付类型", className: "col-type" },
  { key: "paymentStatus", label: "支付状态", className: "col-status" },
  { key: "bankName", label: "银行名", className: "col-bank" },
  { key: "cardNo", label: "卡号", className: "col-card" },
  { key: "channelReturnCode", label: "渠道返回码", className: "col-code" },
  { key: "returnParameterType", label: "返参类型", className: "col-return-type" },
  { key: "parameterValue", label: "参数值", className: "col-parameter" },
  { key: "validityPeriod", label: "有效期", className: "col-validity" },
  { key: "paymentAmount", label: "支付金额", className: "col-amount" },
  { key: "refundCount", label: "退款次数", className: "col-refund-count" },
  { key: "refundedAmount", label: "已退金额", className: "col-refunded" },
  { key: "productName", label: "商品名称", className: "col-product" },
  { key: "userPaymentChannelId", label: "用户支付渠道唯一标识", className: "col-user-channel" },
  { key: "receivingAccount", label: "收款账号", className: "col-account" },
  { key: "notifyUrl", label: "通知地址", className: "col-notify" },
  { key: "callbackMqTopic", label: "支付成功事件主题", className: "col-mq" },
  { key: "expireTime", label: "失效时间", className: "col-time" },
  { key: "createdAt", label: "创建时间", className: "col-time" },
  { key: "updatedAt", label: "更新时间", className: "col-time" },
  { key: "paidAt", label: "支付成功时间", className: "col-time" },
  { key: "userId", label: "用户ID", className: "col-user-id" }
];

function buildQuery() {
  return {
    recordType: pageConfig.value.recordType,
    ...filters.value,
    pageNo: pageNo.value,
    pageSize
  };
}

async function loadRows(query = buildQuery()) {
  const requestId = ++latestLoadRequestId;
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const result = await paymentRecordApi.getList(query);
    if (requestId !== latestLoadRequestId) {
      return;
    }
    rows.value = result.items;
    total.value = result.total;
    selectedRow.value = result.items[0] || null;
    loadedPageNo.value = query.pageNo;
    loadedQuery.value = { ...query };
    hasLoadedData.value = true;
  } catch (error) {
    if (requestId !== latestLoadRequestId) {
      return;
    }
    errorMessage.value = error.message;
    if (hasLoadedData.value) {
      pageNo.value = loadedPageNo.value;
    }
  } finally {
    if (requestId === latestLoadRequestId) {
      isLoading.value = false;
    }
  }
}

function resetFilters() {
  if (isLoading.value) {
    return;
  }
  filters.value = {
    customerName: "",
    businessOrderNo: "",
    paymentType: "",
    paymentStatus: "全部",
    paymentChannel: "",
    sortField: "createdAt",
    sortOrder: "desc"
  };
  pageNo.value = 1;
  loadRows();
}

function applyFilters() {
  if (isLoading.value) {
    return;
  }
  pageNo.value = 1;
  loadRows();
}

function refreshRows() {
  if (isLoading.value) {
    return;
  }
  loadRows();
}

function goToPage(nextPage) {
  if (isLoading.value || isSnapshotStale.value || !loadedQuery.value
      || nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  loadRows({ ...loadedQuery.value, pageNo: nextPage });
}

function exportRows() {
  if (isLoading.value || !canExport.value || !loadedQuery.value) {
    return;
  }
  window.open(paymentRecordApi.buildExportUrl(loadedQuery.value), "_blank", "noopener,noreferrer");
}

function openPayment(row) {
  if (!canUseSnapshot.value || !row?.paymentOrderId) {
    return;
  }
  router.push(paymentRecordDetailRoute(row.paymentOrderId, pageConfig.value.recordType));
}

function openUnverifiedRefunds(row) {
  if (!canUseSnapshot.value || !row?.paymentOrderId || !(row.unverifiedRefundCount > 0)) {
    return;
  }
  router.push(refundsRoute({
    paymentOrderId: row.paymentOrderId,
    refundStatus: "UNVERIFIED_SUCCESS"
  }));
}

function pickRow(row) {
  if (!canUseSnapshot.value) {
    return;
  }
  selectedRow.value = row;
}

function formatCell(row, column) {
  const value = row[column.key];
  return value === null || value === undefined || value === "" ? "—" : value;
}

watch(() => route.meta.recordType, () => {
  pageNo.value = 1;
  loadRows();
});
onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>{{ pageConfig.title }}</h2>
        <p>从收款运营视角统一查看支付记录、退款影响和最近交易快照，减少在多个页面间来回排查。</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" type="button" :disabled="isLoading" @click="refreshRows">
          {{ isLoading ? "刷新中..." : "刷新" }}
        </button>
        <button class="button primary" type="button" :disabled="isLoading || !canExport" @click="exportRows">
          导出记录
        </button>
      </div>
    </div>

    <section v-if="hasLoadedData" class="card-grid">
      <article class="card">
        <p class="card-title">记录总数</p>
        <p class="card-value">{{ metrics.total }}</p>
      </article>
      <article class="card">
        <p class="card-title">本页支付成功</p>
        <p class="card-value">{{ metrics.successTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">本页已关闭</p>
        <p class="card-value">{{ metrics.closedTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">本页已退金额</p>
        <p class="card-value">{{ metrics.refundAmountTotal }}</p>
      </article>
    </section>

    <section class="panel">
      <div class="toolbar">
        <div class="field">
          <label for="record-customer-name">客户姓名</label>
          <input id="record-customer-name" v-model="filters.customerName" placeholder="请输入客户姓名" :disabled="isLoading" />
        </div>
        <div class="field">
          <label for="record-order-no">商户订单号</label>
          <input id="record-order-no" v-model="filters.businessOrderNo" placeholder="请输入商户订单号" :disabled="isLoading" />
        </div>
        <div class="field">
          <label for="record-payment-type">支付类型</label>
          <select id="record-payment-type" v-model="filters.paymentType" :disabled="isLoading">
            <option value="">全部</option>
            <option value="消费支付">消费支付</option>
            <option value="充值支付">充值支付</option>
            <option value="提现补款">提现补款</option>
            <option value="转账补款">转账补款</option>
            <option value="余额补款">余额补款</option>
            <option value="未分类">未分类</option>
          </select>
        </div>
        <div class="field">
          <label for="record-payment-status">支付状态</label>
          <select id="record-payment-status" v-model="filters.paymentStatus" :disabled="isLoading">
            <option value="全部">全部</option>
            <option value="支付成功">支付成功</option>
            <option value="支付中">支付中</option>
            <option value="已关闭">已关闭</option>
            <option value="支付失败">支付失败</option>
          </select>
        </div>
        <div class="field">
          <label for="record-payment-channel">支付渠道</label>
          <input id="record-payment-channel" v-model="filters.paymentChannel" placeholder="请输入支付渠道编码" :disabled="isLoading" />
        </div>
        <div class="field">
          <label for="record-sort-field">排序字段</label>
          <select id="record-sort-field" v-model="filters.sortField" :disabled="isLoading">
            <option value="createdAt">创建时间</option>
            <option value="paymentAmount">支付金额</option>
            <option value="paidAt">支付成功时间</option>
          </select>
        </div>
        <div class="field">
          <label for="record-sort-order">排序方向</label>
          <select id="record-sort-order" v-model="filters.sortOrder" :disabled="isLoading">
            <option value="desc">倒序</option>
            <option value="asc">正序</option>
          </select>
        </div>
        <div class="toolbar-actions">
          <button class="button primary" type="button" :disabled="isLoading" @click="applyFilters">
            {{ isLoading ? "查询中..." : "查询" }}
          </button>
          <button class="button secondary" type="button" :disabled="isLoading" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="errorMessage" class="error-banner">
        {{ hasLoadedData ? "支付记录刷新失败，当前展示上次成功快照：" : "支付记录加载失败：" }}{{ errorMessage }}
      </div>
      <div v-if="isLoading" class="state-box">支付记录加载中...</div>
      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的支付记录快照</div>
      <div v-else-if="!rows.length" class="state-box">暂无符合条件的支付记录</div>
      <div v-else class="detail-layout">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th v-for="column in columns" :key="column.key" :class="column.className">
                  <span>{{ column.label }}</span>
                  <small v-if="column.hint">{{ column.hint }}</small>
                </th>
                <th class="col-operation">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.paymentOrderId">
                <td v-for="column in columns" :key="column.key" :class="column.className">
                  <span :class="{ 'prototype-status': column.key === 'paymentStatus' }">
                    {{ formatCell(row, column) }}
                  </span>
                  <span v-if="column.key === 'paymentStatus' && row.successFactIssue"> · {{ row.successFactIssue }}</span>
                  <span v-if="column.key === 'paymentStatus' && isChannelSubmissionUnaccepted(row.paymentStatus, row.latestAttemptStatus, row.externalTransactionNo)" class="badge warn">渠道受理未确认</span>
                  <span v-if="column.key === 'paymentType' && row.prepayBindingIssue" class="badge warn">{{ row.prepayBindingIssue === "PREPAY_BINDING_MISSING" ? "预付缺失待核查" : "多预付待核查" }}</span>
                  <span v-if="column.key === 'refundedAmount' && row.unverifiedRefundCount > 0" class="badge warn">
                    {{ row.unverifiedRefundCount }} 笔待核查
                  </span>
                </td>
                <td class="col-operation">
                  <button class="link-button" type="button" :disabled="!canUseSnapshot" @click="pickRow(row)">查看快照</button>
                  <button class="link-button" type="button" :disabled="!canUseSnapshot" @click="openPayment(row)">详情</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <aside class="detail-side">
          <div v-if="selectedRow" class="detail-stack">
            <div class="section-title">
              <h3>记录快照</h3>
              <span class="meta">{{ selectedRow.paymentOrderId }}</span>
            </div>
            <div class="detail-grid">
              <div class="detail-card"><span>业务链路</span><strong>{{ selectedRow.businessChainId || "—" }}</strong></div>
              <div class="detail-card"><span>支付场景</span><strong>{{ selectedRow.payScene || "—" }}</strong></div>
              <div v-if="selectedRow.prepayBindingIssue" class="detail-card"><span>预付归属</span><strong>{{ selectedRow.prepayBindingIssue === "PREPAY_BINDING_MISSING" ? "预付归属缺失，待人工核查" : "多预付冲突，待人工核查" }}</strong></div>
              <div class="detail-card"><span>业务订单号</span><strong>{{ selectedRow.businessOrderNo || "—" }}</strong></div>
              <div class="detail-card"><span>支付状态</span><strong>{{ selectedRow.paymentStatus || "—" }} <span v-if="isChannelSubmissionUnaccepted(selectedRow.paymentStatus, selectedRow.latestAttemptStatus, selectedRow.externalTransactionNo)" class="badge warn">渠道受理未确认</span></strong></div>
              <div class="detail-card"><span>最近尝试</span><strong>{{ selectedRow.latestAttemptStatus || "—" }}</strong></div>
              <div v-if="selectedRow.successFactIssue" class="detail-card"><span>支付成功事实</span><strong>{{ selectedRow.successFactIssue }}</strong></div>
              <div class="detail-card"><span>支付类型</span><strong>{{ selectedRow.paymentType || "—" }}</strong></div>
              <div class="detail-card"><span>支付渠道</span><strong>{{ selectedRow.paymentChannel || "—" }}</strong></div>
              <div class="detail-card"><span>支付金额</span><strong>{{ selectedRow.paymentAmount || "—" }}</strong></div>
              <div class="detail-card"><span>已退金额</span><strong>{{ selectedRow.refundedAmount || "—" }}</strong></div>
              <div v-if="selectedRow.unverifiedRefundCount > 0" class="detail-card detail-card-wide">
                <span>成功退款待核查</span><strong>{{ selectedRow.unverifiedRefundCount }} 笔未计入已退</strong>
                <button class="link-button" type="button" :disabled="!canUseSnapshot" @click="openUnverifiedRefunds(selectedRow)">查看待核查退款</button>
              </div>
              <div class="detail-card"><span>外部流水号</span><strong>{{ selectedRow.externalTransactionNo || "—" }}</strong></div>
              <div class="detail-card"><span>支付成功时间</span><strong>{{ selectedRow.paidAt || "—" }}</strong></div>
              <div class="detail-card detail-card-wide"><span>通知地址</span><strong>{{ selectedRow.notifyUrl || "—" }}</strong></div>
              <div class="detail-card detail-card-wide"><span>支付成功事件主题</span><strong>{{ selectedRow.callbackMqTopic || "—" }}</strong></div>
            </div>
            <div class="ops-card">
              <div class="ops-title">运营建议</div>
              <div class="ops-row"><span>优先联查</span><span>{{ selectedRow.successFactIssue ? "支付记录详情 / 支付请求 / 处理日志；成功支付详情不可用" : "支付记录详情 / 支付单详情 / 支付请求" }}</span></div>
              <div class="ops-row"><span>重点核对</span><span>状态、已退金额、外部流水号、通知地址</span></div>
              <div v-if="isChannelSubmissionUnaccepted(selectedRow.paymentStatus, selectedRow.latestAttemptStatus, selectedRow.externalTransactionNo)" class="ops-row"><span>当前阶段</span><span>渠道受理未确认，先核对最近支付尝试与提交日志，等待状态推进；勿主动查单或重复提交。</span></div>
              <div v-else class="ops-row"><span>典型场景</span><span>收款已成功但业务未收口、部分退款后复核</span></div>
            </div>
          </div>
          <div v-else class="state-box">选择左侧支付记录后，可在这里查看记录快照与运营建议。</div>
        </aside>
      </div>

      <div v-if="hasLoadedData && total > pageSize" class="pager">
        <span>共 {{ total }} 条</span>
        <button class="button secondary" type="button" :disabled="isLoading || isSnapshotStale || pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
        <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button class="button secondary" type="button" :disabled="isLoading || isSnapshotStale || pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
small {
  display: block;
  margin-top: 4px;
  color: #94a3b8;
  font-size: 11px;
}

.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) 360px;
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
  word-break: break-word;
}

.detail-card-wide {
  grid-column: 1 / -1;
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

.prototype-empty {
  text-align: center;
  color: #64748b;
}

.prototype-status {
  font-weight: 600;
}
</style>
