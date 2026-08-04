<script setup>
import { onMounted, reactive, ref } from "vue";
import { getBatches, getDifferences, getOverview, resolveDifference, runBatch } from "./api";

const view = ref("overview");
const loading = ref(false);
const message = ref("");
const overview = reactive({ batchCount: 0, runningBatchCount: 0, openDifferenceCount: 0, matchedCount: 0 });
const batches = ref([]);
const differences = reactive({ items: [], total: 0 });
const filter = reactive({ batchNo: "", differenceType: "", status: "OPEN", pageNo: 1, pageSize: 20 });
const selected = ref(null);

const typeLabel = {
  CHANNEL_ONLY: "渠道单边",
  INTERNAL_ONLY: "平台单边",
  AMOUNT_MISMATCH: "金额不一致",
  STATUS_MISMATCH: "状态不一致"
};

async function refresh() {
  loading.value = true;
  message.value = "";
  try {
    Object.assign(overview, await getOverview());
    batches.value = await getBatches();
    Object.assign(differences, await getDifferences(filter));
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
  } catch (error) {
    message.value = error.message;
  }
}

onMounted(refresh);
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand"><span class="brand-mark">R</span><div><strong>对账中心</strong><small>RECONCILIATION</small></div></div>
      <button class="nav" :class="{ active: view === 'overview' }" @click="view = 'overview'">对账工作台 <small>Overview</small></button>
      <button class="nav" :class="{ active: view === 'differences' }" @click="view = 'differences'">差异处理 <small>Differences</small></button>
      <button class="nav" :class="{ active: view === 'batches' }" @click="view = 'batches'">对账批次 <small>Batches</small></button>
      <div class="note"><b>财务核对链路</b><p>渠道账单、内部支付事实和差异处置必须可追踪。</p></div>
    </aside>
    <main class="main">
      <header class="topbar"><div><p class="eyebrow">FINANCE CONTROL / RECONCILIATION</p><h1>{{ view === "overview" ? "对账工作台" : view === "differences" ? "差异处理台" : "对账批次" }}</h1></div><button class="ghost" @click="refresh">刷新数据</button></header>
      <div v-if="message" class="message">{{ message }}</div>
      <section v-if="view === 'overview'">
        <div class="hero panel"><div><p class="eyebrow teal">RECONCILIATION CONTROL TOWER</p><h2>先把事实核对清楚，再进入财务处理</h2><p>每个批次都记录渠道账单、平台事实、自动匹配结果和差异结案状态。</p></div><button class="primary" @click="view = 'differences'">处理未结差异</button></div>
        <div class="metrics"><article><span>对账批次</span><b>{{ overview.batchCount }}</b><small>累计批次</small></article><article class="blue"><span>已匹配记录</span><b>{{ overview.matchedCount }}</b><small>平台与渠道一致</small></article><article class="orange"><span>未结差异</span><b>{{ overview.openDifferenceCount }}</b><small>需要人工处置</small></article><article class="purple"><span>运行中批次</span><b>{{ overview.runningBatchCount }}</b><small>实时处理</small></article></div>
        <section class="panel"><div class="section-title"><div><p class="eyebrow">LATEST BATCHES</p><h3>最近对账批次</h3></div><button class="link" @click="view = 'batches'">查看全部</button></div><table class="table"><thead><tr><th>批次号</th><th>业务日期</th><th>渠道</th><th>匹配 / 差异</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="batch in batches.slice(0, 5)" :key="batch.batchNo"><td class="strong">{{ batch.batchNo }}</td><td>{{ batch.businessDate }}</td><td>{{ batch.channelCode }}</td><td>{{ batch.matchedCount }} / <em>{{ batch.differenceCount }}</em></td><td><span class="pill" :class="batch.status.toLowerCase()">{{ batch.status === "COMPLETED" ? "已完成" : batch.status }}</span></td><td><button class="link" @click="executeBatch(batch)">重新跑批</button></td></tr></tbody></table></section>
      </section>
      <section v-else-if="view === 'batches'" class="panel"><div class="section-title"><div><p class="eyebrow">RECONCILIATION BATCHES</p><h2>对账批次</h2></div></div><table class="table"><thead><tr><th>批次号</th><th>日期</th><th>渠道 / 来源</th><th>渠道记录</th><th>平台记录</th><th>匹配</th><th>差异</th><th>操作</th></tr></thead><tbody><tr v-for="batch in batches" :key="batch.batchNo"><td class="strong">{{ batch.batchNo }}</td><td>{{ batch.businessDate }}</td><td>{{ batch.channelCode }}<small>{{ batch.billSource }}</small></td><td>{{ batch.channelCount }}</td><td>{{ batch.internalCount }}</td><td class="green">{{ batch.matchedCount }}</td><td class="red">{{ batch.differenceCount }}</td><td><button class="link" @click="executeBatch(batch)">重新跑批</button></td></tr></tbody></table></section>
      <section v-else class="panel"><div class="section-title"><div><p class="eyebrow">OPEN DIFFERENCES</p><h2>差异处理台</h2></div><span class="count">共 {{ differences.total }} 条</span></div><div class="filters"><select v-model="filter.differenceType" @change="refresh"><option value="">全部差异类型</option><option value="CHANNEL_ONLY">渠道单边</option><option value="INTERNAL_ONLY">平台单边</option><option value="AMOUNT_MISMATCH">金额不一致</option><option value="STATUS_MISMATCH">状态不一致</option></select><button class="primary compact" @click="refresh">查询</button></div><div v-if="loading" class="empty">正在加载...</div><div v-else-if="!differences.items.length" class="empty">当前没有未结差异</div><table v-else class="table"><thead><tr><th>差异编号</th><th>批次 / 支付单</th><th>差异类型</th><th>渠道金额</th><th>平台金额</th><th>状态</th><th>处置</th></tr></thead><tbody><tr v-for="item in differences.items" :key="item.differenceNo"><td class="strong">{{ item.differenceNo }}</td><td>{{ item.batchNo }}<small>{{ item.paymentOrderId }}</small></td><td><span class="type">{{ typeLabel[item.differenceType] || item.differenceType }}</span></td><td>¥{{ Number(item.channelAmount || 0).toFixed(2) }}</td><td>¥{{ Number(item.internalAmount || 0).toFixed(2) }}</td><td><span class="pill open">待处置</span></td><td><button class="primary compact" @click="closeDifference(item)">人工结案</button></td></tr></tbody></table></section>
    </main>
  </div>
</template>

