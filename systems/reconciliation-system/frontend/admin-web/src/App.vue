<script setup>
import { onMounted, reactive, ref } from "vue";
import { checkBatchReadiness, downloadBatchDetail, getBatchDetail, getBatches, getCrossSystemChain, getDifferences, getOverview, resolveDifference, runBatch } from "./api";

const view = ref("overview");
const loading = ref(false);
const message = ref("");
const overview = reactive({ batchCount: 0, runningBatchCount: 0, openDifferenceCount: 0, matchedCount: 0 });
const batches = ref([]);
const batchesTotal = ref(0);
const batchPageNo = ref(1);
const batchPageSize = 20;
const differences = reactive({ items: [], total: 0 });
const filter = reactive({ batchNo: "", differenceType: "", status: "OPEN", pageNo: 1, pageSize: 20 });
const selectedBatch = ref(null);
const businessChainId = ref("");
const chainSnapshot = ref(null);
const chainLoading = ref(false);

function differencePageCount() {
  return Math.max(1, Math.ceil(differences.total / filter.pageSize));
}

const typeLabel = {
  CHANNEL_ONLY: "渠道单边",
  INTERNAL_ONLY: "平台单边",
  AMOUNT_MISMATCH: "金额不一致",
  STATUS_MISMATCH: "状态不一致"
};

const readinessLabel = {
  NOT_CHECKED: "未检查",
  BLOCKED: "禁止进入对账",
  CONDITIONAL: "有条件进入",
  READY: "可进入对账"
};

function readinessClass(status) {
  return {
    READY: "readiness-ready",
    CONDITIONAL: "readiness-conditional",
    BLOCKED: "readiness-blocked",
    NOT_CHECKED: "readiness-not-checked"
  }[status] || "readiness-not-checked";
}

async function refresh() {
  loading.value = true;
  message.value = "";
  try {
    Object.assign(overview, await getOverview());
    const batchPage = await getBatches({ pageNo: batchPageNo.value, pageSize: batchPageSize });
    batches.value = batchPage.items || [];
    batchesTotal.value = batchPage.total || 0;
    batchPageNo.value = batchPage.pageNo || batchPageNo.value;
    Object.assign(differences, await getDifferences(filter));
    if (selectedBatch.value) {
      selectedBatch.value = await getBatchDetail(selectedBatch.value.batchNo);
    }
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function executeBatch(batch) {
  try {
    await runBatch(batch.batchNo);
    await refresh();
    if (selectedBatch.value && selectedBatch.value.batchNo === batch.batchNo) {
      selectedBatch.value = await getBatchDetail(batch.batchNo);
    }
  } catch (error) {
    message.value = error.message;
  }
}

async function checkReadiness(batchNo) {
  try {
    const checked = await checkBatchReadiness(batchNo);
    message.value = `${batchNo}：${checked.readinessSummary}`;
    await refresh();
    if (selectedBatch.value && selectedBatch.value.batchNo === batchNo) {
      selectedBatch.value = await getBatchDetail(batchNo);
    }
  } catch (error) {
    message.value = error.message;
  }
}

async function closeDifference(item) {
  try {
    await resolveDifference(item.differenceNo, {
      resolution: "人工核实并完成调账",
      remark: "对账运营完成差异处置"
    });
    await refresh();
    if (selectedBatch.value && selectedBatch.value.batchNo === item.batchNo) {
      selectedBatch.value = await getBatchDetail(item.batchNo);
    }
  } catch (error) {
    message.value = error.message;
  }
}

function goToDifferencePage(nextPage) {
  if (loading.value || nextPage < 1 || nextPage > differencePageCount()) return;
  filter.pageNo = nextPage;
  refresh();
}

function resetDifferencePage() {
  filter.pageNo = 1;
  refresh();
}

function batchPageCount() {
  return Math.max(1, Math.ceil(batchesTotal.value / batchPageSize));
}

function goToBatchPage(nextPage) {
  if (loading.value || nextPage < 1 || nextPage > batchPageCount()) return;
  batchPageNo.value = nextPage;
  refresh();
}

async function openBatchDetail(batchNo) {
  try {
    selectedBatch.value = await getBatchDetail(batchNo);
    view.value = "detail";
  } catch (error) {
    message.value = error.message;
  }
}

async function exportBatchDetail(batchNo) {
  try {
    const blob = await downloadBatchDetail(batchNo);
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `reconciliation-batch-${batchNo}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
    message.value = `批次 ${batchNo} 快照已导出`;
  } catch (error) {
    message.value = error.message;
  }
}

async function inspectCrossSystemChain() {
  const chainId = businessChainId.value.trim();
  if (!chainId || chainLoading.value) return;
  chainLoading.value = true;
  message.value = "";
  try {
    chainSnapshot.value = await getCrossSystemChain(chainId);
  } catch (error) {
    chainSnapshot.value = null;
    message.value = error.message;
  } finally {
    chainLoading.value = false;
  }
}

onMounted(() => {
  const initialChainId = new URLSearchParams(window.location.search).get("businessChainId");
  if (initialChainId?.trim()) {
    businessChainId.value = initialChainId.trim();
    inspectCrossSystemChain();
  }
  refresh();
});
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand"><span class="brand-mark">R</span><div><strong>对账中心</strong><small>RECONCILIATION</small></div></div>
      <button class="nav" :class="{ active: view === 'overview' }" @click="view = 'overview'">对账工作台 <small>Overview</small></button>
      <button class="nav" :class="{ active: view === 'differences' }" @click="view = 'differences'">差异处理 <small>Differences</small></button>
      <button class="nav" :class="{ active: view === 'batches' }" @click="view = 'batches'">对账批次 <small>Batches</small></button>
      <button class="nav" :class="{ active: view === 'detail' }" :disabled="!selectedBatch" @click="view = 'detail'">批次详情 <small>Batch Detail</small></button>
      <button class="nav" :class="{ active: view === 'chain' }" @click="view = 'chain'">链路诊断 <small>Chain Facts</small></button>
      <div class="note"><b>财务核对链路</b><p>渠道账单、内部支付事实和差异处置必须可追踪。</p></div>
    </aside>
    <main class="main">
      <header class="topbar"><div><p class="eyebrow">FINANCE CONTROL / RECONCILIATION</p><h1>{{ view === "overview" ? "对账工作台" : view === "differences" ? "差异处理台" : view === "detail" ? "批次详情工作台" : view === "chain" ? "跨系统链路诊断" : "对账批次" }}</h1></div><button class="ghost" @click="refresh">刷新数据</button></header>
      <div v-if="message" class="message">{{ message }}</div>
      <section v-if="view === 'overview'">
        <div class="hero panel"><div><p class="eyebrow teal">RECONCILIATION CONTROL TOWER</p><h2>先把事实核对清楚，再进入财务处理</h2><p>每个批次都记录渠道账单、平台事实、自动匹配结果和差异结案状态。</p></div><button class="primary" @click="view = 'differences'">处理未结差异</button></div>
        <div class="metrics"><article><span>对账批次</span><b>{{ overview.batchCount }}</b><small>累计批次</small></article><article class="blue"><span>已匹配记录</span><b>{{ overview.matchedCount }}</b><small>平台与渠道一致</small></article><article class="orange"><span>未结差异</span><b>{{ overview.openDifferenceCount }}</b><small>需要人工处置</small></article><article class="purple"><span>运行中批次</span><b>{{ overview.runningBatchCount }}</b><small>实时处理</small></article></div>
        <section class="panel"><div class="section-title"><div><p class="eyebrow">LATEST BATCHES</p><h3>最近对账批次</h3></div><button class="link" @click="view = 'batches'">查看全部</button></div><div v-if="!loading && !batches.length" class="empty">暂无对账批次</div><table v-else class="table"><thead><tr><th>批次号</th><th>业务日期</th><th>渠道</th><th>匹配 / 差异</th><th>对账准入</th><th>操作</th></tr></thead><tbody><tr v-for="batch in batches.slice(0, 5)" :key="batch.batchNo"><td class="strong">{{ batch.batchNo }}</td><td>{{ batch.businessDate }}</td><td>{{ batch.channelCode }}</td><td>{{ batch.matchedCount }} / <em>{{ batch.differenceCount }}</em></td><td><span class="pill" :class="readinessClass(batch.readinessStatus)">{{ readinessLabel[batch.readinessStatus] || batch.readinessStatus }}</span><small>{{ batch.readinessCompletedChainCount }}/{{ batch.readinessChainCount }} 条链路</small></td><td><button class="link" @click="openBatchDetail(batch.batchNo)">查看详情</button><button class="link" @click="checkReadiness(batch.batchNo)">检查准入</button><button class="link" @click="executeBatch(batch)">执行对账</button></td></tr></tbody></table></section>
      </section>
      <section v-else-if="view === 'batches'" class="panel"><div class="section-title"><div><p class="eyebrow">RECONCILIATION BATCHES</p><h2>对账批次</h2></div></div><div v-if="!loading && !batches.length" class="empty">暂无对账批次</div><table v-else class="table"><thead><tr><th>批次号</th><th>日期</th><th>渠道 / 来源</th><th>渠道记录</th><th>平台记录</th><th>匹配</th><th>差异</th><th>对账准入</th><th>操作</th></tr></thead><tbody><tr v-for="batch in batches" :key="batch.batchNo"><td class="strong">{{ batch.batchNo }}</td><td>{{ batch.businessDate }}</td><td>{{ batch.channelCode }}<small>{{ batch.billSource }}</small></td><td>{{ batch.channelCount }}</td><td>{{ batch.internalCount }}</td><td class="green">{{ batch.matchedCount }}</td><td class="red">{{ batch.differenceCount }}</td><td><span class="pill" :class="readinessClass(batch.readinessStatus)">{{ readinessLabel[batch.readinessStatus] || batch.readinessStatus }}</span><small>{{ batch.readinessSummary || "尚未检查" }}</small></td><td><button class="link" @click="openBatchDetail(batch.batchNo)">查看详情</button><button class="link" @click="checkReadiness(batch.batchNo)">检查准入</button><button class="link" @click="executeBatch(batch)">执行对账</button></td></tr></tbody></table><div class="pager" aria-label="对账批次分页"><span>共 {{ batchesTotal }} 条，第 {{ batchPageNo }} / {{ batchPageCount() }} 页</span><div><button class="ghost compact" :disabled="loading || batchPageNo <= 1" @click="goToBatchPage(batchPageNo - 1)">上一页</button><button class="ghost compact" :disabled="loading || batchPageNo >= batchPageCount()" @click="goToBatchPage(batchPageNo + 1)">下一页</button></div></div></section>
      <section v-else-if="view === 'detail'" class="panel">
        <div v-if="!selectedBatch" class="empty">请选择一个对账批次查看详情</div>
        <template v-else>
          <div class="section-title">
            <div><p class="eyebrow">BATCH DETAIL</p><h2>{{ selectedBatch.batchNo }}</h2></div>
            <div class="actions">
              <button class="link" @click="exportBatchDetail(selectedBatch.batchNo)">导出快照</button>
              <button class="link" @click="view = 'batches'">返回批次列表</button>
            </div>
          </div>
          <div class="detail-grid">
            <article><span>业务日期</span><b>{{ selectedBatch.businessDate }}</b></article>
            <article><span>渠道 / 来源</span><b>{{ selectedBatch.channelCode }}</b><small>{{ selectedBatch.billSource }}</small></article>
            <article><span>批次状态</span><b>{{ selectedBatch.status === "COMPLETED" ? "已完成" : selectedBatch.status }}</b></article>
            <article><span>已匹配数</span><b>{{ selectedBatch.matchedCount }}</b></article>
            <article><span>未结差异</span><b>{{ selectedBatch.openDifferenceCount }}</b></article>
            <article><span>已结案差异</span><b>{{ selectedBatch.resolvedDifferenceCount }}</b></article>
          </div>
          <section class="readiness-card" :class="readinessClass(selectedBatch.readinessStatus)">
            <div><p class="eyebrow">FINANCIAL ENTRY GATE</p><h3>{{ readinessLabel[selectedBatch.readinessStatus] || selectedBatch.readinessStatus }}</h3><p>{{ selectedBatch.readinessSummary || "尚未执行批次级准入检查" }}</p></div>
            <div class="readiness-facts"><span>链路总数 <b>{{ selectedBatch.readinessChainCount }}</b></span><span>已收口 <b>{{ selectedBatch.readinessCompletedChainCount }}</b></span><span>异常 <b>{{ selectedBatch.readinessAbnormalChainCount }}</b></span><span>待收口 <b>{{ selectedBatch.readinessPendingChainCount }}</b></span></div>
            <div class="readiness-action"><strong>下一动作</strong><span>{{ selectedBatch.readinessSuggestedAction || "点击检查准入获取建议" }}</span><small v-if="selectedBatch.readinessBlockingOwner">责任方：{{ selectedBatch.readinessBlockingOwner }}</small><button class="primary compact" @click="checkReadiness(selectedBatch.batchNo)">重新检查准入</button></div>
          </section>
          <section class="detail-section">
            <div class="section-title"><div><p class="eyebrow">CHANNEL FACTS</p><h3>渠道账单记录</h3></div><span class="count">{{ selectedBatch.channelRecords.length }} 条</span></div>
            <div v-if="!selectedBatch.channelRecords.length" class="empty">当前批次暂无渠道账单记录</div>
            <table v-else class="table"><thead><tr><th>渠道流水</th><th>支付单号</th><th>金额</th><th>状态</th><th>交易时间</th></tr></thead><tbody><tr v-for="item in selectedBatch.channelRecords" :key="item.channelTradeNo"><td class="strong">{{ item.channelTradeNo }}</td><td>{{ item.paymentOrderId }}</td><td>¥{{ Number(item.amount || 0).toFixed(2) }}</td><td>{{ item.tradeStatus }}</td><td>{{ item.tradeTime?.replace("T", " ") }}</td></tr></tbody></table>
          </section>
          <section class="detail-section">
            <div class="section-title"><div><p class="eyebrow">INTERNAL FACTS</p><h3>平台支付事实</h3></div><span class="count">{{ selectedBatch.internalRecords.length }} 条</span></div>
            <div v-if="!selectedBatch.internalRecords.length" class="empty">当前批次暂无平台支付事实</div>
            <table v-else class="table"><thead><tr><th>支付单号</th><th>金额</th><th>状态</th><th>来源系统</th><th>支付时间</th></tr></thead><tbody><tr v-for="item in selectedBatch.internalRecords" :key="item.paymentOrderId"><td class="strong">{{ item.paymentOrderId }}</td><td>¥{{ Number(item.amount || 0).toFixed(2) }}</td><td>{{ item.internalStatus }}</td><td>{{ item.sourceSystem }}</td><td>{{ item.paidTime?.replace("T", " ") }}</td></tr></tbody></table>
          </section>
          <section class="detail-section">
            <div class="section-title"><div><p class="eyebrow">DIFFERENCE BREAKDOWN</p><h3>差异明细</h3></div><span class="count">{{ selectedBatch.differences.length }} 条</span></div>
            <div v-if="!selectedBatch.differences.length" class="empty">当前批次没有差异</div>
            <table v-else class="table"><thead><tr><th>差异编号</th><th>支付单号</th><th>差异类型</th><th>渠道金额</th><th>平台金额</th><th>状态</th><th>结论</th></tr></thead><tbody><tr v-for="item in selectedBatch.differences" :key="item.differenceNo"><td class="strong">{{ item.differenceNo }}</td><td>{{ item.paymentOrderId }}</td><td><span class="type">{{ typeLabel[item.differenceType] || item.differenceType }}</span></td><td>¥{{ Number(item.channelAmount || 0).toFixed(2) }}</td><td>¥{{ Number(item.internalAmount || 0).toFixed(2) }}</td><td><span class="pill" :class="item.status === 'RESOLVED' ? 'completed' : 'open'">{{ item.status === "RESOLVED" ? "已结案" : "待处置" }}</span></td><td>{{ item.resolution || "-" }}</td></tr></tbody></table>
          </section>
        </template>
      </section>
      <section v-else-if="view === 'chain'" class="panel">
        <div class="section-title"><div><p class="eyebrow">CROSS-SYSTEM FACT SNAPSHOT</p><h2>跨系统业务链路诊断</h2></div></div>
        <div class="filters chain-filters">
          <label for="business-chain-id">业务链路 ID</label>
          <input id="business-chain-id" v-model.trim="businessChainId" placeholder="例如 CHAIN-PAY202609230001" :disabled="chainLoading" @keyup.enter="inspectCrossSystemChain" />
          <button class="primary compact" :disabled="chainLoading || !businessChainId" @click="inspectCrossSystemChain">{{ chainLoading ? "查询中..." : "查询链路" }}</button>
        </div>
        <div v-if="!chainSnapshot && !chainLoading" class="empty">输入业务链路 ID，查看支付、清分、结算、账务等系统事实和财务收口判断。</div>
        <div v-else-if="chainLoading" class="empty">正在读取跨系统事实快照...</div>
        <template v-else>
          <div class="detail-grid chain-summary">
            <article><span>业务链路 ID</span><b>{{ chainSnapshot.businessChainId }}</b></article>
            <article><span>链路状态</span><b>{{ chainSnapshot.chainStatus }}</b></article>
            <article><span>责任状态</span><b>{{ chainSnapshot.responsibilityStatus }}</b></article>
            <article><span>财务收口</span><b>{{ chainSnapshot.financialCloseable ? "允许" : "禁止" }}</b></article>
            <article><span>已完成阶段</span><b>{{ chainSnapshot.completedStageCount }} / {{ chainSnapshot.expectedStageCount }}</b></article>
            <article><span>事实数量</span><b>{{ chainSnapshot.factCount }}</b></article>
          </div>
          <section class="readiness-card" :class="chainSnapshot.financialCloseable ? 'readiness-ready' : 'readiness-blocked'">
            <div><p class="eyebrow">DIAGNOSIS</p><h3>{{ chainSnapshot.diagnosis }}</h3><p>{{ chainSnapshot.nextAction }}</p></div>
            <div class="readiness-facts"><span>异常事实 <b>{{ chainSnapshot.abnormalFactCount }}</b></span><span>最近事实 <b>{{ chainSnapshot.lastFactAt || "—" }}</b></span></div>
          </section>
          <div v-if="!chainSnapshot.facts?.length" class="empty">当前链路暂无已上报事实。</div>
          <table v-else class="table"><thead><tr><th>来源系统</th><th>事实类型</th><th>事实编号</th><th>业务号</th><th>支付场景</th><th>金额</th><th>配置版本</th><th>状态</th><th>发生时间</th></tr></thead><tbody><tr v-for="fact in chainSnapshot.facts" :key="`${fact.systemCode}:${fact.factType}:${fact.factNo}`"><td class="strong">{{ fact.systemCode }}</td><td>{{ fact.factType }}</td><td>{{ fact.factNo }}</td><td>{{ fact.businessNo || "—" }}</td><td>{{ fact.payScene || "—" }}</td><td>{{ fact.amount ?? "—" }}</td><td>{{ fact.snapshotVersion ?? "—" }}</td><td><span class="pill" :class="fact.factStatus === 'SUCCESS' ? 'completed' : 'open'">{{ fact.factStatus }}</span></td><td>{{ fact.occurredAt || "—" }}</td></tr></tbody></table>
        </template>
      </section>
      <section v-else class="panel"><div class="section-title"><div><p class="eyebrow">OPEN DIFFERENCES</p><h2>差异处理台</h2></div><span class="count">共 {{ differences.total }} 条</span></div><div class="filters"><select v-model="filter.differenceType" @change="resetDifferencePage"><option value="">全部差异类型</option><option value="CHANNEL_ONLY">渠道单边</option><option value="INTERNAL_ONLY">平台单边</option><option value="AMOUNT_MISMATCH">金额不一致</option><option value="STATUS_MISMATCH">状态不一致</option></select><button class="primary compact" @click="resetDifferencePage">查询</button></div><div v-if="loading" class="empty">正在加载...</div><div v-else-if="!differences.items.length" class="empty">当前没有未结差异</div><table v-else class="table"><thead><tr><th>差异编号</th><th>批次 / 支付单</th><th>差异类型</th><th>渠道金额</th><th>平台金额</th><th>状态</th><th>处置</th></tr></thead><tbody><tr v-for="item in differences.items" :key="item.differenceNo"><td class="strong">{{ item.differenceNo }}</td><td>{{ item.batchNo }}<small>{{ item.paymentOrderId }}</small></td><td><span class="type">{{ typeLabel[item.differenceType] || item.differenceType }}</span></td><td>¥{{ Number(item.channelAmount || 0).toFixed(2) }}</td><td>¥{{ Number(item.internalAmount || 0).toFixed(2) }}</td><td><span class="pill open">待处置</span></td><td><button class="primary compact" @click="closeDifference(item)">人工结案</button></td></tr></tbody></table><div class="pager" aria-label="差异分页"><span>共 {{ differences.total }} 条，第 {{ filter.pageNo }} / {{ differencePageCount() }} 页</span><div><button class="ghost compact" :disabled="loading || filter.pageNo <= 1" @click="goToDifferencePage(filter.pageNo - 1)">上一页</button><button class="ghost compact" :disabled="loading || filter.pageNo >= differencePageCount()" @click="goToDifferencePage(filter.pageNo + 1)">下一页</button></div></div></section>
    </main>
  </div>
</template>
