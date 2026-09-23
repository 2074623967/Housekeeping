<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { dashboardApi, paymentMetricsApi, paymentTaskCenterApi } from "../api/client";
import { buildDashboardFocusQueue } from "../utils/dashboardFocus";

const router = useRouter();
const summary = ref([]);
const isLoading = ref(true);
const hasLoadedData = ref(false);
const errorMessage = ref("");
const taskOverview = ref(null);
const taskOverviewError = ref("");
let latestLoadRequestId = 0;
const metrics = ref({
  totalCount: 0,
  successCount: 0,
  successRate: "—",
  successAmount: "—",
  pendingCount: 0,
  closedCount: 0
});

const overviewCards = computed(() => summary.value);

const focusQueue = computed(() => buildDashboardFocusQueue(metrics.value, taskOverview.value || {}));

const healthRows = computed(() => [
  {
    label: "支付成功率",
    value: metrics.value.successRate,
    hint: `成功 ${metrics.value.successCount || 0} / 总量 ${metrics.value.totalCount || 0}`
  },
  {
    label: "成功支付金额",
    value: metrics.value.successAmount,
    hint: "当前以支付核心主链路口径展示"
  },
  {
    label: "待处理支付",
    value: `${metrics.value.pendingCount || 0} 笔`,
    hint: "用于回调未收口、处理中链路跟踪"
  },
  {
    label: "已关闭支付",
    value: `${metrics.value.closedCount || 0} 笔`,
    hint: "用于超时关单与保护性关闭复盘"
  }
]);

const quickActions = [
  {
    title: "支付异常中心",
    description: "查看待回调、事件失败和停用渠道命中问题",
    route: "/payment-issues"
  },
  {
    title: "支付监控分析",
    description: "查看趋势、渠道成功率和高优先级异常",
    route: "/payment-monitor"
  },
  {
    title: "任务中心",
    description: "触发关单、重发事件、退款重试和巡检任务",
    route: "/payment-task-center"
  },
  {
    title: "收银台会话",
    description: "回看订单到收银台的终端会话状态",
    route: "/cashier-sessions"
  }
];

async function loadDashboard() {
  if (isLoading.value && hasLoadedData.value) {
    return;
  }
  const requestId = ++latestLoadRequestId;
  isLoading.value = true;
  errorMessage.value = "";
  taskOverviewError.value = "";
  try {
    const [dashboardResult, metricResult, taskResult] = await Promise.allSettled([
      dashboardApi.getSummary(),
      paymentMetricsApi.getSummary(),
      paymentTaskCenterApi.getOverview()
    ]);
    if (requestId !== latestLoadRequestId) {
      return;
    }
    if (dashboardResult.status === "rejected" || metricResult.status === "rejected") {
      throw dashboardResult.reason || metricResult.reason;
    }
    summary.value = dashboardResult.value?.cards || [];
    metrics.value = metricResult.value || metrics.value;
    taskOverviewError.value = taskResult.status === "rejected"
      ? taskResult.reason?.message || "暂时无法读取任务中心"
      : "";
    taskOverview.value = taskResult.status === "fulfilled" ? taskResult.value : null;
    hasLoadedData.value = true;
  } catch (error) {
    if (requestId !== latestLoadRequestId) {
      return;
    }
    errorMessage.value = error.message;
  } finally {
    if (requestId === latestLoadRequestId) {
      isLoading.value = false;
    }
  }
}

function openRoute(route) {
  router.push(route);
}

onMounted(loadDashboard);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>工作台</h2>
        <p>查看家政支付系统当天核心指标、待办风险和支付主链路健康度</p>
      </div>
      <button class="button secondary" :disabled="isLoading" @click="loadDashboard">
        {{ isLoading ? "刷新中..." : "刷新" }}
      </button>
    </div>

    <div class="risk-banner">
      当前工作台聚焦支付核心域：优先关注待收口支付、关单复盘、结算影响核对和异常任务处理。
    </div>

    <div v-if="taskOverviewError" class="risk-banner">
      任务中心待办读取失败，当前重点事项仅按支付指标降级展示：{{ taskOverviewError }}
    </div>

    <div v-if="errorMessage" class="error-banner">
      {{ hasLoadedData ? "工作台数据刷新失败，当前展示上次成功快照：" : "工作台数据加载失败：" }}{{ errorMessage }}
    </div>

    <div v-if="isLoading" class="state-box">工作台数据加载中...</div>

    <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的工作台数据快照</div>

    <template v-else>
      <div class="card-grid">
        <div v-for="card in overviewCards" :key="card.key" class="card">
          <p class="card-title">{{ card.title }}</p>
          <p class="card-value">{{ card.value }}</p>
          <span :class="['badge', card.badgeType]">{{ card.badgeText }}</span>
        </div>
      </div>

      <div v-if="!overviewCards.length" class="state-box">当前暂无支付概览指标</div>

      <div class="split-panels">
        <section class="panel">
          <div class="section-title">
            <h3>今日重点事项</h3>
            <span class="meta">按支付运营处理优先级排序</span>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>事项</th>
                  <th>责任角色</th>
                  <th>影响范围</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in focusQueue" :key="item.title">
                  <td>
                    <button class="table-link" @click="openRoute(item.route)">{{ item.title }}</button>
                  </td>
                  <td>{{ item.owner }}</td>
                  <td>{{ item.impact }}</td>
                  <td><span :class="['badge', item.badgeType]">{{ item.status }}</span></td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="panel">
          <div class="section-title">
            <h3>今日健康度</h3>
            <span class="meta">实时汇总</span>
          </div>
          <div class="health-grid">
            <div v-for="item in healthRows" :key="item.label" class="health-card">
              <span class="health-label">{{ item.label }}</span>
              <strong class="health-value">{{ item.value }}</strong>
              <span class="meta">{{ item.hint }}</span>
            </div>
          </div>
        </section>
      </div>

      <section class="panel quick-panel">
        <div class="section-title">
          <h3>快捷入口</h3>
          <span class="meta">跳到排障、监控和任务处理页面</span>
        </div>
        <div class="quick-grid">
          <button
            v-for="item in quickActions"
            :key="item.title"
            class="quick-card"
            @click="openRoute(item.route)"
          >
            <strong>{{ item.title }}</strong>
            <span>{{ item.description }}</span>
          </button>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.health-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.health-card {
  display: grid;
  gap: 8px;
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #f8fafc;
}

.health-label {
  font-size: 13px;
  color: #475569;
}

.health-value {
  font-size: 22px;
  color: #0f172a;
}

.quick-panel {
  margin-top: 16px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.quick-card {
  display: grid;
  gap: 8px;
  padding: 16px;
  text-align: left;
  border: 1px solid #dbeafe;
  border-radius: 16px;
  background: linear-gradient(180deg, #f8fbff 0%, #eef6ff 100%);
  color: #0f172a;
  cursor: pointer;
}

.quick-card span {
  font-size: 13px;
  line-height: 1.6;
  color: #475569;
}

.table-link {
  padding: 0;
  border: none;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
}

@media (max-width: 1100px) {
  .health-grid,
  .quick-grid {
    grid-template-columns: 1fr;
  }
}
</style>
