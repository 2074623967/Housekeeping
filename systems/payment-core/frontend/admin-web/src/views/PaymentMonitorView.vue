<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { paymentMonitorApi } from "../api/client";

const router = useRouter();
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

async function loadOverview() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    overview.value = await paymentMonitorApi.getOverview();
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
                    <div>{{ alert.alertTitle }}</div>
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

        <div class="detail-panel">
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
                <td>{{ item.channelCode }}</td>
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
      </template>
    </section>
  </div>
</template>
