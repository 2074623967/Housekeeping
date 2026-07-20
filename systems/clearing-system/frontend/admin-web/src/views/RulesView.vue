<script setup>
import { onMounted, ref } from "vue";
import { ruleApi } from "../api/client";

const rows = ref([]);
const loading = ref(false);
const message = ref("");
const filters = ref({ ruleType: "", ruleStatus: "" });
const form = ref({ ruleName: "次日达订单清分规则", ruleType: "ORDER", ruleExpression: "平台=10%, 渠道=0.6%", greyFlag: "否" });

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const result = await ruleApi.getList({ ...filters.value, pageNo: 1, pageSize: 20 });
    rows.value = result.items;
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function createRule() {
  await ruleApi.create(form.value);
  await loadRows();
}

async function toggleRule(row) {
  if (row.ruleStatus === "启用") {
    await ruleApi.disable(row.ruleNo);
  } else {
    await ruleApi.enable(row.ruleNo);
  }
  await loadRows();
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
        <p>维护清分表达式、版本和启停状态</p>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>规则类型</label><input v-model="filters.ruleType" /></div>
        <div class="field"><label>规则状态</label><input v-model="filters.ruleStatus" /></div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadRows">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>
      <div class="toolbar">
        <div class="field"><label>规则名称</label><input v-model="form.ruleName" /></div>
        <div class="field"><label>规则类型</label><input v-model="form.ruleType" /></div>
        <div class="field"><label>规则表达式</label><input v-model="form.ruleExpression" /></div>
        <div class="toolbar-actions"><button class="button warn" @click="createRule">新增规则</button></div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">规则列表加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>规则号</th><th>名称</th><th>类型</th><th>表达式</th><th>状态</th><th>版本</th><th>灰度</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.ruleNo">
              <td>{{ row.ruleNo }}</td><td>{{ row.ruleName }}</td><td>{{ row.ruleType }}</td><td>{{ row.ruleExpression }}</td><td><span class="badge" :class="row.ruleStatusType">{{ row.ruleStatus }}</span></td><td>{{ row.versionNo }}</td><td>{{ row.greyFlag }}</td>
              <td><button class="button secondary" @click="toggleRule(row)">{{ row.ruleStatus === "启用" ? "停用" : "启用" }}</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
