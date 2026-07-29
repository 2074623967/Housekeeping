<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { dashboardApi, paymentMetricsApi } from "../api/client";

const router = useRouter();
const summary = ref([]);
const isLoading = ref(true);
const errorMessage = ref("");
const metrics = ref({
  totalCount: 0,
  successCount: 0,
  successRate: "—",
  successAmount: "—",
  pendingCount: 0,
  closedCount: 0
});

const fallbackCards = [
  { key: "payment-count", title: "支付单总量", value: "0", badgeType: "info", badgeText: "待同步" },
  { key: "paid-amount", title: "成功支付金额", value: "¥0.00", badgeType: "success", badgeText: "交易金额" },
  { key: "pending-orders", title: "待收口支付", value: "0", badgeType: "warn", badgeText: "优先排查" },
  { key: "closed-orders", title: "已关闭支付", value: "0", badgeType: "danger", badgeText: "关闭保护" }
];

const overviewCards = computed(() => (summary.value.length ? summary.value : fallbackCards));

const focusQueue = computed(() => [
  {
    title: "待收口支付单",
    owner: "支付运营",
    impact: `${metrics.value.pendingCount || 0} 笔需查单 / 核回调`,
    status: metrics.value.pendingCount > 0 ? "待处理" : "已清空",
    badgeType: metrics.value.pendingCount > 0 ? "warn" : "success",
    route: "/payment-issues"
  },
  {
    title: "支付日终批次检查",
    owner: "财务结算",
    impact: `${metrics.value.closedCount || 0} 笔已关单待复核`,
    status: "待复盘",
    badgeType: "info",
    route: "/payment-day-end"
  },
  {
    title: "服务者结算状态核对",
    owner: "资金运营",
    impact: "支付侧需确认对结算影响",
    status: "去查看",
    badgeType: "warn",
    route: "/worker-settlements"
  }
]);

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
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const [data, metricData] = await Promise.all([dashboardApi.getSummary(), paymentMetricsApi.getSummary()]);
    summary.value = data.cards || [];
    metrics.value = metricData;
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
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
      <button class="button secondary" @click="loadDashboard">刷新</button>
    </div>

    <div class="risk-banner">
      当前工作台聚焦支付核心域：优先关注待收口支付、关单复盘、结算影响核对和异常任务处理。
    </div>

    <div v-if="errorMessage" class="error-banner">
      工作台数据加载失败：{{ errorMessage }}
    </div>

    <div v-if="isLoading" class="state-box">工作台数据加载中...</div>

    <template v-else>
      <div class="card-grid">
        <div v-for="card in overviewCards" :key="card.key" class="card">
          <p class="card-title">{{ card.title }}</p>
          <p class="card-value">{{ card.value }}</p>
          <span :class="['badge', card.badgeType]">{{ card.badgeText }}</span>
        </div>
      </div>

      <div class="split-panels">
        <section class="panel">
          <div class="section-title">
            <h3>今日重点事项</h3>
            <span class="meta">按支付运营处理优先级排序</span>
          </div>
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
