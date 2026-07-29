<script setup>
import { computed, onMounted, ref } from "vue";
import { ruleApi } from "../api/client";

const rows = ref([]);
const loading = ref(false);
const actionLoading = ref(false);
const message = ref("");
const filters = ref({ ruleType: "", ruleStatus: "" });
const form = ref({
  ruleName: "次日达订单清分规则",
  ruleType: "ORDER",
  ruleExpression: "平台=10%, 渠道=0.6%, 商家=12%, 服务者=余下",
  greyFlag: "否"
});
const selectedRule = ref(null);

const activeRuleCount = computed(() => rows.value.filter((item) => item.ruleStatus === "启用").length);
const greyRuleCount = computed(() => rows.value.filter((item) => item.greyFlag === "是").length);

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const result = await ruleApi.getList({ ...filters.value, pageNo: 1, pageSize: 20 });
    rows.value = result.items;
    if (selectedRule.value) {
      const latestRule = result.items.find((item) => item.ruleNo === selectedRule.value.ruleNo);
      if (latestRule) {
        selectedRule.value = latestRule;
      }
    }
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function createRule() {
  actionLoading.value = true;
  message.value = "";
  try {
    await ruleApi.create(form.value);
    await loadRows();
  } catch (error) {
    message.value = error.message;
  } finally {
    actionLoading.value = false;
  }
}

async function toggleRule(row) {
  const actionLabel = row.ruleStatus === "启用" ? "停用" : "启用";
  if (!window.confirm(`确认${actionLabel}清分规则 ${row.ruleNo} 吗？`)) {
    return;
  }
  actionLoading.value = true;
  message.value = "";
  try {
    if (row.ruleStatus === "启用") {
      await ruleApi.disable(row.ruleNo);
    } else {
      await ruleApi.enable(row.ruleNo);
    }
    await loadRows();
    selectedRule.value = row;
  } catch (error) {
    message.value = error.message;
  } finally {
    actionLoading.value = false;
  }
}

function selectRule(row) {
  selectedRule.value = row;
}

function resetFilters() {
  filters.value = { ruleType: "", ruleStatus: "" };
  loadRows();
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>清分规则</h2>
        <p>维护清分表达式、规则版本、灰度标记和启停状态，支撑多业务线拆分策略管理</p>
      </div>
    </div>
    <section class="panel">
      <div class="card-grid metric-grid">
        <article class="card">
          <p class="card-title">规则总数</p>
          <p class="card-value">{{ rows.length }}</p>
        </article>
        <article class="card">
          <p class="card-title">启用规则</p>
          <p class="card-value">{{ activeRuleCount }}</p>
        </article>
        <article class="card">
          <p class="card-title">灰度规则</p>
          <p class="card-value">{{ greyRuleCount }}</p>
        </article>
      </div>

      <div class="toolbar">
        <div class="field"><label>规则类型</label><input v-model="filters.ruleType" placeholder="例如：ORDER" /></div>
        <div class="field"><label>规则状态</label><input v-model="filters.ruleStatus" placeholder="启用 / 停用" /></div>
        <div class="summary-box">
          <strong>规则约束</strong>
          <span>启停前需确认是否影响线上清分，灰度规则建议配合新业务或新合作方试运行。</span>
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadRows">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div class="toolbar create-grid">
        <div class="field"><label>规则名称</label><input v-model="form.ruleName" /></div>
        <div class="field"><label>规则类型</label><input v-model="form.ruleType" /></div>
        <div class="field"><label>规则表达式</label><input v-model="form.ruleExpression" /></div>
        <div class="field"><label>灰度标记</label><input v-model="form.greyFlag" placeholder="是 / 否" /></div>
        <div class="toolbar-actions"><button class="button warn" :disabled="actionLoading" @click="createRule">新增规则</button></div>
      </div>

      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">规则列表加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>规则号</th><th>名称</th><th>类型</th><th>表达式</th><th>状态</th><th>版本</th><th>灰度</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.ruleNo">
              <td>{{ row.ruleNo }}</td><td>{{ row.ruleName }}</td><td>{{ row.ruleType }}</td><td class="expression-cell">{{ row.ruleExpression }}</td><td><span class="badge" :class="row.ruleStatusType">{{ row.ruleStatus }}</span></td><td>{{ row.versionNo }}</td><td>{{ row.greyFlag }}</td>
              <td class="actions-cell">
                <button class="button secondary button-inline" @click="selectRule(row)">查看说明</button>
                <button class="button warn button-inline" :disabled="actionLoading" @click="toggleRule(row)">{{ row.ruleStatus === "启用" ? "停用" : "启用" }}</button>
              </td>
            </tr>
            <tr v-if="rows.length === 0">
              <td colspan="8" class="empty-cell">当前筛选条件下暂无清分规则。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="panel">
      <div class="topbar topbar-inner">
        <div>
          <h3>规则工作区</h3>
          <p>{{ selectedRule ? `当前规则：${selectedRule.ruleNo}` : "请选择上方规则查看版本与灰度说明" }}</p>
        </div>
      </div>
      <div v-if="!selectedRule" class="state-box">点击上方任意规则后，可查看当前版本、灰度标记和表达式说明。</div>
      <div v-else class="detail-summary-grid">
        <div class="summary-card">
          <strong>规则信息</strong>
          <span>{{ selectedRule.ruleName }} / {{ selectedRule.ruleType }}</span>
        </div>
        <div class="summary-card">
          <strong>版本与状态</strong>
          <span>{{ selectedRule.versionNo }} / {{ selectedRule.ruleStatus }}</span>
        </div>
        <div class="summary-card">
          <strong>灰度标记</strong>
          <span>{{ selectedRule.greyFlag }}</span>
        </div>
        <div class="state-box expression-box">
          <strong>表达式说明</strong>
          <span>{{ selectedRule.ruleExpression }}</span>
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

.expression-box {
  grid-column: span 3;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.expression-cell {
  max-width: 360px;
  white-space: normal;
  line-height: 1.5;
}

.actions-cell {
  display: flex;
  gap: 8px;
}

.button-inline {
  padding: 8px 12px;
}

.empty-cell {
  color: #64748b;
  text-align: center;
}
</style>
