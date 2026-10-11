<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import {
  downloadRefundDetail,
  applyRefund,
  dispatchRefundOutbox,
  getOverview,
  getRefundDetail,
  getRefundOutbox,
  getRefunds,
  postRefundAction,
  formatRefundTime
} from "./api";

const activeView = ref("dashboard");
const loading = ref(false);
const activeOperation = ref("");
const errorMessage = ref("");
const overviewError = ref("");
const selectedRefund = ref(null);
const applyForm = reactive({
  paymentOrderId: "",
  refundAmount: "",
  refundMethod: "ORIGINAL",
  refundReason: "",
  idempotencyKey: ""
});
const query = reactive({
  refundOrderId: "",
  paymentOrderId: "",
  status: "",
  refundMethod: "",
  pageNo: 1,
  pageSize: 20
});
const overview = reactive({
  totalCount: 0,
  reviewingCount: 0,
  processingCount: 0,
  failCount: 0,
  successAmount: 0
});
const page = reactive({ items: [], total: 0 });
const outboxQuery = reactive({
  eventId: "",
  aggregateId: "",
  status: "",
  pageNo: 1,
  pageSize: 20
});
const outboxPage = reactive({ items: [], total: 0 });

const pageCount = (result, queryState) =>
  Math.max(1, Math.ceil(Number(result?.total || 0) / Number(queryState.pageSize || 20)));

const changePage = async (queryState, result, nextPage, loader) => {
  const maxPage = pageCount(result, queryState);
  if (loading.value || nextPage < 1 || nextPage > maxPage || nextPage === queryState.pageNo) return;
  queryState.pageNo = nextPage;
  await loader();
};

const statusLabel = {
  REVIEWING: "审核中",
  APPROVED: "已审核",
  PROCESSING: "处理中",
  SUCCESS: "退款成功",
  FAIL: "退款失败"
};
const outboxStatusLabel = {
  PENDING: "待派发",
  SENT: "已派发",
  FAIL: "派发失败"
};

const statusClass = (status, statusType) => `status-${statusType || String(status || "").toLowerCase()}`;
const outboxStatusClass = (status) => `status-${String(status || "").toLowerCase()}`;
const selectedStatus = computed(() =>
  selectedRefund.value ? statusLabel[selectedRefund.value.status] || selectedRefund.value.status : ""
);

async function loadOverview() {
  overviewError.value = "";
  try {
    Object.assign(overview, await getOverview());
  } catch (error) {
    overviewError.value = error.message;
  }
}

async function loadList() {
  loading.value = true;
  errorMessage.value = "";
  try {
    Object.assign(page, await getRefunds(query));
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function loadOutbox() {
  loading.value = true;
  errorMessage.value = "";
  try {
    Object.assign(outboxPage, await getRefundOutbox(outboxQuery));
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function openDetail(item) {
  try {
    selectedRefund.value = await getRefundDetail(item.refundOrderId);
    activeView.value = "detail";
  } catch (error) {
    errorMessage.value = error.message;
  }
}

function resetApplyForm() {
  Object.assign(applyForm, {
    paymentOrderId: "",
    refundAmount: "",
    refundMethod: "ORIGINAL",
    refundReason: "",
    idempotencyKey: ""
  });
}

async function submitApplication() {
  if (activeOperation.value) return;
  activeOperation.value = "refund:apply";
  errorMessage.value = "";
  try {
    const item = await applyRefund({
      paymentOrderId: applyForm.paymentOrderId.trim(),
      refundAmount: Number(applyForm.refundAmount),
      refundMethod: applyForm.refundMethod,
      refundReason: applyForm.refundReason.trim(),
      idempotencyKey: applyForm.idempotencyKey.trim() || undefined
    });
    await loadOverview();
    await loadList();
    selectedRefund.value = await getRefundDetail(item.refundOrderId);
    activeView.value = "detail";
    resetApplyForm();
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    activeOperation.value = "";
  }
}

async function exportDetail(refundOrderId) {
  try {
    const blob = await downloadRefundDetail(refundOrderId);
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `refund-detail-${refundOrderId}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
    errorMessage.value = "";
  } catch (error) {
    errorMessage.value = error.message;
  }
}

async function runAction(action, callbackResult = "SUCCESS") {
  if (!selectedRefund.value || activeOperation.value) return;
  const operationKey = action === "callback"
    ? `refund:callback:${callbackResult}`
    : `refund:${action}`;
  activeOperation.value = operationKey;
  errorMessage.value = "";
  try {
    const payload = action === "callback" && callbackResult === "FAIL"
      ? {
          result: "FAIL",
          failureCode: "LOCAL_CHANNEL_TIMEOUT",
          rawMessage: "本地渠道模拟超时失败回调"
        }
      : action === "callback"
        ? {
            result: "SUCCESS",
            channelRefundId: `LOCAL-${selectedRefund.value.refundOrderId}`,
            rawMessage: "本地渠道成功回调"
          }
        : { remark: "退款中心后台操作" };
    await postRefundAction(selectedRefund.value.refundOrderId, action, {
      ...payload
    });
    await loadOverview();
    await loadList();
    selectedRefund.value = await getRefundDetail(selectedRefund.value.refundOrderId);
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    activeOperation.value = "";
  }
}

async function showList() {
  activeView.value = "list";
  await loadList();
}

async function showOutbox() {
  activeView.value = "outbox";
  await loadOutbox();
}

async function runOutboxDispatch(item, simulateResult) {
  if (activeOperation.value) return;
  activeOperation.value = `outbox:${item.eventId}:${simulateResult}`;
  errorMessage.value = "";
  try {
    await dispatchRefundOutbox(item.eventId, {
      simulateResult,
      remark: simulateResult === "FAIL" ? "本地模拟退款成功事件派发失败" : "本地模拟退款成功事件派发成功"
    });
    await loadOutbox();
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    activeOperation.value = "";
  }
}

onMounted(async () => {
  await loadOverview();
  await loadList();
  await loadOutbox();
});

async function refreshCurrentView() {
  await loadOverview();
  if (activeView.value === "outbox") {
    await loadOutbox();
    return;
  }
  await loadList();
}
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">R</div>
        <div>
          <strong>退款中心</strong>
          <small>REFUND CENTER</small>
        </div>
      </div>
      <button class="nav-item" :class="{ active: activeView === 'dashboard' }" @click="activeView = 'dashboard'">
        <span>概览工作台</span><small>Overview</small>
      </button>
      <button class="nav-item" :class="{ active: activeView === 'list' }" @click="showList">
        <span>退款单管理</span><small>Refund Orders</small>
      </button>
      <button class="nav-item" :class="{ active: activeView === 'apply' }" @click="activeView = 'apply'">
        <span>发起退款</span><small>Apply Refund</small>
      </button>
      <button class="nav-item" :class="{ active: activeView === 'outbox' }" @click="showOutbox">
        <span>成功事件出站</span><small>Outbox Relay</small>
      </button>
      <button class="nav-item" :class="{ active: activeView === 'detail' }" :disabled="!selectedRefund" @click="activeView = 'detail'">
        <span>退款详情</span><small>Operations</small>
      </button>
      <div class="sidebar-note">
        <span>资金逆向交易</span>
        <p>退款事实独立留痕，回调与重试可追踪。</p>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <div>
          <p class="eyebrow">HOME SERVICE PAYMENT PLATFORM / REVERSE FLOW</p>
          <h1>{{ activeView === "detail" ? "退款单详情" : activeView === "list" ? "退款单管理" : activeView === "apply" ? "发起退款" : activeView === "outbox" ? "退款成功事件出站管理" : "退款运营工作台" }}</h1>
        </div>
        <button class="refresh-button" :disabled="loading || !!activeOperation" @click="refreshCurrentView()">{{ loading ? "刷新中..." : "刷新数据" }}</button>
      </header>

      <div v-if="errorMessage" class="error-banner">{{ errorMessage }}</div>
      <div v-if="overviewError" class="error-banner">概览加载失败：{{ overviewError }}</div>

      <section v-if="activeView === 'dashboard'" class="dashboard">
        <div class="hero panel">
          <div>
            <p class="eyebrow accent">REFUND CONTROL TOWER</p>
            <h2>把每一笔退款都变成可核对的资金事实</h2>
            <p>从申请、审核、渠道下发到异步回调，统一在一个工作台完成追踪和补偿。</p>
          </div>
          <button class="primary-button" @click="showList">进入退款作业台</button>
        </div>
        <div class="metric-grid">
          <article class="metric-card"><span>退款单总数</span><strong>{{ overview.totalCount }}</strong><small>当前独立退款域</small></article>
          <article class="metric-card warm"><span>审核中</span><strong>{{ overview.reviewingCount }}</strong><small>待人工确认</small></article>
          <article class="metric-card blue"><span>处理中</span><strong>{{ overview.processingCount }}</strong><small>等待渠道回执</small></article>
          <article class="metric-card red"><span>失败待重试</span><strong>{{ overview.failCount }}</strong><small>需要运营处置</small></article>
        </div>
        <section class="panel guidance">
          <div><span class="section-kicker">PROCESS GUARDRAIL</span><h3>退款状态机</h3></div>
          <div class="flow">
            <span>审核中</span><i>→</i><span>已审核</span><i>→</i><span>处理中</span><i>→</i><span class="success-text">退款成功</span>
            <em>失败可回到处理中重试</em>
          </div>
        </section>
      </section>

      <section v-else-if="activeView === 'apply'" class="panel apply-panel">
        <div class="section-header">
          <div><span class="section-kicker">REFUND APPLICATION</span><h2>发起退款申请</h2></div>
          <span class="count-tip">申请后进入审核中</span>
        </div>
        <form class="apply-form" @submit.prevent="submitApplication">
          <label>原支付单号<input v-model.trim="applyForm.paymentOrderId" required maxlength="64" placeholder="例如 PAY-DEMO-1001" /></label>
          <label>退款金额<input v-model="applyForm.refundAmount" required min="0.01" step="0.01" type="number" placeholder="不能超过原支付金额" /></label>
          <label>退款方式<select v-model="applyForm.refundMethod"><option value="ORIGINAL">原路退款</option><option value="TRANSFER">退转付</option></select></label>
          <label class="wide-field">退款原因<textarea v-model.trim="applyForm.refundReason" maxlength="256" rows="4" placeholder="例如：客户取消服务"></textarea></label>
          <label class="wide-field">幂等键（可选）<input v-model.trim="applyForm.idempotencyKey" maxlength="128" placeholder="同一业务重试时保持不变" /></label>
          <div class="apply-actions">
            <button class="primary-button" type="submit" :disabled="activeOperation === 'refund:apply'">{{ activeOperation === "refund:apply" ? "提交中..." : "提交退款申请" }}</button>
            <button class="secondary-button" type="button" :disabled="activeOperation === 'refund:apply'" @click="resetApplyForm">清空</button>
          </div>
        </form>
        <p class="form-hint">系统会先校验支付成功事实和累计退款金额，申请成功后可在详情页继续审核、提交渠道和处理回调。</p>
      </section>

      <section v-else-if="activeView === 'list'" class="panel">
        <div class="section-header"><div><span class="section-kicker">REFUND ORDERS</span><h2>退款单管理</h2></div><span class="count-tip">共 {{ page.total }} 条</span></div>
        <div class="filters">
          <label>退款单号<input v-model="query.refundOrderId" placeholder="REF..." /></label>
          <label>原支付单号<input v-model="query.paymentOrderId" placeholder="PAY..." /></label>
          <label>状态<select v-model="query.status"><option value="">全部状态</option><option v-for="(label, value) in statusLabel" :key="value" :value="value">{{ label }}</option></select></label>
          <label>退款方式<select v-model="query.refundMethod"><option value="">全部方式</option><option value="ORIGINAL">原路退款</option><option value="TRANSFER">退转付</option></select></label>
          <button class="primary-button compact" @click="query.pageNo = 1; loadList()">查询</button>
        </div>
        <div v-if="loading" class="empty-state">正在加载退款单...</div>
        <div v-else-if="!page.items.length" class="empty-state">暂无符合条件的退款单</div>
        <table v-else class="data-table">
          <thead><tr><th>退款单号</th><th>原支付单</th><th>支付场景</th><th>客户 / 订单</th><th>退款金额</th><th>方式</th><th>状态</th><th>申请时间</th><th>操作</th></tr></thead>
          <tbody><tr v-for="item in page.items" :key="item.refundOrderId">
            <td><button class="link-button" @click="openDetail(item)">{{ item.refundOrderId }}</button></td>
            <td>{{ item.paymentOrderId }}</td>
            <td>{{ item.payScene || "-" }}</td>
            <td><strong>{{ item.customerName }}</strong><small>{{ item.orderNo }}</small></td>
            <td class="money">¥{{ Number(item.refundAmount).toFixed(2) }}</td>
            <td>{{ item.refundMethod === "ORIGINAL" ? "原路退款" : "退转付" }}</td>
            <td><span class="status-pill" :class="statusClass(item.status, item.statusType)">{{ statusLabel[item.status] || item.status }}</span></td>
            <td>{{ formatRefundTime(item.appliedAt) }}</td>
            <td><button class="link-button" @click="openDetail(item)">查看详情</button></td>
          </tr></tbody>
        </table>
        <div v-if="page.total > query.pageSize" class="pagination" aria-label="退款单分页">
          <span>第 {{ query.pageNo }} / {{ pageCount(page, query) }} 页，共 {{ page.total }} 条</span>
          <div>
            <button class="secondary-button" :disabled="loading || query.pageNo <= 1" @click="changePage(query, page, query.pageNo - 1, loadList)">上一页</button>
            <button class="secondary-button" :disabled="loading || query.pageNo >= pageCount(page, query)" @click="changePage(query, page, query.pageNo + 1, loadList)">下一页</button>
          </div>
        </div>
      </section>

      <section v-else-if="activeView === 'outbox'" class="panel">
        <div class="section-header">
          <div><span class="section-kicker">REFUND SUCCESS OUTBOX</span><h2>退款成功事件出站管理</h2></div>
          <span class="count-tip">共 {{ outboxPage.total }} 条</span>
        </div>
        <div class="filters">
          <label>事件编号<input v-model="outboxQuery.eventId" placeholder="REVT..." /></label>
          <label>退款单号<input v-model="outboxQuery.aggregateId" placeholder="REF..." /></label>
          <label>状态<select v-model="outboxQuery.status"><option value="">全部状态</option><option v-for="(label, value) in outboxStatusLabel" :key="value" :value="value">{{ label }}</option></select></label>
          <button class="primary-button compact" @click="outboxQuery.pageNo = 1; loadOutbox()">查询</button>
        </div>
        <div v-if="loading" class="empty-state">正在加载退款成功事件...</div>
        <div v-else-if="!outboxPage.items.length" class="empty-state">暂无符合条件的退款成功事件</div>
        <table v-else class="data-table">
          <thead><tr><th>事件编号</th><th>退款单号</th><th>事件类型</th><th>状态</th><th>重试次数</th><th>最近派发</th><th>错误信息</th><th>操作</th></tr></thead>
          <tbody><tr v-for="item in outboxPage.items" :key="item.eventId">
            <td>{{ item.eventId }}</td>
            <td>{{ item.aggregateId }}</td>
            <td>{{ item.eventType }}</td>
            <td><span class="status-pill" :class="outboxStatusClass(item.status)">{{ outboxStatusLabel[item.status] || item.status }}</span></td>
            <td>{{ item.retryCount }}</td>
            <td>{{ item.lastRelayAt ? item.lastRelayAt.replace("T", " ") : "-" }}</td>
            <td>{{ item.lastErrorMessage || "-" }}</td>
            <td class="table-actions">
              <button v-if="item.status !== 'SENT'" class="link-button" :disabled="!!activeOperation" @click="runOutboxDispatch(item, 'SUCCESS')">{{ activeOperation === `outbox:${item.eventId}:SUCCESS` ? "派发中..." : "模拟派发成功" }}</button>
              <button v-if="item.status !== 'SENT'" class="link-button danger-link" :disabled="!!activeOperation" @click="runOutboxDispatch(item, 'FAIL')">{{ activeOperation === `outbox:${item.eventId}:FAIL` ? "派发中..." : "模拟派发失败" }}</button>
            </td>
          </tr></tbody>
        </table>
        <div v-if="outboxPage.total > outboxQuery.pageSize" class="pagination" aria-label="退款成功事件分页">
          <span>第 {{ outboxQuery.pageNo }} / {{ pageCount(outboxPage, outboxQuery) }} 页，共 {{ outboxPage.total }} 条</span>
          <div>
            <button class="secondary-button" :disabled="loading || outboxQuery.pageNo <= 1" @click="changePage(outboxQuery, outboxPage, outboxQuery.pageNo - 1, loadOutbox)">上一页</button>
            <button class="secondary-button" :disabled="loading || outboxQuery.pageNo >= pageCount(outboxPage, outboxQuery)" @click="changePage(outboxQuery, outboxPage, outboxQuery.pageNo + 1, loadOutbox)">下一页</button>
          </div>
        </div>
      </section>

      <section v-else class="detail-layout">
        <section v-if="selectedRefund" class="panel detail-panel">
          <div class="section-header"><div><span class="section-kicker">REFUND DETAIL</span><h2>{{ selectedRefund.refundOrderId }}</h2></div><span class="status-pill" :class="statusClass(selectedRefund.status, selectedRefund.statusType)">{{ selectedStatus }}</span></div>
          <div class="detail-grid">
            <div><span>原支付单号</span><strong>{{ selectedRefund.paymentOrderId }}</strong></div>
            <div><span>支付场景</span><strong>{{ selectedRefund.payScene || "-" }}</strong></div>
            <div><span>业务订单号</span><strong>{{ selectedRefund.orderNo }}</strong></div>
            <div><span>客户名称</span><strong>{{ selectedRefund.customerName }}</strong></div>
            <div><span>原支付金额</span><strong>¥{{ Number(selectedRefund.paidAmount).toFixed(2) }}</strong></div>
            <div><span>退款金额</span><strong class="money">¥{{ Number(selectedRefund.refundAmount).toFixed(2) }}</strong></div>
            <div><span>退款方式</span><strong>{{ selectedRefund.refundMethod === "ORIGINAL" ? "原路退款" : "退转付" }}</strong></div>
            <div><span>申请时间</span><strong>{{ formatRefundTime(selectedRefund.appliedAt) }}</strong></div>
            <div><span>成功时间</span><strong>{{ formatRefundTime(selectedRefund.successAt) }}</strong></div>
            <div><span>终态时间</span><strong>{{ formatRefundTime(selectedRefund.terminalAt) }}</strong></div>
            <div><span>失败码</span><strong>{{ selectedRefund.failureCode || "-" }}</strong></div>
            <div class="wide"><span>退款原因</span><strong>{{ selectedRefund.refundReason }}</strong></div>
          </div>
          <div class="actions">
            <button class="secondary-button" :disabled="!!activeOperation" @click="exportDetail(selectedRefund.refundOrderId)">导出快照</button>
            <button v-if="selectedRefund.status === 'REVIEWING'" class="primary-button" :disabled="!!activeOperation" @click="runAction('approve')">{{ activeOperation === "refund:approve" ? "审核中..." : "审核通过" }}</button>
            <button v-if="selectedRefund.status === 'APPROVED'" class="primary-button" :disabled="!!activeOperation" @click="runAction('submit')">{{ activeOperation === "refund:submit" ? "提交中..." : "提交渠道" }}</button>
            <button v-if="selectedRefund.status === 'FAIL'" class="primary-button" :disabled="!!activeOperation" @click="runAction('retry')">{{ activeOperation === "refund:retry" ? "重试中..." : "失败重试" }}</button>
            <button v-if="selectedRefund.status === 'PROCESSING'" class="secondary-button" :disabled="!!activeOperation" @click="runAction('callback', 'SUCCESS')">{{ activeOperation === "refund:callback:SUCCESS" ? "回调中..." : "模拟成功回调" }}</button>
            <button v-if="selectedRefund.status === 'PROCESSING'" class="link-button danger-link" :disabled="!!activeOperation" @click="runAction('callback', 'FAIL')">{{ activeOperation === "refund:callback:FAIL" ? "回调中..." : "模拟失败回调" }}</button>
            <button class="secondary-button" :disabled="!!activeOperation" @click="showList">返回列表</button>
          </div>
        </section>
        <section v-if="selectedRefund" class="panel timeline-panel"><span class="section-kicker">AUDIT TRAIL</span><h3>操作日志</h3><div v-if="!selectedRefund.operationLogs.length" class="empty-state">暂无操作日志</div><ol v-else class="timeline"><li v-for="log in selectedRefund.operationLogs" :key="log.logNo"><span class="timeline-dot"></span><div><strong>{{ log.actionName }}</strong><small>{{ log.operatorName }} · {{ formatRefundTime(log.operatedAt) }}</small><p>{{ log.operationRemark }}</p></div><em>{{ statusLabel[log.toStatus] || log.toStatus }}</em></li></ol></section>
      </section>
    </main>
  </div>
</template>
