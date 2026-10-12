<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { paymentMonitorApi } from "../api/client";
import { paymentFlowsRoute, paymentsRoute, refundsRoute } from "../router/listRoutes";

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
    closedPaymentCount: 0,
    failedRefundCount: 0,
    disabledChannelCount: 0,
    alertCount: 0
  },
  trends: [],
  channelMetrics: [],
  alerts: []
});
const isLoading = ref(true);
const hasLoadedData = ref(false);
const errorMessage = ref("");
let latestLoadRequestId = 0;
const isSnapshotStale = computed(() => hasLoadedData.value && Boolean(errorMessage.value));
const canDrillDown = computed(() => hasLoadedData.value && !isLoading.value && !isSnapshotStale.value);
const canExport = computed(() => canDrillDown.value);
const metricDefinitions = computed(() => overview.value.metricDefinitions || []);

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
      title: "待处理支付",
      value: summary.pendingPaymentCount ?? 0,
      hint: "先区分渠道受理未确认与已受理待回调，按告警处理"
    },
    {
      title: "已关闭支付",
      value: summary.closedPaymentCount ?? 0,
      hint: "用于复盘超时关闭与人工关闭"
    },
    {
      title: "异常告警数",
      value: summary.alertCount ?? 0,
      hint: `处理中退款 ${summary.processingRefundCount ?? 0} / 退款失败 ${summary.failedRefundCount ?? 0}`
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
  const latestItem = trendItems[0];
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
    highRiskChannelCount: channelMetrics.filter(isHighRiskChannel).length,
    pendingChannelCount: channelMetrics.filter((item) =>
      (item.pendingCount || 0) > 0 || (item.unacceptedCount || 0) > 0).length,
    alertP1Count: alerts.filter((item) => item.alertLevel === "P1").length
  };
});

function isHighRiskChannel(item) {
  const riskLevel = String(item?.riskLevel || "").trim().toUpperCase();
  return riskLevel === "P1" || riskLevel === "高";
}

const alertSuggestions = computed(() => {
  if (!selectedAlert.value) {
    return [];
  }
  const alert = selectedAlert.value;
  const suggestions = [];
  if (alert.alertType === "CHANNEL_SUBMIT_UNCONFIRMED") {
    suggestions.push("先核对渠道受理与支付尝试日志，等待状态推进；勿主动查单或重复提交。");
  }
  if (alert.alertType === "CHANNEL_TRANSACTION_CONFLICT") {
    suggestions.push("渠道流水归属冲突，人工核查原始渠道事实及提交日志；勿主动查单、重复提交或绑定争议流水。");
  }
  if (alert.alertTitle?.includes("待回调") || alert.alertMessage?.includes("待回调")) {
    suggestions.push("优先联查支付单管理页，确认是否存在 WAIT_CALLBACK 积压和渠道回调延迟。");
  }
  if (alert.alertTitle?.includes("退款成功时间缺失")) {
    suggestions.push("优先核对可信退款回调、主动查单结果和退款操作日志，确认成功时间后再归属逆向资金账期。");
  } else if (alert.alertTitle?.includes("退款终态事实不一致")) {
    suggestions.push("优先核对退款单终态、最新退款尝试状态、完成时间和操作日志，确认结构化事实一致后再推进对账。");
  } else if (alert.alertTitle?.includes("支付成功事实不一致")) {
    suggestions.push("优先核对预付单绑定、订单来源、支付尝试与渠道流水，结合操作日志查明异常；不得按当前订单行自动补标来源或重发付款。");
  } else if (alert.alertTitle?.includes("成功时间缺失") || alert.alertMessage?.includes("成功时间")) {
    suggestions.push("优先核对支付成功通知与内部事件，确认可信成功时间后再归属账期。");
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
    suggestions.push("当前渠道存在已受理待回调支付，进入支付单管理页核对查单与回调收口链路。");
  }
  if ((channel.unacceptedCount || 0) > 0) {
    suggestions.push("当前渠道存在提交受理未确认支付，先核对渠道受理结果，勿主动查单或重复提交。");
  }
  if (channel.riskHint?.includes("流水归属冲突")) {
    suggestions.push("当前渠道存在流水归属冲突，需人工核查原始渠道事实；勿主动查单或重复提交。");
  }
  if (isHighRiskChannel(channel)) {
    suggestions.push("当前渠道为高风险，建议检查渠道可用性、商户配置和返回码分布。");
  }
  if ((channel.pendingCount || 0) === 0 && (channel.unacceptedCount || 0) === 0
    && !channel.riskHint?.includes("流水归属冲突")) {
    suggestions.push("当前渠道无待处理积压，可回看支付流水确认路由和回调轨迹是否稳定。");
  }
  return suggestions;
});

async function loadOverview() {
  if (isLoading.value && hasLoadedData.value) {
    return;
  }
  const requestId = ++latestLoadRequestId;
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const result = await paymentMonitorApi.getOverview();
    if (requestId !== latestLoadRequestId) {
      return;
    }
    overview.value = {
      ...overview.value,
      ...result,
      summary: { ...overview.value.summary, ...(result.summary || {}) },
      trends: Array.isArray(result.trends) ? result.trends : [],
      channelMetrics: Array.isArray(result.channelMetrics) ? result.channelMetrics : [],
      alerts: Array.isArray(result.alerts) ? result.alerts : [],
      metricDefinitions: Array.isArray(result.metricDefinitions) ? result.metricDefinitions : []
    };
    selectedAlert.value = overview.value.alerts[0] || null;
    selectedChannel.value = overview.value.channelMetrics[0] || null;
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

function openAlertRoute(actionRoute) {
  if (!canDrillDown.value || !actionRoute) {
    return;
  }
  router.push(actionRoute);
}

function exportSnapshot() {
  if (!canExport.value) {
    return;
  }
  window.open(paymentMonitorApi.buildExportUrl(), "_blank", "noopener,noreferrer");
}

function openChannelDrillDown(item) {
  if (!canDrillDown.value) {
    return;
  }
  if ((item.pendingCount || 0) > 0 || (item.unacceptedCount || 0) > 0) {
    router.push(paymentsRoute({
      channelCode: item.channelCode,
      paymentMethod: item.paymentMethod,
      status: "WAIT_CALLBACK"
    }));
    return;
  }
  router.push(paymentFlowsRoute({ channelCode: item.channelCode }));
}

function openProcessingRefunds() {
  if (!canDrillDown.value || !(overview.value.summary?.processingRefundCount > 0)) {
    return;
  }
  router.push(refundsRoute({ refundStatus: "PROCESSING" }));
}

function openClosedPayments() {
  if (!canDrillDown.value || !(overview.value.summary?.closedPaymentCount > 0)) {
    return;
  }
  router.push(paymentsRoute({ status: "CLOSED" }));
}

function openFailedRefunds() {
  if (!canDrillDown.value || !(overview.value.summary?.failedRefundCount > 0)) {
    return;
  }
  router.push(refundsRoute({ refundStatus: "FAIL" }));
}

function openDisabledChannels() {
  if (!canDrillDown.value || !(overview.value.summary?.disabledChannelCount > 0)) {
    return;
  }
  router.push({ path: "/payment-config", query: { channelStatus: "DISABLED" } });
}

function pickAlert(alert) {
  if (!canDrillDown.value) {
    return;
  }
  selectedAlert.value = alert;
}

function pickChannel(item) {
  if (!canDrillDown.value) {
    return;
  }
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
      <div class="table-inline-actions">
        <button class="button secondary" :disabled="isLoading" @click="loadOverview">
          {{ isLoading ? "刷新中..." : "刷新" }}
        </button>
        <button class="button secondary" :disabled="!canExport" @click="exportSnapshot">导出快照</button>
      </div>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        {{ hasLoadedData ? "支付监控数据刷新失败，当前展示上次成功快照：" : "支付监控数据加载失败：" }}{{ errorMessage }}
      </div>

      <div v-if="isLoading" class="state-box">支付监控数据加载中...</div>

      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的支付监控快照</div>

      <template v-else>
        <div class="detail-card-grid monitor-card-grid">
          <div v-for="card in monitorCards" :key="card.title" class="detail-card">
            <div class="detail-label">{{ card.title }}</div>
            <div class="detail-value">{{ card.value }}</div>
            <div class="meta">{{ card.hint }}</div>
          </div>
        </div>

        <section class="panel metric-definition-panel">
          <div class="section-title">
            <h3>指标口径与处置入口</h3>
            <span class="meta">当前默认运营门槛，生产 SLO 以正式配置和审批为准</span>
          </div>
          <div v-if="!metricDefinitions.length" class="state-box">当前暂无指标口径定义</div>
          <div v-else class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>指标</th>
                  <th>定义</th>
                  <th>单位</th>
                  <th>默认门槛</th>
                  <th>告警</th>
                  <th>责任方</th>
                  <th>入口</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in metricDefinitions" :key="item.metricCode">
                  <td><strong>{{ item.metricName }}</strong><div class="meta">{{ item.metricCode }}</div></td>
                  <td>{{ item.definition }}</td>
                  <td>{{ item.unit }}</td>
                  <td>{{ item.defaultThreshold }}</td>
                  <td><span class="badge" :class="item.alertLevel === 'P1' ? 'danger' : 'warn'">{{ item.alertLevel }}</span></td>
                  <td>{{ item.owner }}</td>
                  <td><button class="link-button" :disabled="!canDrillDown || !item.actionRoute" @click="openAlertRoute(item.actionRoute)">查看</button></td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="panel overview-panel">
          <div class="section-title">
            <h3>监控风险总览</h3>
            <span class="meta">按趋势、渠道和告警统一观察风险面</span>
          </div>
          <div class="overview-grid">
            <article class="overview-card danger">
              <p class="overview-title">P1 告警优先处理</p>
              <strong>{{ riskSummary.alertP1Count }}</strong>
              <span>优先联查待回调、成功时间缺失、支付与退款终态事实不一致和退款失败告警，避免资金事实持续失真。</span>
            </article>
            <article class="overview-card warn">
              <p class="overview-title">存在待处理渠道</p>
              <strong>{{ riskSummary.pendingChannelCount }}</strong>
              <span>包含已受理待回调与渠道受理未确认，先核对事实再决定是否查单。</span>
            </article>
            <article class="overview-card info">
              <p class="overview-title">高风险渠道数</p>
              <strong>{{ riskSummary.highRiskChannelCount }}</strong>
              <span>用于快速判断是否需要回到配置中心复核商户参数、网关状态和路由规则。</span>
            </article>
            <article class="overview-card warn">
              <p class="overview-title">处理中退款</p>
              <strong>{{ overview.summary.processingRefundCount ?? 0 }}</strong>
              <span>用于快速定位仍等待渠道结果或主动查单收口的退款单。</span>
              <button class="link-button" :disabled="!canDrillDown || !(overview.summary.processingRefundCount > 0)" @click="openProcessingRefunds">
                查看处理中退款
              </button>
            </article>
            <article class="overview-card info">
              <p class="overview-title">已关闭支付</p>
              <strong>{{ overview.summary.closedPaymentCount ?? 0 }}</strong>
              <span>用于复核超时关闭和人工关闭的支付单。</span>
              <button class="link-button" :disabled="!canDrillDown || !(overview.summary.closedPaymentCount > 0)" @click="openClosedPayments">
                查看已关闭支付
              </button>
            </article>
            <article class="overview-card danger">
              <p class="overview-title">失败退款</p>
              <strong>{{ overview.summary.failedRefundCount ?? 0 }}</strong>
              <span>用于快速定位需要运营复核、重试或线下处理的退款单。</span>
              <button class="link-button" :disabled="!canDrillDown || !(overview.summary.failedRefundCount > 0)" @click="openFailedRefunds">
                查看失败退款
              </button>
            </article>
            <article class="overview-card danger">
              <p class="overview-title">停用渠道</p>
              <strong>{{ overview.summary.disabledChannelCount ?? 0 }}</strong>
              <span>用于快速定位当前不应继续承接支付请求的渠道配置。</span>
              <button class="link-button" :disabled="!canDrillDown || !(overview.summary.disabledChannelCount > 0)" @click="openDisabledChannels">
                查看停用渠道
              </button>
            </article>
          </div>
        </section>

        <div class="split-panels">
          <section class="panel mini">
            <div class="section-title">
              <h3>最近支付事实趋势</h3>
              <span class="meta">创建按创建日，成功按成功日</span>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>日期</th>
                    <th>创建单量</th>
                    <th>成功发生量</th>
                    <th>成功金额</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="!overview.trends?.length">
                    <td colspan="4" class="state-box">当前暂无支付趋势数据</td>
                  </tr>
                  <tr v-for="item in overview.trends" :key="item.statDate">
                    <td>{{ item.statDate }}</td>
                    <td>{{ item.totalCount }}</td>
                    <td>{{ item.successCount }}</td>
                    <td>{{ item.successAmount }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <section class="panel mini">
            <div class="section-title">
              <h3>异常告警</h3>
              <span class="meta">优先处理</span>
            </div>
            <div class="table-wrap">
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
                  <tr v-if="!overview.alerts?.length">
                    <td colspan="4" class="state-box">当前暂无待处理异常告警</td>
                  </tr>
                  <tr v-for="alert in overview.alerts" :key="alert.alertType">
                    <td><span :class="['badge', alert.alertLevelType]">{{ alert.alertLevel }}</span></td>
                    <td>
                      <button class="table-link" :disabled="!canDrillDown" @click="pickAlert(alert)">{{ alert.alertTitle }}</button>
                      <div class="meta">{{ alert.alertMessage }}</div>
                      <div class="meta">{{ alert.suggestedAction }}</div>
                    </td>
                    <td>{{ alert.affectedCount }} 笔</td>
                    <td>
                      <button class="link-button" :disabled="!canDrillDown" @click="openAlertRoute(alert.actionRoute)">立即排查</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
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
            <div v-else class="state-box">当前暂无告警快照</div>
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
                  <th>已受理待回调</th>
                  <th>受理未确认</th>
                  <th>风险等级</th>
                  <th>风险说明</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="!overview.channelMetrics?.length">
                  <td colspan="11" class="state-box">当前暂无渠道监控数据</td>
                </tr>
                <tr v-for="item in overview.channelMetrics" :key="item.channelCode + item.paymentMethod">
                  <td>
                    <button class="table-link" :disabled="!canDrillDown" @click="pickChannel(item)">{{ item.channelCode }}</button>
                  </td>
                  <td>{{ item.paymentMethod }}</td>
                  <td>{{ item.totalCount }}</td>
                  <td>{{ item.successCount }}</td>
                  <td>{{ item.successRate }}</td>
                  <td>{{ item.successAmount }}</td>
                  <td>{{ item.pendingCount }}</td>
                  <td>{{ item.unacceptedCount }}</td>
                  <td><span :class="['badge', item.riskLevelType]">{{ item.riskLevel }}</span></td>
                  <td class="flow-summary-cell">{{ item.riskHint }}</td>
                  <td>
                    <button class="link-button" :disabled="!canDrillDown" @click="openChannelDrillDown(item)">查看明细</button>
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
                <div class="detail-card"><span>已受理待回调</span><strong>{{ selectedChannel.pendingCount }}</strong></div>
                <div class="detail-card"><span>受理未确认</span><strong>{{ selectedChannel.unacceptedCount }}</strong></div>
              </div>
              <div class="ops-card">
                <div class="ops-title">风险说明</div>
                <div class="ops-row"><span>风险等级</span><span>{{ selectedChannel.riskLevel }}</span></div>
                <div class="timeline-item">{{ selectedChannel.riskHint }}</div>
              </div>
              <div class="ops-card">
                <div class="ops-title">联查入口</div>
                <div class="list-actions">
                  <button class="link-button" :disabled="!canDrillDown" @click="openChannelDrillDown(selectedChannel)">查看渠道明细</button>
                  <button class="link-button" :disabled="!canDrillDown" @click="router.push('/payment-config')">查看支付配置</button>
                </div>
              </div>
            </div>
            <div v-else class="state-box">
              {{ overview.channelMetrics?.length ? "选择左侧渠道后，可在这里查看渠道快照和处理建议。" : "当前暂无渠道快照" }}
            </div>
          </aside>
        </div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.overview-panel {
  display: grid;
  gap: 16px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.overview-card {
  display: grid;
  gap: 8px;
  padding: 18px;
  border: 1px solid #dbe3f0;
  border-radius: 18px;
  background: #f8fafc;
}

.overview-card strong {
  color: #0f172a;
  font-size: 28px;
}

.overview-card span {
  color: #475569;
  line-height: 1.6;
}

.overview-title {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.overview-card.danger {
  border-color: #fecdd3;
  background: #fff1f2;
}

.overview-card.warn {
  border-color: #fed7aa;
  background: #fff7ed;
}

.overview-card.info {
  border-color: #bfdbfe;
  background: #eff6ff;
}

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
  .overview-grid,
  .detail-layout {
    grid-template-columns: 1fr;
  }
}
</style>
