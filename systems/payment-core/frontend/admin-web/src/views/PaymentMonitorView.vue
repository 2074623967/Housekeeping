<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { paymentMonitorApi } from "../api/client";

const router = useRouter();
const selectedAlert = ref(null);
const selectedChannel = ref(null);
const overview = ref({
  summary: {
    totalCount: 0,
    successCount: 0,
    successRate: "0.00%",
    successAmount: "¥0.00",
    pendingPaymentCount: 0,
    failedRefundCount: 0,
    disabledChannelCount: 0,
    alertCount: 0
  },
  trends: [],
  channelMetrics: [],
  alerts: []
});
const isLoading = ref(true);
const errorMessage = ref("");

const monitorCards = computed(() => {
  const summary = overview.value.summary || {};
  return [
    {
      title: "支付单总量",
      value: summary.totalCount ?? 0,
      hint: `成功 ${summary.successCount ?? 0} 笔`
    },
    {
      title: "整体成功率",
      value: summary.successRate || "0.00%",
      hint: `成功金额 ${summary.successAmount || "¥0.00"}`
    },
    {
      title: "待收口支付",
      value: summary.pendingPaymentCount ?? 0,
      hint: "建议优先查单与核对回调"
    },
    {
      title: "异常告警数",
      value: summary.alertCount ?? 0,
      hint: `退款失败 ${summary.failedRefundCount ?? 0} / 停用渠道 ${summary.disabledChannelCount ?? 0}`
    }
  ];
});

const trendSummary = computed(() => {
  const trendItems = overview.value.trends || [];
  if (!trendItems.length) {
    return {
      latestDate: "-",
      latestTotalCount: 0,
      latestSuccessAmount: "¥0.00",
      trendCount: 0
    };
  }
  const latestItem = trendItems[trendItems.length - 1];
  return {
    latestDate: latestItem.statDate,
    latestTotalCount: latestItem.totalCount,
    latestSuccessAmount: latestItem.successAmount,
    trendCount: trendItems.length
  };
});

const riskSummary = computed(() => {
  const channelMetrics = overview.value.channelMetrics || [];
  const alerts = overview.value.alerts || [];
  return {
    highRiskChannelCount: channelMetrics.filter((item) => item.riskLevel === "高" || item.riskLevel === "HIGH").length,
    pendingChannelCount: channelMetrics.filter((item) => (item.pendingCount || 0) > 0).length,
    alertP1Count: alerts.filter((item) => item.alertLevel === "P1").length
  };
});

const alertSuggestions = computed(() => {
  if (!selectedAlert.value) {
    return [];
  }
  const alert = selectedAlert.value;
  const suggestions = [];
  if (alert.alertTitle?.includes("待回调") || alert.alertMessage?.includes("待回调")) {
    suggestions.push("优先联查支付单管理页，确认是否存在 WAIT_CALLBACK 积压和渠道回调延迟。");
  }
  if (alert.alertTitle?.includes("退款失败") || alert.alertMessage?.includes("退款失败")) {
    suggestions.push("优先联查退款单管理页，核对失败原因并确认是否需要执行失败重试。");
  }
  if (alert.alertTitle?.includes("停用渠道") || alert.alertMessage?.includes("停用渠道")) {
    suggestions.push("优先检查支付配置中心中的渠道启停状态、商户参数和路由命中规则。");
  }
  if (!suggestions.length) {
    suggestions.push("按建议动作先进入对应处理页，再结合支付流水和处理日志继续排障。");
  }
  return suggestions;
});

const channelSuggestions = computed(() => {
  if (!selectedChannel.value) {
    return [];
  }
  const channel = selectedChannel.value;
  const suggestions = [];
  if ((channel.pendingCount || 0) > 0) {
    suggestions.push("当前渠道存在待处理支付，建议优先进入支付单管理页查看回调未收口链路。");
  }
  if (channel.riskLevel === "高" || channel.riskLevel === "HIGH") {
    suggestions.push("当前渠道为高风险，建议检查渠道可用性、商户配置和返回码分布。");
  }
  if ((channel.pendingCount || 0) === 0) {
    suggestions.push("当前渠道无待处理积压，可回看支付流水确认路由和回调轨迹是否稳定。");
  }
  return suggestions;
});

async function loadOverview() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    overview.value = await paymentMonitorApi.getOverview();
    selectedAlert.value = overview.value.alerts?.[0] || null;
    selectedChannel.value = overview.value.channelMetrics?.[0] || null;
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

function openAlertRoute(actionRoute) {
  if (!actionRoute) {
    return;
  }
  router.push(actionRoute);
}

function openChannelDrillDown(item) {
  if ((item.pendingCount || 0) > 0) {
    router.push(`/payments?paymentMethod=${item.paymentMethod}&status=WAIT_CALLBACK`);
    return;
  }
  router.push(`/payment-flows?channelCode=${item.channelCode}`);
}

function pickAlert(alert) {
  selectedAlert.value = alert;
}

function pickChannel(item) {
  selectedChannel.value = item;
}

onMounted(loadOverview);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付监控分析</h2>
        <p>查看最近支付趋势、渠道表现和待处理异常</p>
      </div>
      <button class="button secondary" @click="loadOverview">刷新</button>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        支付监控数据加载失败：{{ errorMessage }}
      </div>

      <div v-if="isLoading" class="state-box">支付监控数据加载中...</div>

      <template v-else>
        <div class="detail-card-grid">
          <div v-for="card in monitorCards" :key="card.title" class="detail-card">
            <div class="detail-label">{{ card.title }}</div>
            <div class="detail-value">{{ card.value }}</div>
            <div class="meta">{{ card.hint }}</div>
          </div>
        </div>

        <section class="panel overview-panel">
          <div class="section-title">
            <h3>监控风险总览</h3>
            <span class="meta">按趋势、渠道和告警统一观察风险面</span>
          </div>
          <div class="overview-grid">
            <article class="overview-card danger">
              <p class="overview-title">P1 告警优先处理</p>
              <strong>{{ riskSummary.alertP1Count }}</strong>
              <span>优先联查待回调、退款失败和停用渠道命中告警，避免主链路持续积压。</span>
            </article>
            <article class="overview-card warn">
              <p class="overview-title">存在待处理渠道</p>
              <strong>{{ riskSummary.pendingChannelCount }}</strong>
              <span>表示当前仍有渠道存在待收口支付，建议结合支付单与回调链路继续追踪。</span>
            </article>
            <article class="overview-card info">
              <p class="overview-title">高风险渠道数</p>
              <strong>{{ riskSummary.highRiskChannelCount }}</strong>
              <span>用于快速判断是否需要回到配置中心复核商户参数、网关状态和路由规则。</span>
            </article>
          </div>
        </section>

        <div class="split-panels">
          <section class="panel mini">
            <div class="section-title">
              <h3>最近支付趋势</h3>
              <span class="meta">按日汇总</span>
            </div>
            <table>
              <thead>
                <tr>
                  <th>日期</th>
                  <th>总单量</th>
                  <th>成功单量</th>
                  <th>成功金额</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in overview.trends" :key="item.statDate">
                  <td>{{ item.statDate }}</td>
                  <td>{{ item.totalCount }}</td>
                  <td>{{ item.successCount }}</td>
                  <td>{{ item.successAmount }}</td>
                </tr>
              </tbody>
            </table>
          </section>

          <section class="panel mini">
            <div class="section-title">
              <h3>异常告警</h3>
              <span class="meta">优先处理</span>
            </div>
            <table>
              <thead>
                <tr>
                  <th>等级</th>
                  <th>标题</th>
                  <th>影响</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="alert in overview.alerts" :key="alert.alertType">
                  <td><span :class="['badge', alert.alertLevelType]">{{ alert.alertLevel }}</span></td>
                  <td>
                    <button class="table-link" @click="pickAlert(alert)">{{ alert.alertTitle }}</button>
                    <div class="meta">{{ alert.alertMessage }}</div>
                    <div class="meta">{{ alert.suggestedAction }}</div>
                  </td>
                  <td>{{ alert.affectedCount }} 笔</td>
                  <td>
                    <button class="link-button" @click="openAlertRoute(alert.actionRoute)">立即排查</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </section>
        </div>

        <div class="split-panels detail-panel">
          <section class="panel mini">
            <div class="section-title">
              <h3>监控快照</h3>
              <span class="meta">最新趋势与当前选中告警</span>
            </div>
            <div class="detail-grid">
              <div class="detail-card"><span>趋势天数</span><strong>{{ trendSummary.trendCount }}</strong></div>
              <div class="detail-card"><span>最新日期</span><strong>{{ trendSummary.latestDate }}</strong></div>
              <div class="detail-card"><span>最新总单量</span><strong>{{ trendSummary.latestTotalCount }}</strong></div>
              <div class="detail-card"><span>最新成功金额</span><strong>{{ trendSummary.latestSuccessAmount }}</strong></div>
            </div>
            <div v-if="selectedAlert" class="ops-card">
              <div class="ops-title">告警快照</div>
              <div class="ops-row"><span>等级</span><span>{{ selectedAlert.alertLevel }}</span></div>
              <div class="ops-row"><span>标题</span><span>{{ selectedAlert.alertTitle }}</span></div>
              <div class="ops-row"><span>影响</span><span>{{ selectedAlert.affectedCount }} 笔</span></div>
              <div class="timeline-item">{{ selectedAlert.alertMessage }}</div>
              <div class="timeline-item">{{ selectedAlert.suggestedAction }}</div>
            </div>
          </section>

          <section class="panel mini">
            <div class="section-title">
              <h3>处理建议</h3>
              <span class="meta">结合当前告警和渠道状态生成</span>
            </div>
            <div v-if="selectedAlert" class="ops-card">
              <div class="ops-title">告警处理建议</div>
              <div v-for="item in alertSuggestions" :key="item" class="timeline-item">{{ item }}</div>
            </div>
            <div v-if="selectedChannel" class="ops-card">
              <div class="ops-title">渠道处理建议</div>
              <div class="ops-row"><span>渠道</span><span>{{ selectedChannel.channelCode }}</span></div>
              <div class="ops-row"><span>方式</span><span>{{ selectedChannel.paymentMethod }}</span></div>
              <div class="ops-row"><span>风险</span><span>{{ selectedChannel.riskLevel }}</span></div>
              <div v-for="item in channelSuggestions" :key="item" class="timeline-item">{{ item }}</div>
            </div>
          </section>
        </div>

        <div class="detail-layout detail-panel">
          <div class="table-wrap">
            <div class="section-title">
              <h3>渠道监控</h3>
              <span class="meta">按渠道和支付方式统计</span>
            </div>
            <table>
              <thead>
                <tr>
                  <th>渠道编码</th>
                  <th>支付方式</th>
                  <th>总单量</th>
                  <th>成功单量</th>
                  <th>成功率</th>
                  <th>成功金额</th>
                  <th>待处理</th>
                  <th>风险等级</th>
                  <th>风险说明</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in overview.channelMetrics" :key="item.channelCode + item.paymentMethod">
                  <td>
                    <button class="table-link" @click="pickChannel(item)">{{ item.channelCode }}</button>
                  </td>
                  <td>{{ item.paymentMethod }}</td>
                  <td>{{ item.totalCount }}</td>
                  <td>{{ item.successCount }}</td>
                  <td>{{ item.successRate }}</td>
                  <td>{{ item.successAmount }}</td>
                  <td>{{ item.pendingCount }}</td>
                  <td><span :class="['badge', item.riskLevelType]">{{ item.riskLevel }}</span></td>
                  <td class="flow-summary-cell">{{ item.riskHint }}</td>
                  <td>
                    <button class="link-button" @click="openChannelDrillDown(item)">查看明细</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <aside class="detail-side">
            <div v-if="selectedChannel" class="detail-stack">
              <div class="section-title">
                <h3>渠道快照</h3>
                <span class="meta">{{ selectedChannel.channelCode }}</span>
              </div>
              <div class="detail-grid">
                <div class="detail-card"><span>支付方式</span><strong>{{ selectedChannel.paymentMethod }}</strong></div>
                <div class="detail-card"><span>总单量</span><strong>{{ selectedChannel.totalCount }}</strong></div>
                <div class="detail-card"><span>成功单量</span><strong>{{ selectedChannel.successCount }}</strong></div>
                <div class="detail-card"><span>成功率</span><strong>{{ selectedChannel.successRate }}</strong></div>
                <div class="detail-card"><span>成功金额</span><strong>{{ selectedChannel.successAmount }}</strong></div>
                <div class="detail-card"><span>待处理</span><strong>{{ selectedChannel.pendingCount }}</strong></div>
              </div>
              <div class="ops-card">
                <div class="ops-title">风险说明</div>
                <div class="ops-row"><span>风险等级</span><span>{{ selectedChannel.riskLevel }}</span></div>
                <div class="timeline-item">{{ selectedChannel.riskHint }}</div>
              </div>
              <div class="ops-card">
                <div class="ops-title">联查入口</div>
                <div class="list-actions">
                  <button class="link-button" @click="openChannelDrillDown(selectedChannel)">查看渠道明细</button>
                  <button class="link-button" @click="router.push('/payment-config')">查看支付配置</button>
                </div>
              </div>
            </div>
            <div v-else class="state-box">选择左侧渠道后，可在这里查看渠道快照和处理建议。</div>
          </aside>
        </div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) 340px;
  gap: 16px;
}

.detail-side {
  display: grid;
  align-self: start;
}

.detail-stack {
  display: grid;
  gap: 16px;
}

.table-link {
  padding: 0;
  border: none;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
}

@media (max-width: 1200px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }
}
</style>
