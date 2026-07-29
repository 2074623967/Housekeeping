<script setup>
import { computed, onMounted, ref } from "vue";
import { feeApi } from "../api/client";

const rows = ref([]);
const message = ref("");
const loading = ref(false);
const actionLoading = ref(false);
const filters = ref({ feeType: "", status: "" });
const form = ref({
  feeName: "营销补贴费",
  feeType: "SUBSIDY",
  feeMode: "FIXED",
  feeRate: 0,
  fixedAmount: 5,
  feeBearer: "平台"
});
const selectedFeeRule = ref(null);

const activeFeeCount = computed(() => rows.value.filter((item) => item.status === "启用").length);
const fixedFeeCount = computed(() => rows.value.filter((item) => item.feeMode === "FIXED").length);

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const result = await feeApi.getList({ ...filters.value, pageNo: 1, pageSize: 20 });
    rows.value = result.items;
    if (selectedFeeRule.value) {
      const latestFeeRule = result.items.find((item) => item.feeRuleNo === selectedFeeRule.value.feeRuleNo);
      if (latestFeeRule) {
        selectedFeeRule.value = latestFeeRule;
      }
    }
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function createFee() {
  actionLoading.value = true;
  message.value = "";
  try {
    await feeApi.create(form.value);
    await loadRows();
  } catch (error) {
    message.value = error.message;
  } finally {
    actionLoading.value = false;
  }
}

function usePlatformServiceFeeCase() {
  form.value = {
    feeName: "平台服务费",
    feeType: "PLATFORM_FEE",
    feeMode: "RATE",
    feeRate: 0.08,
    fixedAmount: 0,
    feeBearer: "用户"
  };
}

function useChannelFeeCase() {
  form.value = {
    feeName: "渠道手续费",
    feeType: "CHANNEL_FEE",
    feeMode: "FIXED",
    feeRate: 0,
    fixedAmount: 1,
    feeBearer: "平台"
  };
}

function selectFeeRule(row) {
  selectedFeeRule.value = row;
}

function resetFilters() {
  filters.value = { feeType: "", status: "" };
  loadRows();
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>费用规则</h2>
        <p>维护平台服务费、渠道手续费、补贴和承担方配置，支撑清分金额拆解</p>
      </div>
    </div>
    <section class="panel">
      <div class="card-grid metric-grid">
        <article class="card">
          <p class="card-title">费用规则总数</p>
          <p class="card-value">{{ rows.length }}</p>
        </article>
        <article class="card">
          <p class="card-title">启用规则</p>
          <p class="card-value">{{ activeFeeCount }}</p>
        </article>
        <article class="card">
          <p class="card-title">固定费用规则</p>
          <p class="card-value">{{ fixedFeeCount }}</p>
        </article>
      </div>

      <div class="toolbar">
        <div class="field"><label>费用类型</label><input v-model="filters.feeType" placeholder="例如：CHANNEL_FEE" /></div>
        <div class="field"><label>状态</label><input v-model="filters.status" placeholder="启用 / 停用" /></div>
        <div class="summary-box">
          <strong>配置说明</strong>
          <span>费率模式适合平台抽佣，固定费用模式适合通道手续费或保底扣费。</span>
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadRows">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div class="toolbar create-grid">
        <div class="field"><label>费用名称</label><input v-model="form.feeName" /></div>
        <div class="field"><label>费用类型</label><input v-model="form.feeType" /></div>
        <div class="field"><label>计费模式</label><input v-model="form.feeMode" /></div>
        <div class="field"><label>费率</label><input v-model="form.feeRate" /></div>
        <div class="field"><label>固定金额</label><input v-model="form.fixedAmount" /></div>
        <div class="field"><label>承担方</label><input v-model="form.feeBearer" /></div>
        <div class="toolbar-actions">
          <button class="button secondary" :disabled="actionLoading" @click="usePlatformServiceFeeCase">平台服务费示例</button>
          <button class="button secondary" :disabled="actionLoading" @click="useChannelFeeCase">渠道费示例</button>
          <button class="button warn" :disabled="actionLoading" @click="createFee">新增费用规则</button>
        </div>
      </div>

      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">费用规则加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>规则号</th><th>费用名称</th><th>费用类型</th><th>模式</th><th>费率</th><th>固定金额</th><th>承担方</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.feeRuleNo">
              <td>{{ row.feeRuleNo }}</td><td>{{ row.feeName }}</td><td>{{ row.feeType }}</td><td>{{ row.feeMode }}</td><td>{{ row.feeRate }}</td><td class="amount deduct">{{ row.fixedAmount }}</td><td>{{ row.feeBearer }}</td>
              <td><span class="badge" :class="row.statusType">{{ row.status }}</span></td>
              <td><button class="button secondary button-inline" @click="selectFeeRule(row)">查看说明</button></td>
            </tr>
            <tr v-if="rows.length === 0">
              <td colspan="9" class="empty-cell">当前筛选条件下暂无费用规则。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="panel">
      <div class="topbar topbar-inner">
        <div>
          <h3>费用规则工作区</h3>
          <p>{{ selectedFeeRule ? `当前规则：${selectedFeeRule.feeRuleNo}` : "请选择上方费用规则查看模式、承担方和说明" }}</p>
        </div>
      </div>
      <div v-if="!selectedFeeRule" class="state-box">点击上方任意费用规则后，可查看计费模式、承担方和适用说明。</div>
      <div v-else class="detail-summary-grid">
        <div class="summary-card">
          <strong>费用信息</strong>
          <span>{{ selectedFeeRule.feeName }} / {{ selectedFeeRule.feeType }}</span>
        </div>
        <div class="summary-card">
          <strong>计费方式</strong>
          <span>{{ selectedFeeRule.feeMode }} / 费率 {{ selectedFeeRule.feeRate }} / 固定 {{ selectedFeeRule.fixedAmount }}</span>
        </div>
        <div class="summary-card">
          <strong>承担方与状态</strong>
          <span>{{ selectedFeeRule.feeBearer }} / {{ selectedFeeRule.status }}</span>
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

.create-grid {
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

.deduct {
  color: #b91c1c;
}

.empty-cell {
  color: #64748b;
  text-align: center;
}
</style>
