<script setup>
import { computed, onMounted, ref } from "vue";
import { shareApi } from "../api/client";

const rows = ref([]);
const message = ref("");
const loading = ref(false);
const filters = ref({ clearingNo: "", shareType: "" });
const selectedShare = ref(null);

const workerShareCount = computed(() => rows.value.filter((item) => item.shareType === "WORKER").length);
const merchantShareCount = computed(() => rows.value.filter((item) => item.shareType === "MERCHANT").length);
const platformShareCount = computed(() => rows.value.filter((item) => item.shareType === "PLATFORM").length);

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const result = await shareApi.getList({ ...filters.value, pageNo: 1, pageSize: 20 });
    rows.value = result.items;
    if (selectedShare.value) {
      const latestShare = result.items.find((item) => item.shareItemNo === selectedShare.value.shareItemNo);
      if (latestShare) {
        selectedShare.value = latestShare;
      }
    }
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

function selectShare(row) {
  selectedShare.value = row;
}

function resetFilters() {
  filters.value = { clearingNo: "", shareType: "" };
  loadRows();
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>分账明细</h2>
        <p>按收款方追踪分账金额、待结算状态和资金去向，支撑财务核账与结算排查</p>
      </div>
    </div>
    <section class="panel">
      <div class="card-grid metric-grid">
        <article class="card">
          <p class="card-title">服务者分账</p>
          <p class="card-value">{{ workerShareCount }}</p>
        </article>
        <article class="card">
          <p class="card-title">商家分账</p>
          <p class="card-value">{{ merchantShareCount }}</p>
        </article>
        <article class="card">
          <p class="card-title">平台分账</p>
          <p class="card-value">{{ platformShareCount }}</p>
        </article>
      </div>

      <div class="toolbar">
        <div class="field"><label>清分单号</label><input v-model="filters.clearingNo" placeholder="例如：CLO20001" /></div>
        <div class="field"><label>分账类型</label><input v-model="filters.shareType" placeholder="例如：WORKER / MERCHANT / PLATFORM" /></div>
        <div class="summary-box">
          <strong>查看建议</strong>
          <span>先按清分单号定位，再按分账类型区分服务者、商家、平台资金去向。</span>
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadRows">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">分账明细加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>分账明细号</th><th>清分单号</th><th>类型</th><th>对象编号</th><th>对象名称</th><th>金额</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.shareItemNo">
              <td>{{ row.shareItemNo }}</td><td>{{ row.clearingNo }}</td><td>{{ row.shareType }}</td><td>{{ row.shareTargetNo }}</td><td>{{ row.shareTargetName }}</td><td class="amount">{{ row.shareAmount }}</td>
              <td><span class="badge" :class="row.statusType">{{ row.shareStatus }}</span></td>
              <td><button class="button secondary button-inline" @click="selectShare(row)">查看去向</button></td>
            </tr>
            <tr v-if="rows.length === 0">
              <td colspan="8" class="empty-cell">当前筛选条件下暂无分账明细。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="panel">
      <div class="topbar topbar-inner">
        <div>
          <h3>资金去向说明</h3>
          <p>{{ selectedShare ? `当前分账明细：${selectedShare.shareItemNo}` : "请选择上方分账明细查看收款方去向说明" }}</p>
        </div>
      </div>
      <div v-if="!selectedShare" class="state-box">点击上方任一分账明细后，可查看对应收款方、分账金额和待结算状态说明。</div>
      <div v-else class="detail-summary-grid">
        <div class="summary-card">
          <strong>清分与类型</strong>
          <span>{{ selectedShare.clearingNo }} / {{ selectedShare.shareType }}</span>
        </div>
        <div class="summary-card">
          <strong>收款对象</strong>
          <span>{{ selectedShare.shareTargetName }} / {{ selectedShare.shareTargetNo }}</span>
        </div>
        <div class="summary-card">
          <strong>金额与状态</strong>
          <span>{{ selectedShare.shareAmount }} / {{ selectedShare.shareStatus }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.metric-grid {
  margin-bottom: 16px;
}

.summary-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid #dbeafe;
  background: linear-gradient(180deg, #f8fbff 0%, #eff6ff 100%);
  color: #1e3a8a;
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

.button-inline {
  padding: 8px 12px;
}

.amount {
  color: #b45309;
  font-weight: 700;
}

.empty-cell {
  color: #64748b;
  text-align: center;
}
</style>
