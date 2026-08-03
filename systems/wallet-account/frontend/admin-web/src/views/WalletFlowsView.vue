<script setup>
import { onMounted, reactive, ref } from "vue";
import {
  buildWalletFlowExportDownloadUrl,
  exportWalletFlows,
  fetchWalletFlowExportTasks,
  fetchWalletFlows
} from "../api";

const query = reactive({
  walletAccountNo: "",
  sourceSystem: "",
  sourceBizNo: "",
  pageNo: 1,
  pageSize: 20
});

const flows = ref([]);
const total = ref(0);
const loading = ref(false);
const errorMessage = ref("");
const exportMessage = ref("");
const exporting = ref(false);
const exportTasks = ref([]);
const exportTaskTotal = ref(0);
const exportTaskLoading = ref(false);
const exportTaskQuery = reactive({
  operatorId: "finance-admin",
  operatorRole: "FINANCE",
  taskStatus: "",
  pageNo: 1,
  pageSize: 5
});

async function loadFlows() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const result = await fetchWalletFlows(query);
    flows.value = result.records || [];
    total.value = result.total || 0;
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function loadExportTasks() {
  exportTaskLoading.value = true;
  try {
    const result = await fetchWalletFlowExportTasks(exportTaskQuery);
    exportTasks.value = result.records || [];
    exportTaskTotal.value = result.total || 0;
  } catch (error) {
    exportMessage.value = error.message;
  } finally {
    exportTaskLoading.value = false;
  }
}

async function exportFlows() {
  exporting.value = true;
  exportMessage.value = "";
  try {
    const task = await exportWalletFlows({
      ...query,
      operatorId: "finance-admin",
      operatorRole: "FINANCE",
      operatorName: "财务管理员"
    });
    exportMessage.value = `导出任务已受理：${task.exportTaskNo}`;
    await loadExportTasks();
  } catch (error) {
    exportMessage.value = error.message;
  } finally {
    exporting.value = false;
  }
}

function downloadTask(task) {
  window.open(buildWalletFlowExportDownloadUrl(task.exportTaskNo, exportTaskQuery.operatorRole), "_blank");
}

onMounted(() => {
  loadFlows();
  loadExportTasks();
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
      <p class="muted">共 {{ total }} 条流水，当前第 {{ query.pageNo }} 页</p>
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
      <div class="toolbar">
        <button class="button button--light" :disabled="query.pageNo <= 1 || loading" @click="query.pageNo -= 1; loadFlows()">
          上一页
        </button>
        <button class="button button--light" :disabled="loading || query.pageNo * query.pageSize >= total" @click="query.pageNo += 1; loadFlows()">
          下一页
        </button>
      </div>

      <div class="section-divider"></div>

      <div class="toolbar">
        <h3>导出任务中心</h3>
        <select v-model="exportTaskQuery.taskStatus" @change="exportTaskQuery.pageNo = 1; loadExportTasks()">
          <option value="">全部状态</option>
          <option value="ACCEPTED">ACCEPTED</option>
        </select>
        <button class="button button--light" :disabled="exportTaskLoading" @click="loadExportTasks">刷新任务</button>
      </div>
      <p class="muted">共 {{ exportTaskTotal }} 个导出任务，支持按任务编号直接下载 CSV。</p>
      <p v-if="exportTaskLoading" class="muted">导出任务加载中...</p>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>任务编号</th>
              <th>状态</th>
              <th>筛选条件</th>
              <th>操作人</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="task in exportTasks" :key="task.exportTaskNo">
              <td>{{ task.exportTaskNo }}</td>
              <td>{{ task.taskStatus }}</td>
              <td>{{ task.walletAccountNo || "全部账户" }} / {{ task.sourceSystem || "全部系统" }} / {{ task.sourceBizNo || "全部业务单号" }}</td>
              <td>{{ task.operatorName }} / {{ task.operatorId }}</td>
              <td>{{ task.createdAt || "--" }}</td>
              <td>
                <button class="text-button" type="button" @click="downloadTask(task)">下载 CSV</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>
