<script setup>
import { onMounted, reactive, ref } from "vue";
import { exportWalletFlows, fetchWalletFlows } from "../api";

const query = reactive({
  walletAccountNo: "",
  sourceSystem: "",
  sourceBizNo: ""
});

const flows = ref([]);
const loading = ref(false);
const errorMessage = ref("");
const exportMessage = ref("");
const exporting = ref(false);

async function loadFlows() {
  loading.value = true;
  errorMessage.value = "";
  try {
    flows.value = await fetchWalletFlows(query);
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function exportFlows() {
  exporting.value = true;
  exportMessage.value = "";
  try {
    const task = await exportWalletFlows({
      ...query,
      operatorId: "admin",
      operatorName: "运营管理员"
    });
    exportMessage.value = `导出任务已受理：${task.exportTaskNo}`;
  } catch (error) {
    exportMessage.value = error.message;
  } finally {
    exporting.value = false;
  }
}

onMounted(() => {
  loadFlows();
});
</script>

<template>
  <section class="page">
    <div class="panel">
      <h2>钱包流水</h2>
      <p class="muted">流水查询和异步导出均通过 wallet-account 真实接口完成。</p>
      <div class="toolbar">
        <input v-model="query.walletAccountNo" placeholder="钱包账户号" />
        <input v-model="query.sourceSystem" placeholder="来源系统" />
        <input v-model="query.sourceBizNo" placeholder="来源业务单号" />
        <button class="button" @click="loadFlows">筛选</button>
        <button class="button button--light" :disabled="exporting" @click="exportFlows">
          {{ exporting ? "提交中..." : "导出流水" }}
        </button>
      </div>
      <p v-if="exportMessage" class="action-message">{{ exportMessage }}</p>
      <p v-if="loading" class="muted">流水加载中...</p>
      <p v-else-if="errorMessage" class="error-text">{{ errorMessage }}</p>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>流水号</th>
              <th>账户号</th>
              <th>流水类型</th>
              <th>来源系统</th>
              <th>来源业务单号</th>
              <th>变更前可用余额</th>
              <th>变更后可用余额</th>
              <th>操作人</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="flow in flows" :key="flow.flowNo">
              <td>{{ flow.flowNo }}</td>
              <td>{{ flow.walletAccountNo }}</td>
              <td>{{ flow.flowType }}</td>
              <td>{{ flow.sourceSystem }}</td>
              <td>{{ flow.sourceBizNo }}</td>
              <td>{{ flow.beforeAvailableBalance }}</td>
              <td>{{ flow.afterAvailableBalance }}</td>
              <td>{{ flow.operatorName }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>
